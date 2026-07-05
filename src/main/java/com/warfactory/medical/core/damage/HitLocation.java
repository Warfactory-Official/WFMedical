package com.warfactory.medical.core.damage;

import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Picks which {@link LimbType} an incoming hit lands on. Uses each limb's base {@code hitWeight}, then
 * nudges the distribution by {@link DamageCategory}: ballistic/piercing projectiles slightly favour the
 * head and torso, while falls favour the legs. Fully deterministic for a given {@link RandomSource}.
 */
public final class HitLocation {

    private HitLocation() {
    }

    public static LimbType pick(DamageSource source, DamageCategory cat, RandomSource rand) {
        LimbType[] limbs = LimbType.values();
        float[] weights = new float[limbs.length];
        float total = 0.0F;
        for (int i = 0; i < limbs.length; i++) {
            float w = limbs[i].getHitWeight() * categoryBias(limbs[i], cat);
            if (w < 0.0F) {
                w = 0.0F;
            }
            weights[i] = w;
            total += w;
        }
        if (total <= 0.0F || rand == null) {
            return LimbType.TORSO;
        }
        float roll = rand.nextFloat() * total;
        float acc = 0.0F;
        for (int i = 0; i < limbs.length; i++) {
            acc += weights[i];
            if (roll < acc) {
                return limbs[i];
            }
        }
        return limbs[limbs.length - 1];
    }

    private static float categoryBias(LimbType limb, DamageCategory cat) {
        if (cat == null) {
            return 1.0F;
        }
        switch (cat) {
            case BALLISTIC:
            case PIERCING:
                // Aimed/directed projectiles trend towards centre-of-mass and head.
                if (limb == LimbType.TORSO) {
                    return 1.35F;
                }
                if (limb == LimbType.HEAD) {
                    return 1.5F;
                }
                return 0.85F;
            case FALL:
                // Impact damage from falling is absorbed by the legs, then torso.
                if (limb.isLeg()) {
                    return 2.5F;
                }
                if (limb == LimbType.TORSO) {
                    return 1.1F;
                }
                return 0.4F;
            case EXPLOSION:
                // Blasts spread widely; flatten slightly towards the extremities.
                return limb.isVital() ? 0.9F : 1.2F;
            default:
                return 1.0F;
        }
    }
}
