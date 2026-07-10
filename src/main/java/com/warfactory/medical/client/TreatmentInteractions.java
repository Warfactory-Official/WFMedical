package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.client.screen.LimbWheelScreen;
import com.warfactory.medical.client.screen.MedicalUIParts;
import com.warfactory.medical.compat.OpenPersistenceCompat;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.limb.LimbStatus;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.Treatment;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

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
     * localized treatments open the wheel / auto-select using the target's damaged limbs.
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
        // Localized: needs a damaged limb.
        if (targetId < 0) {
            proceedLocalized(targetId, itemId, MedicalUIParts.limbSummaries());
        } else {
            // We don't have the target's injuries client-side; ask the server, continue in onTargetInfo().
            MedicalNetworking.sendToServer(new TreatmentTargetRequestPacket(targetId, itemId));
        }
    }

    /**
     * Server reply with a non-self target's limb summaries (client thread). Re-checks the medic still holds the
     * item, then opens the wheel / auto-selects for that target.
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
        proceedLocalized(packet.targetEntityId(), packet.itemId(), packet.limbs());
    }

    /**
     * Given a target's limb summaries: nothing damaged → notify; one damaged limb → auto-apply; more → wheel.
     */
    private static void proceedLocalized(int targetId, ResourceLocation itemId, LimbSummary[] limbs) {
        Minecraft mc = Minecraft.getInstance();
        List<LimbType> damaged = LimbStatus.damaged(limbs);
        if (damaged.isEmpty()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("gui.wfmedical.treat.no_injuries"), true);
            }
            return;
        }
        if (damaged.size() == 1) {
            sendAction(itemId, damaged.get(0), targetId);
            return;
        }
        mc.setScreen(new LimbWheelScreen(targetId, itemId, limbs));
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
     * The entity id to treat: another targetable {@link LivingEntity} under the crosshair, else {@code -1} (self).
     */
    private static int pickTargetId(Minecraft mc, LocalPlayer player) {
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le && le != player && isTargetable(le)) {
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
