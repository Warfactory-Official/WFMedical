package com.warfactory.medical.network;

import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;

/**
 * Client-side holder for the latest medical snapshot synced from the server. Populated by the
 * {@link MedicalSyncPacket} handler on the client main thread and read by future HUD / rendering code.
 *
 * <p>The single reference is {@code volatile} so a read from any thread always sees a fully published
 * snapshot; all writes happen on the client main (packet) thread, so no further locking is needed.</p>
 */
public final class ClientMedicalCache {

    private static volatile MedicalSyncPacket snapshot;
    /** Set by a delta when the local snapshot is known to be behind the server. */
    private static volatile boolean stale;

    private ClientMedicalCache() {
    }

    /** @return the latest full snapshot, or {@code null} if none has been received yet. */
    public static MedicalSyncPacket get() {
        return snapshot;
    }

    /** Store an authoritative full snapshot and clear the stale flag. */
    public static void set(MedicalSyncPacket packet) {
        snapshot = packet;
        stale = false;
    }

    /** Flag the current snapshot as out of date (a delta arrived); a full sync will follow. */
    public static void markStale() {
        stale = true;
    }

    public static boolean isStale() {
        return stale;
    }

    /** Convenience accessor; never null (falls back to a healthy snapshot). */
    public static DerivedStats stats() {
        MedicalSyncPacket s = snapshot;
        return s == null ? DerivedStats.healthy() : s.stats();
    }

    public static HealthState state() {
        MedicalSyncPacket s = snapshot;
        return s == null ? HealthState.HEALTHY : s.state();
    }

    /** Drop the cached snapshot (e.g. on client disconnect / world unload). */
    public static void clear() {
        snapshot = null;
        stale = false;
    }
}
