package com.warfactory.medical.core;

import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;

/**
 * Pure, deterministic derivation of {@link DerivedStats} from a {@link MedicalProfile}.
 *
 * <p>Consumes ONLY the per-limb cached aggregates; the caller must rebuild dirty limb caches first
 * (see {@link MedicalProfile#recompute}). No allocation beyond the single returned record.</p>
 */
public final class Physiology {

    private Physiology() {
    }

    public static DerivedStats compute(MedicalProfile p, PhysiologyParams cfg) {
        double bleeding = 0.0D;
        float limbHealthReduction = 0.0F;
        float movementFromLimbs = 1.0F;
        boolean legFracture = false;
        boolean armFracture = false;
        int fracturedLegs = 0;
        int disabledArms = 0;
        int disabledLegs = 0;
        int armTourniquets = 0;
        int legTourniquets = 0;

        float maxHp = cfg.maxHealthPoints();
        for (LimbType lt : LimbType.VALUES) {
            Limb limb = p.limb(lt);
            // A TOURNIQUET reduces this limb's bleeding OUTPUT (blood loss drains from totalBleeding) without
            // treating the wounds -- remove it and the raw bleeding returns.
            bleeding += limb.hasTourniquet()
                    ? limb.getCachedBleeding() * cfg.tourniquetBleedMultiplier()
                    : limb.getCachedBleeding();
            // Each limb's contribution to the LIFE POOL is CAPPED at its share of the full bar, so a single
            // arm/leg can never drain the whole pool (mirrors the systemic-pain share cap). A limb whose
            // reduction reaches its cap is "drained" -> disabled here (and fractured at damage time); the
            // excess is redirected to bleeding/pain by the damage pipeline rather than sunk into the pool.
            float cap = cfg.healthShare(lt) * maxHp;
            float reduction = limb.getCachedHealthReduction();
            limbHealthReduction += cap > 0.0F ? Math.min(reduction, cap) : reduction;
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
            if (limb.hasTourniquet()) {
                if (lt.isArm()) {
                    armTourniquets++;
                } else if (lt.isLeg()) {
                    legTourniquets++;
                }
            }
        }
        // Both arms drained -> hands unusable (no swing/interact, hidden render); both legs -> forced crawl.
        boolean bothArmsDisabled = disabledArms >= 2;
        boolean bothLegsDisabled = disabledLegs >= 2;
        // A tourniquet on any arm induces weapon sway (synced for the client sway handler); legs/arms also
        // take a per-limb speed penalty below so wearing one permanently is discouraged.
        boolean anyArmTourniquet = armTourniquets > 0;

        // --- PAIN (per body part, capped by a configurable SHARE) --------------------------------------
        // Each limb's raw pain saturates locally (diminishing returns), is reduced by any LOCAL ANESTHETIC
        // on that limb, then has the systemic ANALGESIA (painkiller) mask subtracted (a body-wide floor: small
        // pains vanish entirely, large ones are merely lessened). Two aggregates come out of this:
        //   perceivedPain = the worst single limb's masked pain -> what the player FEELS (sway/vignette/HUD)
        //   systemicPain  = SUM over limbs of (bodyPartShare * maskedPain), clamped to 1 -> what drives
        //                   shock + unconsciousness. Because arms carry a tiny share, an agonising arm reads
        //                   high on perceived pain yet contributes almost nothing to shock.
        float analgesia = p.getPainSuppression();
        if (analgesia < 0.0F) {
            analgesia = 0.0F;
        } else if (analgesia > 1.0F) {
            analgesia = 1.0F;
        }
        // A combat stimulant makes the player very insusceptible to pain: its strength acts as a strong
        // whole-body analgesia floor (like a painkiller) for as long as the stimulant is active.
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
            float local = raw / (raw + saturationK);            // per-limb diminishing returns (0..1)
            float anesthetic = limb.getLocalNumbing();
            if (anesthetic > 0.0F) {                             // local anesthetic on THIS limb
                local *= (1.0F - (Math.min(anesthetic, 1.0F)));
            }
            float masked = local - analgesia;                   // systemic analgesia (painkiller) subtractive mask
            if (masked <= 0.0F) {
                continue;
            }
            if (masked > perceivedPain) {
                perceivedPain = masked;
            }
            systemicPainSum += cfg.painShare(lt) * masked;
        }
        float systemicPain = Math.min(systemicPainSum, 1.0F);
        // "totalPain" in the snapshot is the PERCEIVED pain (feedback); systemicPain drives shock/KO below.
        float totalPain = perceivedPain;

