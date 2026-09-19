package com.warfactory.medical.core;

/**
 * The circulatory loop: heart rate, the cardiac output it produces, and the blood pressure that falls out
 * of both. Modelled on ACE3's {@code ace_medical_vitals_fnc_updateHeartRate} and
 * {@code ace_medical_status_fnc_getCardiacOutput}.
 *
 * <p>The chain is: blood volume sets how well the ventricle fills (venous return), heart rate sets how often
 * it empties, and the product is cardiac output. Output drives blood pressure, and the body raises the heart
 * rate to defend that pressure when volume falls. Because {@link Physiology} scales every wound's bleed rate
 * by cardiac output, that defence has a price: a patient whose heart is racing to hold their pressure up is
 * pushing blood out of their wounds faster. Stopping the bleeding and killing the pain are what break the
 * spiral.
 *
 * <p>Everything here is pure: no entity, no level, no config singleton. The stateful part is the single
 * {@code heartRate} field on {@link MedicalProfile}, advanced once per engine interval.
 */
public final class Cardio {

    /** ACE3's target mean arterial pressure at full volume, in mmHg; the target scales with blood volume. */
    private static final double TARGET_MEAN_PRESSURE = 107.0D;
    /** Systolic and diastolic pressure at a healthy resting circulation, in mmHg. */
    private static final double SYSTOLIC_AT_REST = 120.0D;
    private static final double DIASTOLIC_AT_REST = 80.0D;
    /** Mean arterial pressure at a healthy resting circulation: the usual (2/3 diastolic + 1/3 systolic). */
    private static final double MEAN_AT_REST = (2.0D / 3.0D) * DIASTOLIC_AT_REST + (1.0D / 3.0D) * SYSTOLIC_AT_REST;
    /** Share of the gap to the target the heart closes each second (ACE3 halves the remaining gap). */
    private static final double APPROACH_PER_SECOND = 0.5D;
    /** Share of the current rate a decompensating heart loses each second (ACE3: {@code -hr/10}). */
    private static final double DECAY_PER_SECOND = 0.1D;

    private Cardio() {
    }

    /**
     * How well the ventricle fills, 0..1. Full at normal volume, falling linearly to nothing at
     * {@code cardiacVenousReturnFloor}: past that there is simply not enough blood coming back to pump.
     */
    public static double venousReturn(double bloodRatio, PhysiologyParams cfg) {
        double floor = cfg.cardiacVenousReturnFloor();
        double span = 1.0D - floor;
        if (span <= 0.0D) {
            return 1.0D;
        }
        return clamp01((bloodRatio - floor) / span);
    }

    /**
     * Circulation as a dimensionless factor, 1.0 at a healthy resting heart. Venous return times heart rate,
     * exactly as ACE3 multiplies its filling approximation by beats per minute, floored at
     * {@code cardiacOutputFloor} because a wound still seeps under gravity with no circulation at all.
     *
     * <p>This is the physiological quantity: blood pressure is read off it, and the rate the body settles at
     * is the rate that brings it to the pressure it is defending.
     */
    public static double cardiacOutput(double bloodRatio, float heartRate, PhysiologyParams cfg) {
        if (!cfg.cardiacOutputEnabled()) {
            return 1.0D;
        }
        return Math.max(venousReturn(bloodRatio, cfg) * rateFactor(heartRate, cfg, 1.0D),
                cfg.cardiacOutputFloor());
    }

    /**
     * What a wound's bleed rate is actually multiplied by. Identical to {@link #cardiacOutput} except that
     * {@code heartRateBleedInfluence} can dilute how much the rate contributes: the knob is a balance lever
     * on how punishing compensatory tachycardia is, deliberately kept off the pressure the body is steering
     * by so that the heart is never chasing a target its own dilution puts out of reach.
     */
    public static double bleedScale(double bloodRatio, float heartRate, PhysiologyParams cfg) {
        if (!cfg.cardiacOutputEnabled()) {
            return 1.0D;
        }
        return Math.max(venousReturn(bloodRatio, cfg)
                * rateFactor(heartRate, cfg, cfg.heartRateBleedInfluence()), cfg.cardiacOutputFloor());
    }

    /** How much faster than resting the heart is moving blood, blended by {@code influence}. */
    private static double rateFactor(float heartRate, PhysiologyParams cfg, double influence) {
        if (!cfg.heartRateEnabled()) {
            return 1.0D;
        }
        double resting = cfg.heartRateResting();
        if (resting <= 0.0D) {
            return 1.0D;
        }
        return 1.0D + influence * (Math.max(heartRate, 0.0F) / resting - 1.0D);
    }

