package com.warfactory.medical.stress.client;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Aim shake from stress: bursts at {@link #ONSET}+, merging into a continuous floor by {@link #CONTINUOUS_TO}; peak
 * angle {@link #amplitude}. Offsets in degrees, rewritten each {@link #tick}.
 */
public final class StressShake {
    public static final float CRITICAL = 0.8F;
    public static final float ONSET = 0.15F;
    public static final float CONTINUOUS_FROM = 0.55F;
    public static final float CONTINUOUS_TO = 0.85F;
    private static final float AMPLITUDE_LOW = 0.15F;
    private static final float AMPLITUDE_CRITICAL = 1.1F;
    private static final float AMPLITUDE_MAX = 3.4F;
    private static final float CHANCE_LOW = 0.01F;
    private static final float CHANCE_HIGH = 0.12F;
    private static final float NOISE_FOLLOW = 0.6F;

    public float yaw;
    public float pitch;
    private int burstTick;
    private int burstLength;
    private float noiseYaw;
    private float noisePitch;

    public static float amplitude(float stress) {
        if (stress < ONSET) {
            return 0.0F;
        }
        if (stress <= CRITICAL) {
            return Mth.lerp((stress - ONSET) / (CRITICAL - ONSET), AMPLITUDE_LOW, AMPLITUDE_CRITICAL);
        }
        return Mth.lerp((stress - CRITICAL) / (1.0F - CRITICAL), AMPLITUDE_CRITICAL, AMPLITUDE_MAX);
    }

    /** Per tick chance a burst starts. */
    public static float burstChance(float stress) {
        return Mth.lerp(Mth.clamp((stress - ONSET) / (CONTINUOUS_FROM - ONSET), 0.0F, 1.0F), CHANCE_LOW, CHANCE_HIGH);
    }

    /** Envelope floor: 0 below {@link #CONTINUOUS_FROM}, 1 from {@link #CONTINUOUS_TO}. */
    public static float floor(float stress) {
        float t = Mth.clamp((stress - CONTINUOUS_FROM) / (CONTINUOUS_TO - CONTINUOUS_FROM), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** @param weight 1 holding a weapon, less otherwise (scales the angle) */
    public void tick(float stress, float weight, RandomSource random) {
        if (stress < ONSET || weight <= 0.0F) {
            burstLength = 0;
            yaw = 0.0F;
            pitch = 0.0F;
            return;
        }
        if (burstLength == 0 && random.nextFloat() < burstChance(stress)) {
            burstLength = 4 + random.nextInt(9);
            burstTick = 0;
        }
        float window = 0.0F;
        if (burstLength > 0) {
            window = Mth.sin((float) Math.PI * (burstTick + 0.5F) / burstLength);
            if (++burstTick >= burstLength) {
                burstLength = 0;
            }
        }
        float envelope = Math.max(floor(stress), window);
        noiseYaw = Mth.lerp(NOISE_FOLLOW, noiseYaw, random.nextFloat() * 2.0F - 1.0F);
        noisePitch = Mth.lerp(NOISE_FOLLOW, noisePitch, random.nextFloat() * 2.0F - 1.0F);
        float scale = amplitude(stress) * envelope * weight;
        yaw = scale * noiseYaw;
        pitch = scale * noisePitch;
    }

    public void reset() {
        burstLength = 0;
        burstTick = 0;
        noiseYaw = 0.0F;
        noisePitch = 0.0F;
        yaw = 0.0F;
        pitch = 0.0F;
    }
}
