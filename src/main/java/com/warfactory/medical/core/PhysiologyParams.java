package com.warfactory.medical.core;

import com.warfactory.medical.core.limb.LimbType;

/**
 * Immutable bundle of physiology tunables. Kept config-free so the core stays pure; the config module
 * builds one of these from TOML and hands it to {@link Physiology}.
 *
 * @param maxHealthPoints                  baseline max-health "points" (30 = 15 hearts).
 * @param maxBloodMl                       total blood volume in ml.
 * @param bloodLowFraction                 fraction below which blood-loss penalties begin.
 * @param bloodCriticalFraction            fraction below which the player is critical.
 * @param bloodDeathMl                     blood volume (ml) at or below which death/bleed-out occurs.
 * @param painShockThreshold               pain (0..1) above which pain-shock penalties begin.
 * @param painMaxHealthPenalty             max health points removed by full pain shock.
 * @param legFractureSpeedMultiplier       movement multiplier applied per fractured leg.
 * @param painSpeedFloor                   lower bound on the movement multiplier.
 * @param bleedoutEnabled                  if true, lethal conditions render unconscious (bleed-out) instead of instant death.
 * @param bleedoutTicks                    ticks a player may remain unconscious from bleeding out before dying.
 * @param bloodDeathLossFraction           fraction of total blood volume that, once LOST, kills outright (bleeding out).
 * @param bloodUnconsciousLossFraction     fraction LOST at which blood loss starts feeding the unconsciousness score.
 * @param painUnconsciousThreshold         perceived pain (0..1) above which pain feeds the unconsciousness score.
 * @param painUnconsciousWeight            how much fully-saturated pain contributes to the unconsciousness score.
 * @param bloodMovementPenaltyLossFraction fraction of blood LOST above which walk/jump speed is penalised.
 * @param painShareHead                    max SYSTEMIC pain (0..1) a fully-painful head can contribute.
 * @param painShareTorso                   max SYSTEMIC pain (0..1) a fully-painful torso can contribute.
 * @param painShareArm                     max SYSTEMIC pain (0..1) a fully-painful arm can contribute (per arm).
 * @param painShareLeg                     max SYSTEMIC pain (0..1) a fully-painful leg can contribute (per leg).
 * @param painSaturationK                  per-limb diminishing-returns constant: local pain = raw / (raw + k).
 * @param adrenalineEnabled                if true, a PAIN-driven knockout is held off for a grace period (engine-timed).
 * @param asphyxiaMoveMultiplier           movement multiplier while consciously asphyxiating (heavy constraint).
 * @param stimulantSpeedBonus              movement-speed bonus fraction at full stimulant strength (added above normal).
 * @param healthShareHead                  max share of the life pool a fully-destroyed head can remove.
 * @param healthShareTorso                 max share of the life pool a fully-destroyed torso can remove.
 * @param healthShareArm                   max share of the life pool a fully-destroyed arm can remove (per arm).
 * @param healthShareLeg                   max share of the life pool a fully-destroyed leg can remove (per leg).
 * @param tourniquetBleedMultiplier        multiplier applied to a limb's bleeding while a tourniquet is on it (does not treat the wound).
 * @param tourniquetLegSpeedMultiplier     movement multiplier applied per leg wearing a tourniquet (discourages permanent wear).
 * @param tourniquetArmSpeedMultiplier     movement multiplier applied per arm wearing a tourniquet (minor).
 * @param headDepletionInstakill           if true, a fully-destroyed head kills outright instead of downing the player.
 * @param torsoDepletionInstakill          if true, a fully-destroyed torso kills outright instead of downing the player.
 * @param bleedingRateMultiplier           global multiplier on every wound's bleeding rate.
 * @param cardiacOutputEnabled             if true, bleeding scales with circulation so blood loss decelerates as volume falls.
 * @param cardiacVenousReturnFloor         blood ratio at or below which venous return (and so cardiac output) reaches zero.
 * @param cardiacOutputFloor               lower bound on the cardiac-output factor: blood still seeps with no circulation.
 * @param heartRateEnabled                 if true, heart rate is modelled and feeds cardiac output; if false it is pinned at resting.
 * @param heartRateResting                 resting heart rate in bpm, and the rate at which circulation counts as 1.0.
 * @param heartRateMax                     ceiling on heart rate in bpm.
 * @param heartRateBleedInfluence          how much heart rate scales the BLEED rate: 0 ignores it, 1 is fully linear in bpm.
 * @param heartRateCompensationRatio       blood ratio below which the body raises the rate to defend its blood pressure.
 * @param heartRateDecompensationRatio     blood ratio below which compensation gives out and the rate decays toward zero.
 * @param heartRatePainThreshold           perceived pain (0..1) above which pain alone raises the heart rate.
 * @param heartRatePainGain                bpm added above resting by fully saturated pain.
 * @param heartRateStimulantBonus          bpm added by a full stimulant dose.
 * @param heartRateOpioidDrop              bpm removed by full opioid pain suppression.
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
        double bloodMovementPenaltyLossFraction,
        float painShareHead,
        float painShareTorso,
        float painShareArm,
        float painShareLeg,
        float painSaturationK,
        boolean adrenalineEnabled,
        float asphyxiaMoveMultiplier,
        float stimulantSpeedBonus,
        float healthShareHead,
        float healthShareTorso,
        float healthShareArm,
        float healthShareLeg,
        float tourniquetBleedMultiplier,
        float tourniquetLegSpeedMultiplier,
        float tourniquetArmSpeedMultiplier,
        boolean headDepletionInstakill,
        boolean torsoDepletionInstakill,
        double bleedingRateMultiplier,
        boolean cardiacOutputEnabled,
        double cardiacVenousReturnFloor,
        double cardiacOutputFloor,
        boolean heartRateEnabled,
        double heartRateResting,
        double heartRateMax,
        double heartRateBleedInfluence,
        double heartRateCompensationRatio,
        double heartRateDecompensationRatio,
        float heartRatePainThreshold,
        float heartRatePainGain,
        float heartRateStimulantBonus,
        float heartRateOpioidDrop
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
                0.25D,      // bloodMovementPenaltyLossFraction
                0.35F,      // painShareHead
                0.50F,      // painShareTorso
                0.10F,      // painShareArm
                0.20F,      // painShareLeg
                1.00F,      // painSaturationK
                true,       // adrenalineEnabled
                0.25F,      // asphyxiaMoveMultiplier
                0.30F,      // stimulantSpeedBonus
                0.35F,      // healthShareHead
                0.55F,      // healthShareTorso
                0.12F,      // healthShareArm
                0.18F,      // healthShareLeg
                0.20F,      // tourniquetBleedMultiplier
                0.85F,      // tourniquetLegSpeedMultiplier
                0.95F,      // tourniquetArmSpeedMultiplier
                false,      // headDepletionInstakill
                false,      // torsoDepletionInstakill
                0.50D,      // bleedingRateMultiplier
                true,       // cardiacOutputEnabled
                0.50D,      // cardiacVenousReturnFloor
                0.05D,      // cardiacOutputFloor
                true,       // heartRateEnabled
                80.0D,      // heartRateResting
                220.0D,     // heartRateMax
                0.50D,      // heartRateBleedInfluence
                0.70D,      // heartRateCompensationRatio
                0.60D,      // heartRateDecompensationRatio
                0.20F,      // heartRatePainThreshold
                50.0F,      // heartRatePainGain
                40.0F,      // heartRateStimulantBonus
                30.0F       // heartRateOpioidDrop
        );
    }

    /**
     * Arms are deliberately small so an agonising arm cannot alone cause pain-shock; torso/head carry most
     * of the shock-inducing weight.
     */
    public float painShare(LimbType lt) {
        if (lt == LimbType.HEAD) {
            return painShareHead;
        }
        if (lt == LimbType.TORSO) {
            return painShareTorso;
        }
        return lt.isLeg() ? painShareLeg : painShareArm;
    }

    /**
     * Caps each limb's contribution so a single arm/leg never drains the whole pool; combined destruction
     * still sums past the cap and collapses the player.
     */
    public float healthShare(LimbType lt) {
        if (lt == LimbType.HEAD) {
            return healthShareHead;
        }
        if (lt == LimbType.TORSO) {
            return healthShareTorso;
        }
        return lt.isLeg() ? healthShareLeg : healthShareArm;
    }
}
