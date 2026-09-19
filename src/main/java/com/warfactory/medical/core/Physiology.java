package com.warfactory.medical.core;

import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;

public final class Physiology {

    private Physiology() {
    }

    public static DerivedStats compute(MedicalProfile p, PhysiologyParams cfg) {
        double bleeding = 0.0D;
        float limbHealthReduction = 0.0F;
        float currentHealthDeficit = 0.0F;
        float movementFromLimbs = 1.0F;
        boolean legFracture = false;
        boolean armFracture = false;
        boolean headDestroyed = false;
        boolean torsoDestroyed = false;
        int fracturedLegs = 0;
        int disabledArms = 0;
        int disabledLegs = 0;
        int armTourniquets = 0;
        int legTourniquets = 0;

        float maxHp = cfg.maxHealthPoints();
        for (LimbType lt : LimbType.VALUES) {
            Limb limb = p.limb(lt);
            bleeding += limb.hasTourniquet()
                    ? limb.getCachedBleeding() * cfg.tourniquetBleedMultiplier()
                    : limb.getCachedBleeding();
            float cap = cfg.healthShare(lt) * maxHp;
            float reduction = limb.getCachedHealthReduction();
            limbHealthReduction += cap > 0.0F ? Math.min(reduction, cap) : reduction;
            currentHealthDeficit += limb.getCachedCurrentHealthReduction();
            boolean drained = cap > 0.0F && reduction >= cap;
            movementFromLimbs *= limb.getCachedMovementMultiplier();
            if (limb.hasCachedFracture()) {
                if (lt.isLeg()) {
                    legFracture = true;
                    fracturedLegs++;
                } else if (lt.isArm()) {
                    armFracture = true;
                }
            }
            if (lt.isArm() && drained) {
                disabledArms++;
            } else if (lt.isLeg() && drained) {
                disabledLegs++;
            }
            if (drained) {
                if (lt == LimbType.HEAD) {
                    headDestroyed = true;
                } else if (lt == LimbType.TORSO) {
                    torsoDestroyed = true;
                }
            }
            if (limb.hasTourniquet()) {
                if (lt.isArm()) {
                    armTourniquets++;
                } else if (lt.isLeg()) {
                    legTourniquets++;
                }
            }
        }
        boolean bothArmsDisabled = disabledArms >= 2;
        boolean bothLegsDisabled = disabledLegs >= 2;
        boolean anyArmTourniquet = armTourniquets > 0;

        float analgesia = p.getPainSuppression();
        if (analgesia < 0.0F) {
            analgesia = 0.0F;
        } else if (analgesia > 1.0F) {
            analgesia = 1.0F;
        }
        float stimulant = p.getStimulant();
        if (stimulant > analgesia) {
            analgesia = stimulant;
        }
        float saturationK = cfg.painSaturationK();
        if (saturationK <= 0.0F) {
            saturationK = 0.0001F;
        }
        float perceivedPain = 0.0F;
        float systemicPainSum = 0.0F;
        for (LimbType lt : LimbType.VALUES) {
            Limb limb = p.limb(lt);
            float raw = limb.getCachedPain();
            if (raw <= 0.0F) {
                continue;
            }
            float local = raw / (raw + saturationK);
            float anesthetic = limb.getLocalNumbing();
            if (anesthetic > 0.0F) {
                local *= (1.0F - (Math.min(anesthetic, 1.0F)));
            }
            float masked = local - analgesia;
            if (masked <= 0.0F) {
                continue;
            }
            if (masked > perceivedPain) {
                perceivedPain = masked;
            }
            systemicPainSum += cfg.painShare(lt) * masked;
        }
        float systemicPain = Math.min(systemicPainSum, 1.0F);
        float totalPain = perceivedPain;

        double bloodMl = p.getBloodMl();
        double maxBlood = cfg.maxBloodMl();
        double bloodLossFraction = maxBlood <= 0.0D ? 0.0D : 1.0D - (bloodMl / maxBlood);
        if (bloodLossFraction < 0.0D) {
            bloodLossFraction = 0.0D;
        }

        double bloodRatio = maxBlood <= 0.0D ? 1.0D : bloodMl / maxBlood;
        float heartRate = cfg.heartRateEnabled() ? p.getHeartRate() : (float) cfg.heartRateResting();
        double cardiacOutput = Cardio.cardiacOutput(bloodRatio, heartRate, cfg);
        double bleedScale = Cardio.bleedScale(bloodRatio, heartRate, cfg);

        double lowMl = cfg.bloodLowFraction() * cfg.maxBloodMl();
        double deathMl = cfg.bloodDeathMl();
        float bloodLossPenalty = 0.0F;
        if (bloodMl < lowMl) {
            double range = lowMl - deathMl;
            double t = range <= 0.0D ? 1.0D : (lowMl - bloodMl) / range;
            if (t < 0.0D) {
                t = 0.0D;
            } else if (t > 1.0D) {
                t = 1.0D;
            }
            bloodLossPenalty = (float) (cfg.maxHealthPoints() * t);
        }

        float painShockPenalty = 0.0F;
        if (systemicPain > cfg.painShockThreshold()) {
            float span = 1.0F - cfg.painShockThreshold();
            float t = span <= 0.0F ? 1.0F : (systemicPain - cfg.painShockThreshold()) / span;
            painShockPenalty = cfg.painMaxHealthPenalty() * t;
        }

        float healthModifier = limbHealthReduction + bloodLossPenalty + painShockPenalty;
        float effectiveMaxHealth = cfg.maxHealthPoints() - healthModifier;
        if (effectiveMaxHealth < 0.0F) {
            effectiveMaxHealth = 0.0F;
        }
        // Minor injuries (bruises, a fall's blunt trauma) sap current health without lowering the max;
        // it recovers on its own as those traumas self-heal.
        float effectiveCurrentHealth = effectiveMaxHealth - currentHealthDeficit;
        if (effectiveCurrentHealth < 0.0F) {
            effectiveCurrentHealth = 0.0F;
        }

        float bloodScore = 0.0F;
        if (cfg.bloodUnconsciousLossFraction() > 0.0D) {
            bloodScore = (float) (bloodLossFraction / cfg.bloodUnconsciousLossFraction());
        }
        if (bloodScore > 1.0F) {
            bloodScore = 1.0F;
        }
        float painScore = 0.0F;
        float painSpan = cfg.painUnconsciousThreshold() - cfg.painShockThreshold();
        if (painSpan > 0.0F && systemicPain > cfg.painShockThreshold()) {
            painScore = (systemicPain - cfg.painShockThreshold()) / painSpan;
            if (painScore > 1.0F) {
                painScore = 1.0F;
            }
        }
        painScore *= cfg.painUnconsciousWeight();
        float unconsciousScore = bloodScore + painScore;

        boolean painKoAllowed = !cfg.adrenalineEnabled() || p.isAdrenalineExhausted();
        float koScore = bloodScore + (painKoAllowed ? painScore : 0.0F);
        boolean painKoPending = (bloodScore + painScore) >= 1.0F && bloodScore < 1.0F;

        boolean vitalDestroyed = headDestroyed || torsoDestroyed;
        boolean vitalInstakill = (headDestroyed && cfg.headDepletionInstakill())
                || (torsoDestroyed && cfg.torsoDepletionInstakill());
        boolean bloodDeath = bloodLossFraction >= cfg.bloodDeathLossFraction();
        // A casualty brought round by CPR is still, by the numbers, down: they came up at the very threshold
        // that downed them. The grace window is what stops them folding again on the next recompute and gives
        // the medic time to actually treat them. It never blocks bleed-out death or a destroyed vital.
        boolean unconsciousTrigger = (koScore >= 1.0F && !p.isReviveGraceActive())
                || effectiveMaxHealth <= 0.0F || vitalDestroyed;

        HealthState state;
        if (bloodDeath || vitalInstakill) {
            state = HealthState.DEAD;
        } else if (unconsciousTrigger) {
            state = cfg.bleedoutEnabled() ? HealthState.UNCONSCIOUS : HealthState.DEAD;
        } else if (unconsciousScore >= 0.5F
                || effectiveCurrentHealth <= cfg.maxHealthPoints() * cfg.bloodCriticalFraction()
                || bloodMl <= cfg.bloodCriticalFraction() * cfg.maxBloodMl()) {
            state = HealthState.CRITICAL;
        } else {
            state = HealthState.HEALTHY;
        }

        if (p.isOverdoseUnconscious() && state.ordinal() < HealthState.UNCONSCIOUS.ordinal()) {
            state = HealthState.UNCONSCIOUS;
        }

        if (p.isAsphyxiaUnconscious() && state.ordinal() < HealthState.UNCONSCIOUS.ordinal()) {
            state = HealthState.UNCONSCIOUS;
        }

        if (p.isUnconsciousLatched() && state.ordinal() < HealthState.UNCONSCIOUS.ordinal()) {
            state = HealthState.UNCONSCIOUS;
        }

        HealthState forced = p.getForcedState();
        if (forced != null && forced.ordinal() > state.ordinal()) {
            state = forced;
        }

        boolean incapacitated = state == HealthState.UNCONSCIOUS;

        boolean asphyxiating = p.isAsphyxiating() && !incapacitated && state != HealthState.DEAD;

        float bloodMove = bloodMovementMultiplier(bloodLossFraction, cfg);
        boolean severeBloodLoss = bloodLossFraction >= cfg.bloodMovementPenaltyLossFraction();

        float movement = movementFromLimbs;
        for (int i = 0; i < fracturedLegs; i++) {
            movement *= cfg.legFractureSpeedMultiplier();
        }
        for (int i = 0; i < legTourniquets; i++) {
            movement *= cfg.tourniquetLegSpeedMultiplier();
        }
        for (int i = 0; i < armTourniquets; i++) {
            movement *= cfg.tourniquetArmSpeedMultiplier();
        }
        movement *= bloodMove;
        if (incapacitated) {
            movement = 0.0F;
        } else {
            if (movement < cfg.painSpeedFloor()) {
                movement = cfg.painSpeedFloor();
            }
            if (stimulant > 0.0F) {
                float boosted = 1.0F + cfg.stimulantSpeedBonus() * stimulant;
                if (boosted > movement) {
                    movement = boosted;
                }
            }
            if (asphyxiating) {
                movement *= cfg.asphyxiaMoveMultiplier();
            }
            if (bothLegsDisabled && movement > cfg.painSpeedFloor()) {
                movement = cfg.painSpeedFloor();
            }
        }

        boolean sprintBlocked = legFracture || severeBloodLoss || incapacitated || asphyxiating || bothLegsDisabled;

        float jumpMultiplier;
        if (legFracture || incapacitated || asphyxiating || bothLegsDisabled) {
            jumpMultiplier = 0.0F;
        } else {
            jumpMultiplier = movementFromLimbs * bloodMove;
            if (jumpMultiplier < 0.0F) {
                jumpMultiplier = 0.0F;
            } else if (jumpMultiplier > 1.0F) {
                jumpMultiplier = 1.0F;
            }
        }
        if (stimulant > 0.0F && !incapacitated && !asphyxiating && !bothLegsDisabled) {
            jumpMultiplier = 1.0F;
        }

        return new DerivedStats(
                effectiveMaxHealth,
                healthModifier,
                effectiveCurrentHealth,
                bleeding * cfg.bleedingRateMultiplier() * bleedScale,
                totalPain,
                systemicPain,
                movement,
                sprintBlocked,
                jumpMultiplier,
                state,
                legFracture,
                armFracture,
                asphyxiating,
                painKoPending,
                bothArmsDisabled,
                bothLegsDisabled,
                anyArmTourniquet,
                cardiacOutput,
                heartRate
        );
    }

    private static float bloodMovementMultiplier(double lossFraction, PhysiologyParams cfg) {
        double onset = cfg.bloodMovementPenaltyLossFraction();
        if (lossFraction <= onset) {
            return 1.0F;
        }
        double span = cfg.bloodDeathLossFraction() - onset;
        double t = span <= 0.0D ? 1.0D : (lossFraction - onset) / span;
        if (t > 1.0D) {
            t = 1.0D;
        }
        return (float) (1.0D - (1.0D - cfg.painSpeedFloor()) * t);
    }
}
