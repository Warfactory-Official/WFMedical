package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.client.screen.LimbWheelScreen;
import com.warfactory.medical.compat.OpenPersistenceCompat;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.limb.LimbStatus;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.Treatment;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.item.InjectableItem;
import com.warfactory.medical.item.MedicalItem;
import com.warfactory.medical.network.ClientMedicalCache;
import com.warfactory.medical.network.MedicalActionPacket;
import com.warfactory.medical.network.MedicalNetworking;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import com.warfactory.medical.network.TreatmentTargetInfoPacket;
import com.warfactory.medical.network.TreatmentTargetRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * CLIENT-ONLY driver for the right-click treatment flow that replaces the old hold-to-use channel.
 *
 * <p>Right-clicking with a medical item in the MAIN hand is intercepted here: we detect the target (self, or
 * another player / downed body under the crosshair), then either apply immediately (systemic / global
 * treatments), auto-apply to the single damaged limb, or open the {@link LimbWheelScreen}. For a non-self
 * target whose injuries we don't know locally, we ask the server ({@link TreatmentTargetRequestPacket}) and
 * continue from its reply ({@link #onTargetInfo}). While a treatment is running the held item is locked (no
 * hotbar change / scroll / offhand swap), matching "cannot change items while applying it".</p>
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TreatmentInteractions {

    /**
     * Reach (blocks) of the fallback medical-target pick ray, between vanilla's ~3-block entity pick and the
     * server's 6-block treatment validation.
     */
    private static final double TARGET_PICK_REACH = 4.5D;

    /**
     * Hotbar slot locked for the duration of the active treatment ({@code -1} = nothing locked).
     */
    private static int lockedSlot = -1;
    /**
     * Previous-tick active-treatment flag, to detect the active→inactive edge that releases the item lock.
     */
    private static boolean wasActive;

    private TreatmentInteractions() {
    }

    // ------------------------------------------------------------------ right-click interception

    @SubscribeEvent
    public static void onUseInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || player.isSpectator()) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof MedicalItem)) {
            return; // only the main-hand medical item drives the wheel; leave every other item to vanilla
        }
        // Consume the interaction regardless of what we do next so the vanilla channel never also fires.
        event.setSwingHand(false);
        event.setCanceled(true);

        // A downed / no-hands medic can't treat; an in-progress treatment blocks starting another.
        if (MedicalState.isHandsDisabled(player) || ClientMedicalCache.hasActiveTreatment()) {
            return;
        }
        beginTreatment(mc, player, held);
    }

    /**
     * Branch on the item and target: injectables are self-only systemic; global treatments apply immediately;
     * localized treatments ask the server for the target's injuries + the per-limb treatable mask (for self
     * AND other targets – the mask needs the authoritative trauma list either way), then open the wheel /
     * auto-select in {@link #onTargetInfo}.
     */
    private static void beginTreatment(Minecraft mc, LocalPlayer player, ItemStack held) {
        Item item = held.getItem();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null) {
            return;
        }
        if (item instanceof InjectableItem) {
            // Systemic self-injection – no target choice, no wheel.
            sendAction(itemId, null, -1);
            return;
        }
        if (!(item instanceof MedicalItem medical)) {
            return;
        }
        Treatment treatment = medical.getTreatment();
        int targetId = pickTargetId(mc, player);
        if (treatment == null || treatment.action().isGlobal()) {
            // Whole-body effect (blood / painkiller / clotting): apply immediately to whoever is aimed at.
            sendAction(itemId, null, targetId);
            return;
        }
        // Localized: the client's own LimbSummary cache can't tell WHICH limbs this item can actually treat
        // (minor-damage pools have no trauma), so always ask the server; continue in onTargetInfo().
        MedicalNetworking.sendToServer(new TreatmentTargetRequestPacket(targetId, itemId));
    }

    /**
     * Server reply with a target's limb summaries + treatable mask (client thread; {@code targetEntityId = -1}
     * = self). Re-checks the medic still holds the item, then opens the wheel / auto-selects for that target.
     */
    public static void onTargetInfo(TreatmentTargetInfoPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(packet.itemId());
        if (!(item instanceof MedicalItem) || !holdsAnywhere(player, item)) {
            return;
        }
        proceedLocalized(packet.targetEntityId(), packet.itemId(), packet.limbs(), packet.treatableMask());
    }

    /**
     * Given a target's limb summaries and the item's treatable mask: build the offered slices – damaged limbs
     * the item can treat, plus (when holding a tourniquet) limbs already wearing one, offered as REMOVE
     * slices. Nothing offerable → notify; exactly one apply slice → auto-apply; more → wheel.
     */
    private static void proceedLocalized(int targetId, ResourceLocation itemId, LimbSummary[] limbs,
                                         int treatableMask) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || limbs == null) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        boolean tourniquetHeld = item instanceof MedicalItem medical
                && medical.getTreatment() != null
                && medical.getTreatment().action() == TreatmentAction.APPLY_TOURNIQUET;
        int trackerId = targetId < 0 ? player.getId() : targetId;

        int applyMask = 0;
        int removeMask = 0;
        for (LimbSummary s : limbs) {
            if (s == null) {
                continue;
            }
            int bit = 1 << s.limb().ordinal();
            if (tourniquetHeld && ClientTourniquetTracker.has(trackerId, s.limb().ordinal())) {
                removeMask |= bit; // worn limb: the slice removes instead of double-applying
                continue;
            }
            if (LimbStatus.isDamaged(s) && (treatableMask & bit) != 0) {
                applyMask |= bit;
            }
        }

        if (applyMask == 0 && removeMask == 0) {
            String key = LimbStatus.damaged(limbs).isEmpty()
                    ? "gui.wfmedical.treat.no_injuries"
                    : "gui.wfmedical.treat.no_treatable";
            player.displayClientMessage(Component.translatable(key), true);
            return;
        }
        if (removeMask == 0 && Integer.bitCount(applyMask) == 1) {
            sendAction(itemId, LimbType.VALUES[Integer.numberOfTrailingZeros(applyMask)], targetId);
            return;
        }
        mc.setScreen(new LimbWheelScreen(targetId, itemId, limbs, applyMask | removeMask, removeMask));
    }

    /**
     * Send the authoritative treatment request and remember the acting hotbar slot for the item lock. Called by
     * both the auto-select path and {@link LimbWheelScreen} on a slice click.
     */
    public static void sendAction(ResourceLocation itemId, LimbType limb, int targetId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            lockedSlot = mc.player.getInventory().selected;
        }
        MedicalNetworking.sendToServer(new MedicalActionPacket(itemId, limb, targetId));
    }

    /**
     * The entity id to treat: another targetable {@link LivingEntity} under the crosshair, else {@code -1}
     * (self). Vanilla's crosshair pick ({@code mc.hitResult}) stops at ~3 blocks for entities while the
     * server accepts treatment out to 6, so when it misses we run our own slightly longer ray – downed
     * bodies lie low and are easy to under-reach otherwise. The ray goes through
     * {@link ProjectileUtil#getEntityHitResult}, so it inherits the envelope hit-registration boxes.
     */
    /**
     * The entity id the local player is aiming at for a medical interaction ({@code -1} = self / nothing
     * targetable). Public so the open-sheet key can bind the interaction sheet to the same aimed-at target the
     * right-click wheel would treat. Safe no-op ({@code -1}) with no local player.
     */
    public static int pickTargetEntityId() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return player == null ? -1 : pickTargetId(mc, player);
    }

    private static int pickTargetId(Minecraft mc, LocalPlayer player) {
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le && le != player && isTargetable(le)) {
            return le.getId();
        }
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F);
        Vec3 end = eye.add(view.scale(TARGET_PICK_REACH));
        AABB sweep = player.getBoundingBox().expandTowards(view.scale(TARGET_PICK_REACH)).inflate(1.0D);
        EntityHitResult ray = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
                e -> e != player && e instanceof LivingEntity le && isTargetable(le),
                TARGET_PICK_REACH * TARGET_PICK_REACH);
        if (ray != null && ray.getEntity() instanceof LivingEntity le) {
            return le.getId();
        }
        return -1;
    }

    /**
     * Any player, or (with the compat toggle) an Open-Persistence logout body – both carry a medical profile.
     */
    private static boolean isTargetable(LivingEntity entity) {
        return entity instanceof Player
                || (MedicalConfig.openPersistenceCompat() && OpenPersistenceCompat.isPersistentBody(entity));
    }

    private static boolean holdsAnywhere(Player player, Item item) {
        return player.getMainHandItem().getItem() == item || player.getOffhandItem().getItem() == item;
    }

    // ------------------------------------------------------------------ item lock while applying

    /**
     * Enforce "cannot change items while applying": snap the held hotbar slot back and swallow offhand swaps
     * for as long as a treatment is running on this client.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean active = player != null && ClientMedicalCache.hasActiveTreatment();
        if (active) {
            if (lockedSlot < 0) {
                lockedSlot = player.getInventory().selected; // fallback capture if we never went through sendAction
            }
            if (lockedSlot >= 0 && lockedSlot < 9 && player.getInventory().selected != lockedSlot) {
                player.getInventory().selected = lockedSlot;
            }
            while (mc.options.keySwapOffhand.consumeClick()) {
                // discard offhand-swap presses so the treatment item can't be moved out of hand
            }
        } else if (wasActive) {
            lockedSlot = -1; // treatment just ended; release the lock
        }
        wasActive = active;
    }

    /**
     * Block hotbar scrolling while a treatment is running (mirrors the hotbar-slot lock for the scroll wheel).
     */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && ClientMedicalCache.hasActiveTreatment()) {
            event.setCanceled(true);
        }
    }
}
