package com.warfactory.medical.body.limb;

import com.warfactory.medical.body.network.MedicalSyncPacket.LimbSummary;

import java.util.ArrayList;
import java.util.List;

public final class LimbStatus {

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
