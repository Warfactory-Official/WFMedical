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
 * CLIENT-ONLY registry of which player ENTITY IDs are currently "downed" (passed out — the single merged
 * {@link com.warfactory.medical.core.HealthState#UNCONSCIOUS} state, entered from either an opioid overdose
 * or a bleed-out unconsciousness; see {@link com.warfactory.medical.core.MedicalProfile#isDowned()}).
 *
 * <p>The server is authoritative: {@link com.warfactory.medical.network.DownedStatePacket} fans the flag
 * out to every tracking client (and the subject itself), and its {@code handleClient()} routes here via
 * {@link net.minecraftforge.fml.DistExecutor} so this class is never classloaded on a dedicated server.
 * Reads happen on the RENDER thread (the downed body pose in {@code DownedPlayerRenderer}), writes happen
 * on the client MAIN thread (packet handling), so the backing set is guarded by an explicit monitor.</p>
 *
 * <h2>Local-player fold-in</h2>
 * <p>{@link #isDowned(int)} also returns {@code true} for the LOCAL player whenever the synced
 * {@link ClientMedicalCache} says the local body is unconscious, even before a
 * self-broadcast round-trips. This keeps the first-person effect gate and the third-person (F5) body pose
 * consistent with the HUD the instant the local snapshot flips.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>The nested {@link Events} subscriber wipes all tracked ids on client disconnect so stale flags never
 * bleed across worlds; the local-player fold-in needs no cleanup because it derives purely from the
 * (separately cleared) {@link ClientMedicalCache}.</p>
 */
public final class ClientDownedTracker {

    /**
     * Guards {@link #DOWNED}. Writes are on the client main thread; reads are on the render thread.
     */
    private static final Object LOCK = new Object();
    /**
     * Entity ids the server has flagged as downed. Not thread-safe itself, hence {@link #LOCK}.
     */
    private static final IntOpenHashSet DOWNED = new IntOpenHashSet();

    private ClientDownedTracker() {
    }

    /**
     * Record (or clear) the downed flag for a player entity id. Called on the client main thread from
     * {@link com.warfactory.medical.network.DownedStatePacket#handleClient()}.
     *
     * @param entityId the network id of the player whose downed state changed
     * @param downed   {@code true} to mark it downed, {@code false} to clear it
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
     * @param entityId a player entity network id
     * @return {@code true} if that player is currently downed. Any entity id the server has broadcast as
     * downed reports {@code true}; additionally the LOCAL player reports {@code true} whenever the
     * synced snapshot says it is unconscious (the single merged passed-out state, for either the
     * overdose or bleed-out cause; see the class javadoc). Unknown ids default to {@code false}.
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
     * CLIENT-ONLY FORGE-bus lifecycle hook: clears the registry when the local player disconnects, mirroring
     * how {@code MedicalClientEvents} drops the medical cache. Kept as a tiny nested subscriber so the whole
     * downed-tracking concern lives in one file.
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
         * flag set before death would survive onto the fresh body — rendering the respawned player twisted
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
