package com.warfactory.medical.substance.api;

import com.warfactory.medical.substance.Substance;
import com.warfactory.medical.substance.Substances;

public final class Opioids {

    private Opioids() {
    }

    /** Morphine overdose progress in [0, 1] at drug load {@code load}; 0 without a valid morphine window. */
    public static float overdose(float load) {
        Substance morphine = Substances.SUBSTANCES.get(Substances.MORPHINE);
        if (morphine == null || morphine.overdoseThreshold() <= morphine.doseLoad()) {
            return 0.0F;
        }
        float over = (load - morphine.doseLoad()) / (morphine.overdoseThreshold() - morphine.doseLoad());
        return Math.max(0.0F, Math.min(1.0F, over));
    }
}
