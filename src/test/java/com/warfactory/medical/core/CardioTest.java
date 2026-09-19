package com.warfactory.medical.core;

import com.warfactory.medical.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The circulatory loop, ported from ACE3: blood volume sets how well the ventricle fills, heart rate sets
 * how often it empties, and the product is what every wound's bleed rate is scaled by. The interesting
 * behaviour is the feedback -- a patient who is losing blood raises their heart rate to hold their blood
 * pressure up, and that same effort pushes blood out of their wounds faster.
 */
class CardioTest {

    private static final double EPS = 1.0e-3D;
    /** A second of game time per step keeps the arithmetic in the tests readable. */
    private static final double ONE_SECOND = 1.0D;

    private PhysiologyParams params;

    @BeforeEach
    void setUp() {
        params = Fixtures.params();
    }

    /** Run the rate forward until it settles, so a test can assert where it ends up rather than a step. */
    private float settle(float from, double bloodRatio, float pain) {
        float hr = from;
        for (int i = 0; i < 600; i++) {
            hr = Cardio.advanceHeartRate(hr, bloodRatio, pain, 0.0F, 0.0F, ONE_SECOND, params);
        }
        return hr;
    }

    @Nested
    class AtRest {

        @Test
        void aHealthyHeartSitsAtRestingAndCountsAsFullCirculation() {
            float hr = settle((float) params.heartRateResting(), 1.0D, 0.0F);
            assertEquals(params.heartRateResting(), hr, EPS);
            assertEquals(1.0D, Cardio.cardiacOutput(1.0D, hr, params), EPS);
        }

        @Test
        void restingPressureIs120Over80() {
            double output = Cardio.cardiacOutput(1.0D, (float) params.heartRateResting(), params);
            assertEquals(120, Cardio.systolic(output));
            assertEquals(80, Cardio.diastolic(output));
        }

        @Test
        void aRateWellAboveRestingIsNotWhereAnUninjuredPatientStays() {
            assertEquals(params.heartRateResting(), settle(180.0F, 1.0D, 0.0F), EPS);
        }
    }

    @Nested
    class Compensation {

        @Test
        void aBleedingPatientGoesTachycardic() {
            float justBelow = settle((float) params.heartRateResting(),
                    params.heartRateCompensationRatio() - 0.01D, 0.0F);
            assertTrue(justBelow > params.heartRateResting(),
                    "losing blood must raise the rate, got " + justBelow);
        }

        @Test
        void theRateSettlesWhereThePressureItIsDefendingIsMet() {
            // ACE3 iterates targetHR = hr * targetBP / meanBP once a second; that loop's fixed point is the
            // rate at which mean pressure equals the target it is chasing, which is what this is solving for.
            double ratio = params.heartRateCompensationRatio() - 0.02D;
            float hr = settle((float) params.heartRateResting(), ratio, 0.0F);
            double mean = Cardio.meanArterialPressure(Cardio.cardiacOutput(ratio, hr, params));
            // The pressure being defended falls with volume: a 68%-full patient holds 68% of normal.
            assertEquals(Cardio.meanArterialPressure(1.0D) * ratio, mean, 1.0D);
        }

        @Test
        void theWorseTheBleedTheHarderTheHeartWorks() {
            float mild = settle((float) params.heartRateResting(), 0.72D, 0.0F);
            float bad = settle((float) params.heartRateResting(), 0.66D, 0.0F);
            assertTrue(bad > mild, mild + " -> " + bad);
        }

        @Test
        void itNeverRunsPastTheCeiling() {
            float hr = settle((float) params.heartRateResting(),
                    params.heartRateDecompensationRatio() + 0.001D, 0.0F);
            assertTrue(hr <= params.heartRateMax(), "rate ran away to " + hr);
        }

        @Test
        void belowDecompensationTheHeartGivesOutInsteadOfChasingThePressure() {
            float hr = settle((float) params.heartRateMax(),
                    params.heartRateDecompensationRatio() - 0.01D, 0.0F);
            assertTrue(hr < 1.0F, "a decompensating heart must fall away, got " + hr);
        }
    }

    @Nested
    class PainAndDrugs {

        @Test
        void painAloneRaisesTheRate() {
            float calm = settle((float) params.heartRateResting(), 1.0D, 0.0F);
            float hurting = settle((float) params.heartRateResting(), 1.0D, 0.8F);
            assertEquals(params.heartRateResting() + params.heartRatePainGain() * 0.8F, hurting, EPS);
            assertTrue(hurting > calm);
        }

