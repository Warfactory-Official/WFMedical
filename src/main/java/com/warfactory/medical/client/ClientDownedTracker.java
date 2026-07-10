package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.network.ClientMedicalCache;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLIENT-ONLY registry of which player entity IDs are downed (server-authoritative; updated via
 * DownedStatePacket). Thread-guarded: reads on the render thread, writes on the client main thread.
 * {@link #isDowned(int)} also folds in the local player's synced snapshot so the effect gate stays
 * consistent before the self-broadcast arrives.
 */
public final class ClientDownedTracker {

    /**
     * Guards DOWNED; writes on client main thread, reads on render thread.
     */
    private static final Object LOCK = new Object();
    private static final IntOpenHashSet DOWNED = new IntOpenHashSet();

    private ClientDownedTracker() {
    }

    /**
     * Record or clear the downed flag for a player entity id (called on the client main thread).
     */
    public static void set(int entityId, boolean downed) {
        synchronized (LOCK) {
            if (downed) {
                DOWNED.add(entityId);
            } else {
                DOWNED.remove(entityId);
            }
        }
        // Refresh that entity's collision box / eye-height on the client so the rotated downed hitbox (for the
        // attacker's raytrace) and the lowered first-person camera apply immediately, and revert on wake. Runs
        // on the client main (packet) thread. The subject receives its own edge (TRACKING_ENTITY_AND_SELF), so
        // this also refreshes the LOCAL player. No-op if the entity isn't loaded yet.
        Entity entity = Minecraft.getInstance().level == null ? null
                : Minecraft.getInstance().level.getEntity(entityId);
        if (entity != null) {
            entity.refreshDimensions();
        }
    }

    /**
     * True if the server flagged this entity id as downed, OR (for the local player) if the synced
     * snapshot says it is unconscious – so the local effect gate stays consistent before the broadcast.
     */
    public static boolean isDowned(int entityId) {
        synchronized (LOCK) {
            if (DOWNED.contains(entityId)) {
                return true;
            }
        }
        // Local-player fold-in: keep the local body / effect gate consistent with the HUD even before a
        // self-broadcast arrives. Reads the (volatile) synced snapshot; never allocates.
        LocalPlayer self = Minecraft.getInstance().player;
        if (self != null && self.getId() == entityId) {
            return ClientMedicalCache.stats().unconscious();
        }
        return false;
    }

    /**
     * Wipe all tracked downed ids (client disconnect / world change) so nothing leaks across worlds.
     */
    public static void clear() {
        synchronized (LOCK) {
            DOWNED.clear();
        }
    }

    /**
     * Clears the registry on disconnect so stale flags never bleed across worlds.
     */
    @Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Events {

        private Events() {
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            clear();
        }

        /**
         * On a death/respawn (or dimension change) the LOCAL player entity keeps its network id, so a downed
         * flag set before death would survive onto the fresh body – rendering the respawned player twisted
         * (laid-out pose), with the rotated hitbox and a locked look. Clear that id on the clone edge so the
         * respawned body is upright and interactive. Other players' flags are untouched; the subject re-learns
         * its own state from the resync/broadcast that follows a respawn.
         */
        @SubscribeEvent
        public static void onRespawnClone(ClientPlayerNetworkEvent.Clone event) {
            LocalPlayer player = event.getNewPlayer();
            if (player != null) {
                set(player.getId(), false);
            }
            // Snap the passed-out screen effects to clear so a vignette / blackout / blur that was on-screen at
            // the moment of death doesn't linger and briefly black out the fresh life while it eases back down.
            com.warfactory.medical.client.overlay.UnconsciousOverlay.reset();
            com.warfactory.medical.client.effect.PassoutBlurEffect.reset();
        }
    }
}
