package com.warfactory.medical.damage;

import com.warfactory.medical.body.limb.LimbType;

/**
 * xray-monolith's critical-hit stagger as a rule: every hit adds {@link #power} to a decaying meter, and a meter past
 * the threshold staggers the casualty (engine: {@code critical_wound_threshold} 0.35, {@code decrease_quant},
 * body-part section picks the {@code critical_hit_*} clip). Pure, so the balance is unit-testable.
 */
public final class Stagger {

    /** Rifle-round energy that counts as one full unit of impact. */
    static final float REFERENCE_ENERGY = 10.0F;

    /** Body part sections of the engine's {@code body_parts_section_id}. */
    public enum Zone {
        HEAD(0.45F, 12), TORSO(0.30F, 10), ARM_L(0.18F, 8), ARM_R(0.18F, 8), LEG_L(0.22F, 14), LEG_R(0.22F, 14);

        /** Meter gain per {@link #REFERENCE_ENERGY} of impact. */
        final float weight;
        /** Stagger length, ticks. */
        final int ticks;

        Zone(float weight, int ticks) {
            this.weight = weight;
            this.ticks = ticks;
        }

        public int ticks() {
            return ticks;
        }

        public boolean isLeg() {
            return this == LEG_L || this == LEG_R;
        }

        public static Zone of(LimbType limb) {
            return switch (limb) {
                case HEAD -> HEAD;
                case TORSO -> TORSO;
                case LEFT_ARM -> ARM_L;
                case RIGHT_ARM -> ARM_R;
                case LEFT_LEG -> LEG_L;
                case RIGHT_LEG -> LEG_R;
            };
        }
    }

    private Stagger() {
    }

    /** Meter gain of a hit; {@code jitter} in [0.75, 1.25] is the roll that makes a borderline hit a chance. */
    public static float power(float impactEnergy, Zone zone, float jitter) {
        return impactEnergy / REFERENCE_ENERGY * zone.weight * jitter;
    }

    /** What a casualty's meter holds: gained per hit, drained over time, emptied by a stagger. */
    public static final class Meter {
        private float value;
        private long lastTick;
        private long freeAt;

        /** @return true if this hit staggers (meter past {@code threshold}, none already running). */
        public boolean add(float power, long now, float threshold, float decayPerTick, int staggerTicks) {
            value = Math.max(0.0F, value - (now - lastTick) * decayPerTick) + power;
            lastTick = now;
            if (value < threshold || now < freeAt) {
                return false;
            }
            value = 0.0F;
            freeAt = now + staggerTicks + COOLDOWN_TICKS;
            return true;
        }

        public float value() {
            return value;
        }

        private static final int COOLDOWN_TICKS = 20;
    }
}
