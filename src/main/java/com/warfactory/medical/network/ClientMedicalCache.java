package com.warfactory.medical.network;

import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.limb.LimbType;

/**
 * Client-side holder for the latest medical snapshot synced from the server. Populated by the
 * {@link MedicalSyncPacket} handler on the client main thread and read by future HUD / rendering code.
 *
 * <p>The single reference is {@code volatile} so a read from any thread always sees a fully published
 * snapshot; all writes happen on the client main (packet) thread, so no further locking is needed.</p>
 */
public final class ClientMedicalCache {

    private static volatile MedicalSyncPacket snapshot;
    /**
     * Latest active-treatment state; null (or an inactive packet) means no overlay is shown.
     */
    private static volatile ActiveTreatmentPacket activeTreatment;
    /**
     * Client-only debug flag toggled by a key/UI; drives extra HUD/diagnostic rendering.
     */
    private static volatile boolean debug;
    /**
     * UI-local selected limb for highlighting; not authoritative, mirrors the server targeting hint.
     */
    private static volatile LimbType selectedLimb;

    private ClientMedicalCache() {
    }

    /**
     * @return the latest full snapshot, or {@code null} if none has been received yet.
     */
    public static MedicalSyncPacket get() {
        return snapshot;
    }

    /**
     * Store an authoritative full snapshot (a fresh baseline).
     */
    public static void set(MedicalSyncPacket packet) {
        snapshot = packet;
    }

    /**
     * Patch the cached baseline with an incremental {@link MedicalDeltaPacket}. No-op when no baseline exists
     * yet – a full snapshot always precedes deltas, so this only guards the brief pre-first-sync window.
     */
    public static void applyDelta(MedicalDeltaPacket delta) {
        MedicalSyncPacket base = snapshot;
        if (base != null) {
            snapshot = delta.applyTo(base);
        }
    }

    /**
     * Convenience accessor; never null (falls back to a healthy snapshot).
     */
    public static DerivedStats stats() {
        MedicalSyncPacket s = snapshot;
        return s == null ? DerivedStats.healthy() : s.stats();
    }

    public static HealthState state() {
        MedicalSyncPacket s = snapshot;
        return s == null ? HealthState.HEALTHY : s.state();
    }

    /**
     * Perceived-pain suppression fraction (0..1) from the latest snapshot; 0 when none received.
     */
    public static float painSuppression() {
        MedicalSyncPacket s = snapshot;
        return s == null ? 0.0F : s.painSuppression();
    }

    /**
     * Accumulating injectable-drug load from the latest snapshot; 0 when none received.
     */
    public static float drugLoad() {
        MedicalSyncPacket s = snapshot;
        return s == null ? 0.0F : s.drugLoad();
    }

    /**
     * 0..1 bleed-out death-timer progress from the latest snapshot (0 = not dying / just downed, 1 = about to
     * die); 0 when none received. Drives the pre-death blackout ramp in the unconscious overlay.
     */
    public static float deathProgress() {
        MedicalSyncPacket s = snapshot;
        return s == null ? 0.0F : s.deathProgress();
    }

    // ------------------------------------------------------------------ active treatment

    /**
     * Store the latest active-treatment state (from the server).
     */
    public static void setActiveTreatment(ActiveTreatmentPacket packet) {
        activeTreatment = packet;
    }

    /**
     * @return the latest active-treatment state, or {@code null} if none has been received.
     */
    public static ActiveTreatmentPacket activeTreatment() {
        return activeTreatment;
    }

    /**
     * @return true when a treatment is currently in progress on this client.
     */
    public static boolean hasActiveTreatment() {
        ActiveTreatmentPacket a = activeTreatment;
        return a != null && a.active();
    }

    // ------------------------------------------------------------------ client-only UI state

    /**
     * @return the client debug flag.
     */
    public static boolean isDebug() {
        return debug;
    }

    public static void setDebug(boolean value) {
        debug = value;
    }

    /**
     * Flip the client debug flag; @return the new value.
     */
    public static boolean toggleDebug() {
        debug = !debug;
        return debug;
    }

    /**
     * The UI-selected limb for local highlight (nullable).
     */
    public static LimbType selectedLimb() {
        return selectedLimb;
    }

    public static void setSelectedLimb(LimbType limb) {
        selectedLimb = limb;
    }

    /**
     * Drop the cached snapshot and all UI state (e.g. on client disconnect / world unload).
     */
    public static void clear() {
        snapshot = null;
        activeTreatment = null;
        selectedLimb = null;
        debug = false;
    }
}