        // Blood volume + LOSS fraction (0 = full, 1 = fully exsanguinated). All the death/unconsciousness
        // thresholds below are expressed as fractions of blood LOST, mirroring the config.
        double bloodMl = p.getBloodMl();
        double maxBlood = cfg.maxBloodMl();
        double bloodLossFraction = maxBlood <= 0.0D ? 0.0D : 1.0D - (bloodMl / maxBlood);
        if (bloodLossFraction < 0.0D) {
            bloodLossFraction = 0.0D;
        }

        // Blood-loss penalty: 0 above the low fraction, ramping to full max-health at the death volume.
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

        // Pain-shock penalty: 0 below the threshold, scaling to painMaxHealthPenalty at full SYSTEMIC pain.
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
        // In this model current health tracks the derived max; integration may clamp it lower.
        float effectiveCurrentHealth = effectiveMaxHealth;

        // --- Unconsciousness SCORE: the SUM of two INDEPENDENT factors, each of which can reach 1.0 on its
        // own, so blood loss alone (at its threshold) OR severe pain alone can knock you out, while lesser
        // amounts of BOTH combine to tip the player over. Reaching 1.0 renders the player unconscious.
        // Blood-loss factor: 0 with no loss, ramping to 1.0 AT the unconscious-loss fraction (default 30%
        // lost) and held there up to the death loss -- losing 30% of your blood is enough to pass out by itself.
        float bloodScore = 0.0F;
        if (cfg.bloodUnconsciousLossFraction() > 0.0D) {
            bloodScore = (float) (bloodLossFraction / cfg.bloodUnconsciousLossFraction());
        }
        if (bloodScore > 1.0F) {
            bloodScore = 1.0F;
        }
        // Pain factor: 0 below the pain-shock threshold, ramping to 1.0 at the pain-unconscious threshold, then
        // scaled by painUnconsciousWeight (default 1.0 = severe pain alone can knock you out; lower = pain only
        // contributes toward a combined knockout).
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

        // Pain-driven knockout is gated by ADRENALINE: when enabled, pain contributes to the ACTUAL knockout
        // only once the engine's grace timer has run out (p.isAdrenalineExhausted()). Until then the player
        // stays up on adrenaline no matter how much it hurts -- though blood loss can still drop them. The
        // painKoPending flag marks (adrenaline-independently) that PAIN, not blood, is the thing trying to
        // knock the player out (blood alone would not), so the engine knows to start / hold that timer.
        boolean painKoAllowed = !cfg.adrenalineEnabled() || p.isAdrenalineExhausted();
        float koScore = bloodScore + (painKoAllowed ? painScore : 0.0F);
        boolean painKoPending = (bloodScore + painScore) >= 1.0F && bloodScore < 1.0F;

        // Bleeding out TOTALLY (blood loss at/past the death fraction) kills outright -- the only instant-death
        // physiology condition. The engine turns this derived DEAD into an actual death (drops health to 0).
        boolean bloodDeath = bloodLossFraction >= cfg.bloodDeathLossFraction();
        // A full knockout score (koScore -- pain gated by adrenaline) OR trauma zeroing the effective max
        // health collapses the player. This is a SURVIVABLE downed state (blood loss, not trauma, is what
        // actually kills) when bleed-out is on.
        boolean unconsciousTrigger = koScore >= 1.0F || effectiveMaxHealth <= 0.0F;

