package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.network.ClientMedicalCache;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

public final class ClientDownedTracker {

    private static final Object LOCK = new Object();
    private static final IntOpenHashSet DOWNED = new IntOpenHashSet();

    private ClientDownedTracker() {
    }

    public static void set(int entityId, boolean downed) {
        synchronized (LOCK) {
            if (downed) {
                DOWNED.add(entityId);
            } else {
                DOWNED.remove(entityId);
            }
        }
        Entity entity = Minecraft.getInstance().level == null ? null
                : Minecraft.getInstance().level.getEntity(entityId);
        if (entity != null) {
            entity.refreshDimensions();
        }
    }

    public static boolean isDowned(int entityId) {
        synchronized (LOCK) {
            if (DOWNED.contains(entityId)) {
                return true;
            }
        }
        LocalPlayer self = Minecraft.getInstance().player;
        if (self != null && self.getId() == entityId) {
            return ClientMedicalCache.stats().unconscious();
        }
        return false;
    }

    public static void clear() {
        synchronized (LOCK) {
            DOWNED.clear();
        }
    }

    @EventBusSubscriber(modid = WFMedical.MOD_ID, value = Dist.CLIENT)
    public static final class Events {

        private Events() {
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            clear();
        }


        @SubscribeEvent
        public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
            if (!event.getLevel().isClientSide()) {
                return;
            }
            synchronized (LOCK) {
                DOWNED.remove(event.getEntity().getId());
            }
        }

        @SubscribeEvent
        public static void onRespawnClone(ClientPlayerNetworkEvent.Clone event) {
            LocalPlayer player = event.getNewPlayer();
            if (player != null) {
                set(player.getId(), false);
            }
            com.warfactory.medical.client.overlay.UnconsciousOverlay.reset();
            com.warfactory.medical.client.effect.PassoutBlurEffect.reset();
        }
    }
}
