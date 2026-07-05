package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.network.ClientMedicalCache;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLIENT-ONLY registry of which player ENTITY IDs are currently "downed" (passed out — either blacked
 * out from an opioid overdose or knocked down while bleeding out; see
 * {@link com.warfactory.medical.core.MedicalProfile#isDowned()}).
 *
 * <p>The server is authoritative: {@link com.warfactory.medical.network.DownedStatePacket} fans the flag
 * out to every tracking client (and the subject itself), and its {@code handleClient()} routes here via
 * {@link net.minecraftforge.fml.DistExecutor} so this class is never classloaded on a dedicated server.
 * Reads happen on the RENDER thread (the downed body pose in {@code DownedPlayerRenderer}), writes happen
 * on the client MAIN thread (packet handling), so the backing set is guarded by an explicit monitor.</p>
 *
 * <h2>Local-player fold-in</h2>
 * <p>{@link #isDowned(int)} also returns {@code true} for the LOCAL player whenever the synced
 * {@link ClientMedicalCache} says the local body is blacked out or knocked down, even before a
 * self-broadcast round-trips. This keeps the first-person effect gate and the third-person (F5) body pose
 * consistent with the HUD the instant the local snapshot flips.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>The nested {@link Events} subscriber wipes all tracked ids on client disconnect so stale flags never
 * bleed across worlds; the local-player fold-in needs no cleanup because it derives purely from the
 * (separately cleared) {@link ClientMedicalCache}.</p>
 */
public final class ClientDownedTracker {

    /** Guards {@link #DOWNED}. Writes are on the client main thread; reads are on the render thread. */
    private static final Object LOCK = new Object();
    /** Entity ids the server has flagged as downed. Not thread-safe itself, hence {@link #LOCK}. */
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
    }

    /**
     * @param entityId a player entity network id
     * @return {@code true} if that player is currently downed. Any entity id the server has broadcast as
     *         downed reports {@code true}; additionally the LOCAL player reports {@code true} whenever the
     *         synced snapshot says it is blacked out or knocked down (see the class javadoc). Unknown ids
     *         default to {@code false}.
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
            return ClientMedicalCache.stats().blackout()
                    || ClientMedicalCache.stats().state() == HealthState.KNOCKED_DOWN;
        }
        return false;
    }

    /** Wipe all tracked downed ids (client disconnect / world change) so nothing leaks across worlds. */
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
    }
}
