package com.warfactory.medical.stress.client;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StressShakeTest {

    private static final int TICKS = 4000;

    /** Share of ticks with a visible offset, and the peak angle. */
    private static float[] run(float stress, float weight) {
        StressShake shake = new StressShake();
        RandomSource random = RandomSource.create(7L);
        int active = 0;
        float peak = 0.0F;
        for (int i = 0; i < TICKS; i++) {
            shake.tick(stress, weight, random);
            float mag = Math.max(Math.abs(shake.yaw), Math.abs(shake.pitch));
            if (mag > 0.01F) {
                active++;
            }
            peak = Math.max(peak, mag);
        }
        return new float[]{active / (float) TICKS, peak};
    }

    @Test
    void noShakeBelowTheOnset() {
        assertEquals(0.0F, run(0.1F, 1.0F)[1]);
    }

    @Test
    void lowStressShakesInBurstsAndHighStressContinuously() {
        float[] low = run(0.3F, 1.0F);
        float[] high = run(0.9F, 1.0F);
        assertTrue(low[0] > 0.02F && low[0] < 0.5F, "sporadic: " + low[0]);
        assertTrue(high[0] > 0.9F, "continuous: " + high[0]);
    }

    @Test
    void theAngleGrowsWithStressAndJumpsAtTheCriticalBand() {
        assertTrue(run(0.3F, 1.0F)[1] < run(0.7F, 1.0F)[1]);
        assertTrue(run(1.0F, 1.0F)[1] > 2.0F * run(0.8F, 1.0F)[1]);
    }

    @Test
    void noWeaponNoShake() {
        assertEquals(0.0F, run(0.9F, 0.0F)[1]);
    }
}
