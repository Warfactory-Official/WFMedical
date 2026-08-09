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

@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TreatmentInteractions {

    private static int lockedSlot = -1;
    private static boolean wasActive;

    private TreatmentInteractions() {
    }


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
            return;
        }
        event.setSwingHand(false);
        event.setCanceled(true);

        if (MedicalState.isHandsDisabled(player) || ClientMedicalCache.hasActiveTreatment()) {
            return;
        }
        beginTreatment(mc, player, held);
    }

    private static void beginTreatment(Minecraft mc, LocalPlayer player, ItemStack held) {
        Item item = held.getItem();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null) {
            return;
        }
        // Self-only items (oral meds) always dose the user; everything else may target an aimed-at player.
        boolean selfOnly = item instanceof MedicalItem m && m.isSelfOnly();
        int targetId = selfOnly ? -1 : pickTargetId(mc, player);
        if (item instanceof InjectableItem) {
            // A syringe is a whole-body jab (no limb) -- dose self or the targeted player directly.
            sendAction(itemId, null, targetId);
            return;
        }
        if (!(item instanceof MedicalItem medical)) {
            return;
        }
        Treatment treatment = medical.getTreatment();
        if (treatment == null || treatment.action().isGlobal()) {
            sendAction(itemId, null, targetId);
            return;
        }
        MedicalNetworking.sendToServer(new TreatmentTargetRequestPacket(targetId, itemId));
    }

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
                removeMask |= bit;
                continue;
            }

            boolean eligible = (treatableMask & bit) != 0
                    && (tourniquetHeld || LimbStatus.isDamaged(s));
            if (eligible) {
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

    public static void sendAction(ResourceLocation itemId, LimbType limb, int targetId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            lockedSlot = mc.player.getInventory().selected;
        }
        MedicalNetworking.sendToServer(new MedicalActionPacket(itemId, limb, targetId));
    }

    public static int pickTargetEntityId() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return player == null ? -1 : pickTargetId(mc, player);
    }

    private static int pickTargetId(Minecraft mc, LocalPlayer player) {
        double reach = MedicalConfig.treatReachBlocks();
        double reachSqr = reach * reach;
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le && le != player && isTargetable(le)
                && player.distanceToSqr(le) <= reachSqr) {
            return le.getId();
        }
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F);
        Vec3 end = eye.add(view.scale(reach));
        AABB sweep = player.getBoundingBox().expandTowards(view.scale(reach)).inflate(1.0D);
        EntityHitResult ray = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
                e -> e != player && e instanceof LivingEntity le && isTargetable(le),
                reachSqr);
        if (ray != null && ray.getEntity() instanceof LivingEntity le && player.distanceToSqr(le) <= reachSqr) {
            return le.getId();
        }
        return -1;
    }

    private static boolean isTargetable(LivingEntity entity) {
        return entity instanceof Player
                || (MedicalConfig.openPersistenceCompat() && OpenPersistenceCompat.isPersistentBody(entity));
    }

    private static boolean holdsAnywhere(Player player, Item item) {
        return player.getMainHandItem().getItem() == item || player.getOffhandItem().getItem() == item;
    }


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
                lockedSlot = player.getInventory().selected;
            }
            if (lockedSlot >= 0 && lockedSlot < 9 && player.getInventory().selected != lockedSlot) {
                player.getInventory().selected = lockedSlot;
            }
            while (mc.options.keySwapOffhand.consumeClick()) {
            }
        } else if (wasActive) {
            lockedSlot = -1;
        }
        wasActive = active;
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && ClientMedicalCache.hasActiveTreatment()) {
            event.setCanceled(true);
        }
    }
}
