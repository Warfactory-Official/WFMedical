package com.warfactory.medical.core;

/**
 * Immutable bundle of physiology tunables. Kept config-free so the core stays pure; the config module
 * builds one of these from TOML and hands it to {@link Physiology}.
 *
 * @param maxHealthPoints            baseline max-health "points" (30 = 15 hearts).
 * @param maxBloodMl                 total blood volume in ml.
 * @param bloodLowFraction           fraction below which blood-loss penalties begin.
 * @param bloodCriticalFraction      fraction below which the player is critical.
 * @param bloodDeathMl               blood volume (ml) at or below which death/bleed-out occurs.
 * @param painShockThreshold         pain (0..1) above which pain-shock penalties begin.
 * @param painMaxHealthPenalty       max health points removed by full pain shock.
 * @param legFractureSpeedMultiplier movement multiplier applied per fractured leg.
 * @param painSpeedFloor             lower bound on the movement multiplier.
 * @param bleedoutEnabled            if true, lethal conditions render unconscious (bleed-out) instead of instant death.
 * @param bleedoutTicks              ticks a player may remain unconscious from bleeding out before dying.
 * @param bloodDeathLossFraction     fraction of total blood volume that, once LOST, kills outright (bleeding out).
 * @param bloodUnconsciousLossFraction fraction LOST at which blood loss starts feeding the unconsciousness score.
 * @param painUnconsciousThreshold   perceived pain (0..1) above which pain feeds the unconsciousness score.
 * @param painUnconsciousWeight      how much fully-saturated pain contributes to the unconsciousness score.
 * @param bloodMovementPenaltyLossFraction fraction of blood LOST above which walk/jump speed is penalised.
 */
public record PhysiologyParams(
        float maxHealthPoints,
        double maxBloodMl,
        double bloodLowFraction,
        double bloodCriticalFraction,
        double bloodDeathMl,
        float painShockThreshold,
        float painMaxHealthPenalty,
        float legFractureSpeedMultiplier,
        float painSpeedFloor,
        boolean bleedoutEnabled,
        int bleedoutTicks,
        double bloodDeathLossFraction,
        double bloodUnconsciousLossFraction,
        float painUnconsciousThreshold,
        float painUnconsciousWeight,
        double bloodMovementPenaltyLossFraction
) {
    public static PhysiologyParams defaults() {
        return new PhysiologyParams(
                30.0F,      // maxHealthPoints
                5000.0D,    // maxBloodMl
                0.60D,      // bloodLowFraction
                0.35D,      // bloodCriticalFraction
                0.0D,       // bloodDeathMl
                0.60F,      // painShockThreshold
                10.0F,      // painMaxHealthPenalty
                0.40F,      // legFractureSpeedMultiplier
                0.30F,      // painSpeedFloor
                true,       // bleedoutEnabled
                600,        // bleedoutTicks
                0.40D,      // bloodDeathLossFraction
                0.30D,      // bloodUnconsciousLossFraction
                0.70F,      // painUnconsciousThreshold
                1.00F,      // painUnconsciousWeight
                0.25D       // bloodMovementPenaltyLossFraction
        );
    }
}