        @Test
        void aTwingeIsBelowTheThresholdAndChangesNothing() {
            float hr = settle((float) params.heartRateResting(), 1.0D,
                    params.heartRatePainThreshold() - 0.01F);
            assertEquals(params.heartRateResting(), hr, EPS);
        }

        @Test
        void aStimulantPushesItUpAndAnOpioidHoldsItDown() {
            float stimulated = Cardio.targetHeartRate(1.0D, 0.0F, 1.0F, 0.0F, params);
            float sedated = Cardio.targetHeartRate(1.0D, 0.0F, 0.0F, 1.0F, params);
            assertEquals(params.heartRateResting() + params.heartRateStimulantBonus(), stimulated, EPS);
            assertEquals(params.heartRateResting() - params.heartRateOpioidDrop(), sedated, EPS);
        }

        @Test
        void tooMuchOpioidReadsAsABradycardia() {
            float sedated = settle((float) params.heartRateResting(), 1.0D, 0.0F);
            sedated = Cardio.advanceHeartRate(sedated, 1.0D, 0.0F, 0.0F, 1.0F, 60.0D, params);
            assertTrue(sedated < params.heartRateResting() * 0.70D,
                    "a fully suppressed patient should read as slow, got " + sedated);
        }
    }

    @Nested
    class Coupling {

        @Test
        void aRacingHeartMovesMoreBloodThanACalmOneAtTheSameVolume() {
            double calm = Cardio.cardiacOutput(0.8D, (float) params.heartRateResting(), params);
            double racing = Cardio.cardiacOutput(0.8D, (float) params.heartRateResting() * 2.0F, params);
            assertEquals(calm * 2.0D, racing, EPS, "circulation itself is linear in bpm, as in ACE3");
        }

        @Test
        void theBleedInfluenceDilutesWhatThatCostsTheCasualtyWithoutMovingThePressure() {
            float racing = (float) params.heartRateResting() * 2.0F;
            double output = Cardio.cardiacOutput(0.8D, racing, params);
            double bleed = Cardio.bleedScale(0.8D, racing, params);
            double calm = Cardio.cardiacOutput(0.8D, (float) params.heartRateResting(), params);

            assertTrue(bleed > calm && bleed < output,
                    "a diluted influence must still bite, but less than circulation does: " + bleed);
            assertEquals(calm * (1.0D + params.heartRateBleedInfluence()), bleed, EPS);
        }

        @Test
        void venousReturnStillCollapsesNoMatterHowFastTheHeartBeats() {
            double drained = Cardio.cardiacOutput(0.0D, (float) params.heartRateMax(), params);
            assertEquals(params.cardiacOutputFloor(), drained, 1.0e-6D,
                    "an empty ventricle pumps nothing however often it tries");
        }

        @Test
        void turningTheRateOffPinsItAtRestingAndLeavesCirculationAsItWas() {
            PhysiologyParams off = Fixtures.paramsWithHeartRate(params, false);
            assertEquals(off.heartRateResting(),
                    Cardio.advanceHeartRate(200.0F, 0.6D, 1.0F, 0.0F, 0.0F, ONE_SECOND, off), EPS);
            assertEquals(Cardio.venousReturn(0.8D, off), Cardio.cardiacOutput(0.8D, 200.0F, off), EPS);
            assertEquals(Cardio.venousReturn(0.8D, off), Cardio.bleedScale(0.8D, 200.0F, off), EPS);
        }
    }

    @Nested
    class Approach {

        @Test
        void theRateRampsRatherThanSnapping() {
            float oneStep = Cardio.advanceHeartRate((float) params.heartRateResting(), 1.0D, 1.0F, 0.0F, 0.0F,
                    ONE_SECOND, params);
            float target = Cardio.targetHeartRate(1.0D, 1.0F, 0.0F, 0.0F, params);
            assertTrue(oneStep > params.heartRateResting() && oneStep < target,
                    "one second should close part of the gap, got " + oneStep + " of " + target);
        }

        @Test
        void aLongIntervalCannotCarryItPastWhatItWasAimingFor() {
            float target = Cardio.targetHeartRate(1.0D, 1.0F, 0.0F, 0.0F, params);
            float jumped = Cardio.advanceHeartRate((float) params.heartRateResting(), 1.0D, 1.0F, 0.0F, 0.0F,
                    600.0D, params);
            assertEquals(target, jumped, EPS);
        }

        @Test
        void andItCannotUndershootOnTheWayBackDown() {
            float dropped = Cardio.advanceHeartRate(200.0F, 1.0D, 0.0F, 0.0F, 0.0F, 600.0D, params);
            assertEquals(params.heartRateResting(), dropped, EPS);
        }
    }
}
