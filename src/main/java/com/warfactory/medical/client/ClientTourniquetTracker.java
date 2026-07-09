package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLIENT-ONLY registry of each player entity id's worn-tourniquet limb mask (bit {@code 1 <<
 * LimbType.ordinal()}), kept server-authoritative via {@code TourniquetStatePacket} (broadcast to trackers,
 * so you see teammates' tourniquets too). Read on the render thread, written on the client main thread;
 * guarded by a lock like {@link ClientDownedTracker}. Pure presentation state.
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientTourniquetTracker {

    private static final Object LOCK = new Object();
    private static final Int2IntOpenHashMap MASKS = new Int2IntOpenHashMap();

    private ClientTourniquetTracker() {
    }

    /**
     * Record (or clear, when {@code mask == 0}) a player's worn-tourniquet mask. Client main thread.
     */
    public static void set(int entityId, int mask) {
        synchronized (LOCK) {
            if (mask == 0) {
                MASKS.remove(entityId);
            } else {
                MASKS.put(entityId, mask);
            }
        }
    }

    /**
     * The worn-tourniquet limb mask for a player entity id ({@code 0} if none / unknown). Render thread.
     */
    public static int mask(int entityId) {
        synchronized (LOCK) {
            return MASKS.get(entityId);
        }
    }

    /**
     * Whether the given limb ordinal currently wears a tourniquet on that entity.
     */
    public static boolean has(int entityId, int limbOrdinal) {
        return (mask(entityId) & (1 << limbOrdinal)) != 0;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        synchronized (LOCK) {
            MASKS.clear();
        }
    }
}
