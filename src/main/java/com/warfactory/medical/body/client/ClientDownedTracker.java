package com.warfactory.medical.body.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

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

    public static void forget(int entityId) {
        synchronized (LOCK) {
            DOWNED.remove(entityId);
        }
    }
}
