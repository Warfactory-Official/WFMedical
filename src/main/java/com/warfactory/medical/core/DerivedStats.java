package com.warfactory.medical.core;

public record DerivedStats(
        float effectiveMaxHealth,
        float healthModifier,
        float effectiveCurrentHealth,
        double totalBleeding,
        float totalPain,
        float systemicPain,
        float movementMultiplier,
        boolean sprintBlocked,
        float jumpMultiplier,
        HealthState state,
        boolean anyLegFracture,
        boolean anyArmFracture,
        boolean asphyxiating,
        boolean painKoPending,
        boolean bothArmsDisabled,
        boolean bothLegsDisabled,
        boolean anyArmTourniquet,
        /** Circulation factor (1.0 = healthy): bleeding is already scaled by this. */
        double cardiacOutput,
        /** Beats per minute. */
        float heartRate
) {
    private static final DerivedStats HEALTHY = new DerivedStats(
            30.0F, 0.0F, 30.0F, 0.0D, 0.0F, 0.0F, 1.0F, false, 1.0F,
            HealthState.HEALTHY, false, false, false, false, false, false, false, 1.0D,
            MedicalProfile.DEFAULT_HEART_RATE);

    public static DerivedStats healthy() {
        return HEALTHY;
    }

    /** Systolic blood pressure in mmHg; 120 at a healthy rest. */
    public int systolic() {
        return Cardio.systolic(cardiacOutput);
    }

    /** Diastolic blood pressure in mmHg; 80 at a healthy rest. */
    public int diastolic() {
        return Cardio.diastolic(cardiacOutput);
    }

    public boolean unconscious() {
        return state() == HealthState.UNCONSCIOUS;
    }

    @Override
    public boolean asphyxiating() {
        return asphyxiating;
    }
}
