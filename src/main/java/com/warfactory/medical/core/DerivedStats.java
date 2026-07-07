package com.warfactory.medical.core;

/**
 * Immutable snapshot of everything the rest of the mod derives from the medical state. Produced by
 * {@link Physiology#compute} and cached on the {@link MedicalProfile}; safe to copy over the network.
 *
 */
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
        boolean anyArmTourniquet
) {
    private static final DerivedStats HEALTHY = new DerivedStats(
            30.0F, 0.0F, 30.0F, 0.0D, 0.0F, 0.0F, 1.0F, false, 1.0F,
            HealthState.HEALTHY, false, false, false, false, false, false, false);

    /**
     * A no-injury snapshot (30 health points, full mobility).
     */
    public static DerivedStats healthy() {
        return HEALTHY;
    }

    /**
     * True when unconscious from ANY cause (bleed-out or overdose); derived from state, not stored.
     */
    public boolean unconscious() {
        return state() == HealthState.UNCONSCIOUS;
    }

    /**
     * Conscious pre-unconsciousness respiratory-depression phase (air loss + no sprint + blur). Synced.
     */
    @Override
    public boolean asphyxiating() {
        return asphyxiating;
    }
}
