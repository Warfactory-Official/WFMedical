package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = WFMedical.MOD_ID, value = Dist.CLIENT)
public final class ClientTourniquetTracker {

    private static final Object LOCK = new Object();
    private static final Int2IntOpenHashMap MASKS = new Int2IntOpenHashMap();

    private ClientTourniquetTracker() {
    }

    public static void set(int entityId, int mask) {
        synchronized (LOCK) {
            if (mask == 0) {
                MASKS.remove(entityId);
            } else {
                MASKS.put(entityId, mask);
            }
        }
    }

    public static int mask(int entityId) {
        synchronized (LOCK) {
            return MASKS.get(entityId);
        }
    }

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
