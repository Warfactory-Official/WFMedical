package com.warfactory.medical.core.limb;

import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared predicate for "is this limb damaged" — the single criterion that decides which limbs appear in the
 * treatment wheel. Deliberately independent of what the held item can actually treat: a limb shows up while it
 * is anything other than perfectly healthy (reduced health, active bleeding, felt pain, or an unstabilized
 * fracture), even if the current item cannot address that particular injury.
 *
 * <p>Common (dist-neutral) so both the client wheel builder and the server-side validation / auto-select use
 * the exact same rule.</p>
 */
public final class LimbStatus {

    /**
     * Below this health fraction a limb counts as hurt (guards against float dust reading a full limb as damaged).
     */
    private static final float HEALTHY_EPS = 0.999F;

    private LimbStatus() {
    }

    public static boolean isDamaged(float healthPercent, float bleeding, float pain, boolean fracture) {
        return healthPercent < HEALTHY_EPS || bleeding > 0.0F || pain > 0.0F || fracture;
    }

    public static boolean isDamaged(LimbSummary summary) {
        return summary != null
                && isDamaged(summary.healthPercent(), summary.bleeding(), summary.pain(), summary.fracture());
    }

    /**
     * The damaged limbs among {@code summaries}, preserving {@link LimbType} order. Never null.
     */
    public static List<LimbType> damaged(LimbSummary[] summaries) {
        List<LimbType> out = new ArrayList<>();
        if (summaries != null) {
            for (LimbSummary s : summaries) {
                if (isDamaged(s)) {
                    out.add(s.limb());
                }
            }
        }
        return out;
    }
}
