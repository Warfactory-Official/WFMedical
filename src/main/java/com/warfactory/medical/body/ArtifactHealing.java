package com.warfactory.medical.body;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.api.MedicalHealing;
import com.wf.wflib.artifact.api.Artifacts;

public final class ArtifactHealing {

    /** GAMMA's strongest closer (Mica, 0.054 a real second) ~ full clotting boost. */
    private static final float FULL_CLOT_RATE = 0.06F;

    private ArtifactHealing() {
    }

    /** Vanilla heal is clamped to the medical health value and would do nothing; negative clot rate thins. */
    public static void claim() {
        Artifacts.HEALER.set(WFMedical.MOD_ID, MedicalHealing::heal);
        Artifacts.CLOTTER.set(WFMedical.MOD_ID, (player, rate) -> {
            if (rate > 0.0F) {
                MedicalHealing.clot(player, Math.min(1.0F, rate / FULL_CLOT_RATE), 40);
            } else {
                MedicalHealing.thin(player, Math.min(1.0F, -rate / FULL_CLOT_RATE), 40);
            }
        });
    }
}