    /** Mean arterial pressure in mmHg for a given cardiac output; ~93 at a healthy rest. */
    public static double meanArterialPressure(double cardiacOutput) {
        return MEAN_AT_REST * cardiacOutput;
    }

    /** Systolic pressure in mmHg; 120 at a healthy rest. */
    public static int systolic(double cardiacOutput) {
        return (int) Math.round(SYSTOLIC_AT_REST * cardiacOutput);
    }

    /** Diastolic pressure in mmHg; 80 at a healthy rest. */
    public static int diastolic(double cardiacOutput) {
        return (int) Math.round(DIASTOLIC_AT_REST * cardiacOutput);
    }

    /**
     * The rate the heart is trying to reach, in bpm.
     *
     * <p>Three things move it. Below {@code heartRateCompensationRatio} (ACE3's class III haemorrhage) the
     * body defends its blood pressure: it needs enough beats to push the target pressure through a ventricle
     * that is only partly filling, which is where tachycardia in a bleeding casualty comes from. Pain drives
     * a floor under the rate. Drugs move it both ways: a stimulant pushes it up, an opioid holds it down,
     * which is what makes a patient who has been given too much morphine read as dangerously slow.
     *
     * <p>ACE3 reaches the same place by iterating {@code targetHR = hr * targetBP / meanBP} once per second;
     * this is that loop solved for its fixed point, which converges identically but cannot run away when the
     * measured pressure is near zero.
     */
    public static float targetHeartRate(double bloodRatio, float perceivedPain, float stimulant,
                                        float painSuppression, PhysiologyParams cfg) {
        double resting = cfg.heartRateResting();
        double target = resting;

        if (bloodRatio < cfg.heartRateCompensationRatio()) {
            double entering = venousReturn(bloodRatio, cfg);
            if (entering <= 0.0D) {
                target = cfg.heartRateMax();
            } else {
                // The body defends its normal mean pressure scaled by the volume it has left, so the output
                // it demands is simply that fraction of a healthy one, and the rate needed for it is that
                // divided by how well the ventricle is filling. (ACE3 writes the same thing as a 107 mmHg
                // target against a resting mean of about the same number, and gets there by iterating.)
                target = resting * bloodRatio / entering;
            }
        }

        if (perceivedPain > cfg.heartRatePainThreshold()) {
            target = Math.max(target, resting + cfg.heartRatePainGain() * perceivedPain);
        }

        target += cfg.heartRateStimulantBonus() * clamp01(stimulant);
        target -= cfg.heartRateOpioidDrop() * clamp01(painSuppression);

        if (target < 0.0D) {
            target = 0.0D;
        } else if (target > cfg.heartRateMax()) {
            target = cfg.heartRateMax();
        }
        return (float) target;
    }

    /**
     * Advance the heart rate by {@code deltaSeconds} toward its target, never overshooting it.
     *
     * <p>Below {@code heartRateDecompensationRatio} (ACE3's class IV haemorrhage) the compensation gives out
     * and the rate falls away toward zero instead of chasing a target it can no longer reach. At the default
     * blood settings that boundary is the same volume at which a casualty bleeds out, so it is the tail of
     * the curve rather than a state you sit in; raise {@code bloodDeathLossFraction} past it and the full
     * bradycardic collapse becomes reachable.
     */
    public static float advanceHeartRate(float current, double bloodRatio, float perceivedPain, float stimulant,
                                         float painSuppression, double deltaSeconds, PhysiologyParams cfg) {
        if (!cfg.heartRateEnabled()) {
            return (float) cfg.heartRateResting();
        }
        if (deltaSeconds <= 0.0D) {
            return current;
        }
        float hr = Math.max(current, 0.0F);

        if (bloodRatio < cfg.heartRateDecompensationRatio()) {
            double next = hr - deltaSeconds * DECAY_PER_SECOND * hr;
            return (float) Math.max(next, 0.0D);
        }

        float target = targetHeartRate(bloodRatio, perceivedPain, stimulant, painSuppression, cfg);
        double step = deltaSeconds * APPROACH_PER_SECOND * (target - hr);
        double next = hr + step;
        // Approach only: a long interval must not carry the rate past what it was aiming for.
        return (float) (target >= hr ? Math.min(next, target) : Math.max(next, target));
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : Math.min(v, 1.0D);
    }
}
