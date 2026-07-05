package com.warfactory.medical.core;

/**
 * Immutable snapshot of everything the rest of the mod derives from the medical state. Produced by
 * {@link Physiology#compute} and cached on the {@link MedicalProfile}; safe to copy over the network.
 */
public record DerivedStats(
        float effectiveMaxHealth,
        float healthModifier,
        float effectiveCurrentHealth,
        double totalBleeding,
        float totalPain,
        float movementMultiplier,
        boolean sprintBlocked,
        float jumpMultiplier,
        HealthState state,
        boolean anyLegFracture,
        boolean anyArmFracture,
        boolean asphyxiating
) {
    private static final DerivedStats HEALTHY = new DerivedStats(
            30.0F, 0.0F, 30.0F, 0.0D, 0.0F, 1.0F, false, 1.0F,
            HealthState.HEALTHY, false, false, false);

    /**
     * A no-injury snapshot (30 health points, full mobility).
     */
    public static DerivedStats healthy() {
        return HEALTHY;
    }

    /**
     * Whether the player is unconscious ("passed out"), for ANY cause (bleed-out unconsciousness OR opioid
     * overdose unconsciousness — both merged into {@link HealthState#UNCONSCIOUS}). Derived from {@link #state()}
     * rather than stored, so it costs nothing in the sync packet.
     *
     * @return {@code true} when {@link #state()} is {@link HealthState#UNCONSCIOUS}
     */
    public boolean unconscious() {
        return state() == HealthState.UNCONSCIOUS;
    }

    /**
     * Whether the player is currently asphyxiating — the conscious, pre-unconsciousness respiratory-depression
     * phase of a heavy opioid overdose (rapid air loss + weakness + no sprint + blurred vision), before it
     * tips over into {@link HealthState#UNCONSCIOUS}. Synced so the client can drive the blur / impairment.
     *
     * @return {@code true} while the player is in the overdose asphyxia phase
     */
    @Override
    public boolean asphyxiating() {
        return asphyxiating;
    }
}
