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
        float painSum = 0.0F;
        float limbHealthReduction = 0.0F;
        float movementFromLimbs = 1.0F;
        boolean legFracture = false;
        boolean armFracture = false;
        int fracturedLegs = 0;

        for (LimbType lt : LimbType.VALUES) {
            Limb limb = p.limb(lt);
            bleeding += limb.getCachedBleeding();
            painSum += limb.getCachedPain();
            limbHealthReduction += limb.getCachedHealthReduction();
            movementFromLimbs *= limb.getCachedMovementMultiplier();
            if (limb.hasCachedFracture()) {
                if (lt.isLeg()) {
                    legFracture = true;
                    fracturedLegs++;
                } else if (lt.isArm()) {
                    armFracture = true;
                }
            }
        }

        // Pain saturates to 0..1 via a smooth diminishing-returns curve.
        float totalPain = painSum <= 0.0F ? 0.0F : painSum / (painSum + 1.0F);

        // Painkillers reduce PERCEIVED pain without healing the injury (severity/bleeding/health untouched).
        // The suppression fraction decays over time in the engine; here it only masks the derived pain.
        float suppression = p.getPainSuppression();
        if (suppression > 0.0F) {
            totalPain *= (1.0F - (suppression > 1.0F ? 1.0F : suppression));
            if (totalPain < 0.0F) {
                totalPain = 0.0F;
            }
        }

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

        // Pain-shock penalty: 0 below the threshold, scaling to painMaxHealthPenalty at full pain.
        float painShockPenalty = 0.0F;
        if (totalPain > cfg.painShockThreshold()) {
            float span = 1.0F - cfg.painShockThreshold();
            float t = span <= 0.0F ? 1.0F : (totalPain - cfg.painShockThreshold()) / span;
            painShockPenalty = cfg.painMaxHealthPenalty() * t;
        }

        float healthModifier = limbHealthReduction + bloodLossPenalty + painShockPenalty;
        float effectiveMaxHealth = cfg.maxHealthPoints() - healthModifier;
        if (effectiveMaxHealth < 0.0F) {
            effectiveMaxHealth = 0.0F;
        }
        // In this model current health tracks the derived max; integration may clamp it lower.
        float effectiveCurrentHealth = effectiveMaxHealth;

        // --- Unconsciousness SCORE (0..1): a weighted, EXTENSIBLE combination of the factors that push a
        // player toward passing out. Reaching 1.0 on this recalculation renders the player unconscious
        // instantly. For now the factors are blood loss and severe pain; more can simply add to the sum.
        float unconsciousScore = 0.0F;
        // Blood loss: 0 below the unconscious-loss fraction (default 30% lost), ramping to 1 at the death-loss
        // fraction (default 40% lost) -- i.e. the 30%-40% blood-loss band alone can knock you out.
        double bloodBandSpan = cfg.bloodDeathLossFraction() - cfg.bloodUnconsciousLossFraction();
        if (bloodLossFraction > cfg.bloodUnconsciousLossFraction() && bloodBandSpan > 0.0D) {
            unconsciousScore += (float) ((bloodLossFraction - cfg.bloodUnconsciousLossFraction()) / bloodBandSpan);
        }
        // Severe pain: 0 below the pain threshold, ramping by the configured weight up to full pain, so pain
        // can tip a partially-bled player over the edge (or, at weight >= 1, knock them out on its own).
        float painBandSpan = 1.0F - cfg.painUnconsciousThreshold();
        if (totalPain > cfg.painUnconsciousThreshold() && painBandSpan > 0.0F) {
            unconsciousScore += cfg.painUnconsciousWeight()
                    * (totalPain - cfg.painUnconsciousThreshold()) / painBandSpan;
        }
        if (unconsciousScore < 0.0F) {
            unconsciousScore = 0.0F;
        }

        // Bleeding out TOTALLY (blood loss at/past the death fraction) kills outright -- the only instant-death
        // physiology condition. The engine turns this derived DEAD into an actual death (drops health to 0).
        boolean bloodDeath = bloodLossFraction >= cfg.bloodDeathLossFraction();
        // A full unconsciousness score OR trauma zeroing the effective max health collapses the player. This
        // is a SURVIVABLE downed state (blood loss, not trauma, is what actually kills) when bleed-out is on.
        boolean unconsciousTrigger = unconsciousScore >= 1.0F || effectiveMaxHealth <= 0.0F;

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

        // Movement: leg-fracture multipliers * pain slowdown, floored.
        float movement = movementFromLimbs;
        for (int i = 0; i < fracturedLegs; i++) {
            movement *= cfg.legFractureSpeedMultiplier();
        }
        movement *= (1.0F - 0.5F * totalPain);
        if (incapacitated) {
            movement = 0.0F;
        } else if (movement < cfg.painSpeedFloor()) {
            movement = cfg.painSpeedFloor();
        }

        boolean lowBlood = bloodMl < lowMl;
        boolean sprintBlocked = legFracture
                || totalPain > cfg.painShockThreshold()
                || lowBlood
                || incapacitated
                || asphyxiating;

        float jumpMultiplier;
        if (legFracture || incapacitated) {
            jumpMultiplier = 0.0F;
        } else {
            jumpMultiplier = 1.0F - totalPain;
            if (jumpMultiplier < 0.0F) {
                jumpMultiplier = 0.0F;
            }
        }

        return new DerivedStats(
                effectiveMaxHealth,
                healthModifier,
                effectiveCurrentHealth,
                bleeding,
                totalPain,
                movement,
                sprintBlocked,
                jumpMultiplier,
                state,
                legFracture,
                armFracture,
                asphyxiating
        );
    }
}
