package com.warfactory.medical.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StressTest {

    static final Stress.Params P = new Stress.Params(true, 0.15F, 0.03F, 0.004F, 0.05F, 100, 4, 0.015F, 3.0F, 0.35F, 1.0F);

    @Test
    void painBelowTheThresholdAddsNothingAndFullPainAddsTheGain() {
        assertEquals(0.0F, Stress.painRate(0.15F, P));
        assertEquals(0.03F, Stress.painRate(1.0F, P), 1.0e-6F);
        assertEquals(0.015F, Stress.painRate(0.575F, P), 1.0e-6F);
    }

    @Test
    void recoveryIsFasterTheHigherTheStress() {
        assertTrue(Stress.regenRate(0.9F, P) > Stress.regenRate(0.2F, P) * 3);
    }

    @Test
    void fullStressClearsInAboutAMinuteAndLowStressInUnderTwenty() {
        assertTrue(secondsToClear(1.0F) > 40 && secondsToClear(1.0F) < 70, "from 100%: " + secondsToClear(1.0F));
        assertTrue(secondsToClear(0.1F) < 20, "from 10%: " + secondsToClear(0.1F));
    }

    @Test
    void noRecoveryWhileNotCalm() {
        assertEquals(0.6F, Stress.advance(0.6F, 0.0F, false, 10.0D, P));
    }

    @Test
    void painRisesAndBeatsRecoveryInTheSameStep() {
        float next = Stress.advance(0.3F, 1.0F, true, 1.0D, P);
        assertEquals(0.33F, next, 1.0e-6F);
    }

    @Test
    void stressStaysInUnitRange() {
        assertEquals(1.0F, Stress.advance(0.99F, 1.0F, false, 100.0D, P));
        assertEquals(0.0F, Stress.advance(0.01F, 0.0F, true, 100.0D, P));
    }

    @Test
    void suppressionFallsLinearlyToZeroAtTheRange() {
        assertEquals(0.015F, Stress.suppression(0.0D, P), 1.0e-6F);
        assertEquals(0.0075F, Stress.suppression(1.5D, P), 1.0e-6F);
        assertEquals(0.0F, Stress.suppression(3.0D, P));
        assertEquals(0.0F, Stress.suppression(9.0D, P));
    }

    private static int secondsToClear(float from) {
        float s = from;
        int t = 0;
        while (s > 0.0F && t < 1000) {
            s = Stress.advance(s, 0.0F, true, 1.0D, P);
            t++;
        }
        return t;
    }
}
