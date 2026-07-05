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

        // Blood-loss penalty: 0 above the low fraction, ramping to full max-health at the death volume.
        double bloodMl = p.getBloodMl();
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

        // Determine lethal / knockdown condition up front so mobility can react to it.
        boolean lethal = bloodMl <= deathMl || effectiveMaxHealth <= 0.0F;
        boolean knockdown = lethal && cfg.knockdownEnabled();

        // Health-based state progression (Healthy -> Critical -> Knocked Down -> Dead), derived purely from
        // blood / trauma / pain. Computed here (rather than at the tail) so mobility can react to a forced
        // knockdown too.
        HealthState state;
        if (lethal) {
            state = cfg.knockdownEnabled() ? HealthState.KNOCKED_DOWN : HealthState.DEAD;
        } else if (effectiveCurrentHealth <= cfg.maxHealthPoints() * cfg.bloodCriticalFraction()
                || bloodMl <= cfg.bloodCriticalFraction() * cfg.maxBloodMl()) {
            state = HealthState.CRITICAL;
        } else {
            state = HealthState.HEALTHY;
        }
        // Admin-forced override: honour an operator-pinned state (e.g. /wfmedical knockdown on an uninjured
        // player) that the pure physiology would not itself derive, but never DOWNGRADE a genuinely worse
        // derived condition. This keeps the forced state, its mobility lock and the downed pose stable across
        // every recompute instead of being clobbered back to the derived value.
        HealthState forced = p.getForcedState();
        if (forced != null && forced.ordinal() > state.ordinal()) {
            state = forced;
        }

        // Overdose blackout: an orthogonal incapacitation (unconscious). Treated like knockdown for mobility
        // (movement 0, sprint blocked, no jump) but it does NOT change the health-based HealthState. A
        // knocked-down player (derived OR admin-forced) is likewise incapacitated for mobility purposes.
        boolean blackout = p.isBlackoutActive();
        boolean incapacitated = knockdown || blackout || state == HealthState.KNOCKED_DOWN;

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
                || incapacitated;

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
                blackout
        );
    }
}