        // Health-based state progression (Healthy -> Critical -> Unconscious -> Dead). Catastrophic blood loss
        // takes precedence; then the collapse trigger (downed while bleed-out is on, else death); the building
        // score / low blood / low health reads as CRITICAL below that.
        HealthState state;
        if (bloodDeath) {
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

        // Overdose unconsciousness: now a CAUSE of the single unified UNCONSCIOUS state rather than a separate
        // flag. Raise the state to UNCONSCIOUS while an opioid overdose has the player unconscious, but never
        // DOWNGRADE a strictly-worse derived condition (DEAD). The engine's wake timer clears the overdose
        // marker, after which this no longer applies (unless a bleed-out condition independently holds).
        if (p.isOverdoseUnconscious() && state.ordinal() < HealthState.UNCONSCIOUS.ordinal()) {
            state = HealthState.UNCONSCIOUS;
        }

        // Asphyxia unconsciousness (drowning / drug respiratory depression) likewise raises the state to
        // UNCONSCIOUS; the engine runs a DEATH timer for it (fatal unless the cause is cleared in time).
        if (p.isAsphyxiaUnconscious() && state.ordinal() < HealthState.UNCONSCIOUS.ordinal()) {
            state = HealthState.UNCONSCIOUS;
        }

        // Admin-forced override: honour an operator-pinned state (e.g. /wfmedical unconscious on an uninjured
        // player) that the pure physiology would not itself derive, but never DOWNGRADE a genuinely worse
        // derived condition. This keeps the forced state, its mobility lock and the downed pose stable across
        // every recompute instead of being clobbered back to the derived value.
        HealthState forced = p.getForcedState();
        if (forced != null && forced.ordinal() > state.ordinal()) {
            state = forced;
        }

        // Incapacitation (movement 0, sprint blocked, no jump) applies uniformly whenever the player is
        // UNCONSCIOUS — covering every cause (bleed-out unconsciousness, overdose unconsciousness, admin-forced)
        // through the single merged state.
        boolean incapacitated = state == HealthState.UNCONSCIOUS;

        // Overdose asphyxia: the conscious pre-unconsciousness respiratory-depression phase. The player can
        // still walk, so it does NOT incapacitate, but it blocks sprint and drives the client blur (below). It
        // is only meaningful while still conscious; once the state has tipped to UNCONSCIOUS/DEAD it no longer
        // applies (the merged unconscious handling takes over).
        boolean asphyxiating = p.isAsphyxiating() && !incapacitated && state != HealthState.DEAD;

        // Movement & jump are affected ONLY by LEG injuries and SEVERE BLOOD LOSS -- never by general pain or
        // by arm/head/torso wounds. movementFromLimbs is already leg-only (the per-limb cache leaves non-leg
        // limbs at 1.0). Severe blood loss ramps a slowdown in from bloodMovementPenaltyLossFraction (default
        // 25% lost) down to the pain-speed floor at the death loss.
        float bloodMove = bloodMovementMultiplier(bloodLossFraction, cfg);
        boolean severeBloodLoss = bloodLossFraction >= cfg.bloodMovementPenaltyLossFraction();

        float movement = movementFromLimbs;
        for (int i = 0; i < fracturedLegs; i++) {
            movement *= cfg.legFractureSpeedMultiplier();
        }
        // Tourniquets restrict circulation -> a per-limb speed penalty (legs heavier than arms).
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
            // Combat stimulant overrides the injury slowdown and boosts speed above normal (pushes through it).
            if (stimulant > 0.0F) {
                float boosted = 1.0F + cfg.stimulantSpeedBonus() * stimulant;
                if (boosted > movement) {
                    movement = boosted;
                }
            }
            // Heavy movement constraint while consciously asphyxiating (below the pain floor) — you can barely
            // move while suffocating. Applied after the boost so suffocation still slows a stimulated player.
            if (asphyxiating) {
                movement *= cfg.asphyxiaMoveMultiplier();
            }
            // Both legs drained: forced crawl -- clamp to a slow crawl (holds even through a stimulant, since
            // two destroyed legs cannot be run on regardless of injury masking).
            if (bothLegsDisabled && movement > cfg.painSpeedFloor()) {
                movement = cfg.painSpeedFloor();
            }
        }

        boolean sprintBlocked = legFracture || severeBloodLoss || incapacitated || asphyxiating || bothLegsDisabled;

        float jumpMultiplier;
        if (legFracture || incapacitated || asphyxiating || bothLegsDisabled) {
            jumpMultiplier = 0.0F;
        } else {
            // Leg trauma + severe blood loss reduce jump; general pain and non-leg wounds do not.
            jumpMultiplier = movementFromLimbs * bloodMove;
            if (jumpMultiplier < 0.0F) {
                jumpMultiplier = 0.0F;
            } else if (jumpMultiplier > 1.0F) {
                jumpMultiplier = 1.0F;
            }
        }
        // Combat stimulant clears any jump penalty (masks the injury — even a broken leg), except while out
        // cold or suffocating.
        if (stimulant > 0.0F && !incapacitated && !asphyxiating && !bothLegsDisabled) {
            jumpMultiplier = 1.0F;
        }

        return new DerivedStats(
                effectiveMaxHealth,
                healthModifier,
                effectiveCurrentHealth,
                bleeding,
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
                anyArmTourniquet
        );
    }

    /**
     * 1.0 until bloodMovementPenaltyLossFraction is lost, then ramps to painSpeedFloor at death loss.
     * Blood loss is the ONLY path that slows the player independent of pain / non-leg trauma.
     */
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
