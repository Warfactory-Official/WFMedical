package com.warfactory.medical.core;

import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.server.MedicalActionService;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import com.warfactory.medical.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Physiology#compute} is the single function that turns a bag of wounds into everything the rest of
 * the mod reacts to -- max health, bleed rate, movement, and the HEALTHY/CRITICAL/UNCONSCIOUS/DEAD state that
 * decides whether a player is downed. It is pure, so all of it is reachable here.
 */
class PhysiologyTest {

    private static final float EPS = 1.0e-4F;

    private TraumaRegistry registry;
    private MedicalProfile profile;
    private PhysiologyParams params;

    /**
     * A pain-only trauma: one point of raw limb pain per point of severity, no bleeding, no health cost, no
     * severity ceiling. Real wound types cap at severity 1.0 and drag health down with them, so they cannot
     * express "this limb hurts exactly this much" -- which is what the saturation curve, the analgesia
     * subtraction and the pain-shock threshold all need to be pinned against.
     */
    private static final TraumaType PAIN_ONLY = TraumaType.builder("test_pain_only", TraumaCategory.BRUISE)
            .major(false)
            .painPerSeverity(1.0F)
            .bleedingPerSeverity(0.0F)
            .healthReductionPerSeverity(0.0F)
            .maxSeverity(10000.0F)
            .build();

    @BeforeEach
    void setUp() {
        registry = Fixtures.registry();
        registry.register(PAIN_ONLY);
        profile = Fixtures.profile();
        params = Fixtures.params();
    }

    private DerivedStats compute() {
        return profile.recompute(params);
    }

    /** Set a limb's raw (pre-saturation) pain to exactly {@code pain}, with no other physiological cost. */
    private void setLimbPain(LimbType lt, float pain) {
        Limb limb = profile.limb(lt);
        limb.getTraumas().removeIf(t -> t.getType() == PAIN_ONLY);
        Fixtures.wound(profile, registry, lt, PAIN_ONLY.getId(), pain);
        limb.rebuildCache();
    }

    @Nested
    class Baseline {

        @Test
        void anUninjuredProfileIsHealthyAndUnimpaired() {
            DerivedStats s = compute();
            assertEquals(HealthState.HEALTHY, s.state());
            assertEquals(params.maxHealthPoints(), s.effectiveMaxHealth(), EPS);
            assertEquals(params.maxHealthPoints(), s.effectiveCurrentHealth(), EPS);
            assertEquals(0.0D, s.totalBleeding(), EPS);
            assertEquals(0.0F, s.totalPain(), EPS);
            assertEquals(1.0F, s.movementMultiplier(), EPS);
            assertEquals(1.0F, s.jumpMultiplier(), EPS);
            assertFalse(s.sprintBlocked());
            assertFalse(s.unconscious());
        }

        @Test
        void recomputeCachesAndSyncsTheProfileState() {
            DerivedStats s = compute();
            assertSame(s, profile.cached());
            assertEquals(s.state(), profile.getState());
            assertFalse(profile.isDirty(), "recompute should clear the dirty flag");
        }
    }

    @Nested
    class Bleeding {

        @Test
        void bleedingIsTheSumOverLimbsScaledByTheGlobalRate() {
            Fixtures.wound(profile, registry, LimbType.TORSO, "laceration_large", 1.0F);
            Fixtures.wound(profile, registry, LimbType.LEFT_ARM, "puncture", 1.0F);
            // laceration_large 1.2/severity, puncture 0.9/severity.
            double expected = (1.2D + 0.9D) * params.bleedingRateMultiplier();
            assertEquals(expected, compute().totalBleeding(), 1.0e-3D);
        }

        @Test
        void aTourniquetScalesThatLimbsBleedingButNotTheOthers() {
            Fixtures.wound(profile, registry, LimbType.LEFT_LEG, "laceration_large", 1.0F);
            Fixtures.wound(profile, registry, LimbType.RIGHT_LEG, "laceration_large", 1.0F);
            double both = compute().totalBleeding();

            profile.limb(LimbType.LEFT_LEG).setTourniquet(true);
            double withTq = compute().totalBleeding();

            double perLeg = both / 2.0D;
            assertEquals(perLeg * (1.0D + params.tourniquetBleedMultiplier()), withTq, 1.0e-3D);
            assertTrue(withTq < both, "a tourniquet must reduce total bleeding");
        }

        @Test
        void aStoppedBleedFactorRemovesTheLimbFromTheTotal() {
            var t = Fixtures.wound(profile, registry, LimbType.TORSO, "laceration_large", 1.0F);
            assertTrue(compute().totalBleeding() > 0.0D);
            t.setBleedFactor(0.0F);
            profile.limb(LimbType.TORSO).markDirty();
            assertEquals(0.0D, compute().totalBleeding(), EPS);
        }
    }

    @Nested
    class CardiacOutput {

        /** Full volume must leave the raw rate alone, or every existing wound number silently changes. */
        @Test
        void aFullPatientBleedsAtTheRawRate() {
            Fixtures.wound(profile, registry, LimbType.TORSO, "laceration_large", 1.0F);
            assertEquals(1.2D * params.bleedingRateMultiplier(), compute().totalBleeding(), 1.0e-3D);
            assertEquals(1.0D, compute().cardiacOutput(), 1.0e-3D);
        }

        @Test
        void losingBloodSlowsTheBleedBecauseThereIsLessToPump() {
            Fixtures.wound(profile, registry, LimbType.TORSO, "laceration_large", 1.0F);
            double full = compute().totalBleeding();

            profile.setBloodMl(params.maxBloodMl() * 0.75D);
            double halfWay = compute().totalBleeding();

            assertTrue(halfWay < full, full + " -> " + halfWay);
            // Venous return is linear from the floor (0.5) to full, so 0.75 volume is half output.
            assertEquals(full * 0.5D, halfWay, 1.0e-3D);
        }

        @Test
        void anEmptyPatientStillSeepsAtTheFloorRatherThanStoppingDead() {
            Fixtures.wound(profile, registry, LimbType.TORSO, "laceration_large", 1.0F);
            double full = compute().totalBleeding();
            profile.setBloodMl(0.0D);

            DerivedStats drained = compute();
            assertEquals(params.cardiacOutputFloor(), drained.cardiacOutput(), 1.0e-6D);
            assertEquals(full * params.cardiacOutputFloor(), drained.totalBleeding(), 1.0e-3D);
            assertTrue(drained.totalBleeding() > 0.0D,
                    "a casualty must not stabilise themselves by bleeding out");
        }

        @Test
        void theDecelerationCanBeTurnedOff() {
            Fixtures.wound(profile, registry, LimbType.TORSO, "laceration_large", 1.0F);
            PhysiologyParams flat = Fixtures.paramsWith(params, false);
            profile.setBloodMl(params.maxBloodMl() * 0.7D);
            compute(); // build the limb caches the way the engine would before comparing params
            assertTrue(compute().totalBleeding() < 1.2D * params.bleedingRateMultiplier(),
                    "the enabled model must be decelerating here for the comparison to mean anything");

            assertEquals(1.2D * params.bleedingRateMultiplier(),
                    Physiology.compute(profile, flat).totalBleeding(), 1.0e-3D);
        }
    }

    @Nested
    class HeartRateCoupling {

        @Test
        void aRestingHeartLeavesEveryExistingNumberWhereItWas() {
            Fixtures.wound(profile, registry, LimbType.TORSO, "laceration_large", 1.0F);
            profile.setHeartRate((float) params.heartRateResting());
            assertEquals(1.2D * params.bleedingRateMultiplier(), compute().totalBleeding(), 1.0e-3D);
            assertEquals(1.0D, compute().cardiacOutput(), 1.0e-3D);
        }

        @Test
        void aRacingHeartPushesBloodOutOfTheWoundFaster() {
            Fixtures.wound(profile, registry, LimbType.TORSO, "laceration_large", 1.0F);
            double calm = compute().totalBleeding();

            profile.setHeartRate((float) params.heartRateResting() * 2.0F);
            double racing = compute().totalBleeding();

            assertTrue(racing > calm, calm + " -> " + racing);
        }

        @Test
        void theRateIsCarriedThroughToTheClientAlongWithThePressureItProduces() {
            profile.setHeartRate(150.0F);
            DerivedStats stats = compute();
            assertEquals(150.0F, stats.heartRate());
            assertTrue(stats.systolic() > 120, "a racing heart at full volume reads high: " + stats.systolic());

            profile.setBloodMl(params.maxBloodMl() * 0.6D);
            assertTrue(compute().systolic() < 120, "and a drained one reads low");
        }

        @Test
        void turningTheRateOffIgnoresWhateverTheProfileIsCarrying() {
            Fixtures.wound(profile, registry, LimbType.TORSO, "laceration_large", 1.0F);
            profile.setHeartRate(200.0F);
            PhysiologyParams off = Fixtures.paramsWithHeartRate(params, false);
            compute(); // build the limb caches the way the engine would before comparing params

            DerivedStats flat = Physiology.compute(profile, off);
            assertEquals(params.heartRateResting(), flat.heartRate(), EPS);
            assertEquals(1.2D * params.bleedingRateMultiplier(), flat.totalBleeding(), 1.0e-3D);
        }
    }

    @Nested
    class Resuscitation {

        /** The odds must reward the medic for stopping the bleed and replacing volume, in that order. */
        @Test
        void theOddsFallAsThePatientEmpties() {
            profile.setBloodMl(params.maxBloodMl() * (1.0D - params.bloodUnconsciousLossFraction()));
            compute();
            float justDown = MedicalActionService.resuscitateChance(profile);

            profile.setBloodMl(params.maxBloodMl() * (1.0D - params.bloodDeathLossFraction()));
            compute();
            float nearlyGone = MedicalActionService.resuscitateChance(profile);

            assertTrue(justDown > nearlyGone, justDown + " -> " + nearlyGone);
            assertTrue(nearlyGone > 0.0F, "a long shot is still a shot");
        }

        @Test
        void aHaemorrhagingPatientCannotBeBroughtRoundAtAll() {
            profile.setBloodMl(params.maxBloodMl() * (1.0D - params.bloodUnconsciousLossFraction()));
            compute();
            assertTrue(MedicalActionService.resuscitateChance(profile) > 0.0F);

            float dry = MedicalActionService.resuscitateChance(profile);

            // An open haemorrhage cuts the odds: stop the bleed before working the chest.
            Fixtures.wound(profile, registry, LimbType.TORSO, "internal_bleeding", 1.0F);
            compute();
            float bleeding = MedicalActionService.resuscitateChance(profile);
            assertTrue(bleeding < dry, dry + " -> " + bleeding);

            // Past the reference rate it is hopeless outright.
            for (LimbType lt : LimbType.VALUES) {
                Fixtures.wound(profile, registry, lt, "internal_bleeding", 1.0F);
            }
            compute();
            assertEquals(0.0F, MedicalActionService.resuscitateChance(profile), EPS);
        }
    }

    @Nested
    class ReviveGrace {

        @Test
        void aRevivedCasualtyStaysUpThroughTheGraceDespiteTheNumbersSayingOtherwise() {
            profile.setBloodMl(params.maxBloodMl() * (1.0D - params.bloodUnconsciousLossFraction() - 0.02D));
            assertEquals(HealthState.UNCONSCIOUS, compute().state());

            profile.setReviveGraceUntilTick(1000L);
            assertTrue(compute().state() != HealthState.UNCONSCIOUS,
                    "without the grace a revive folds again on the very next recompute");
        }

        @Test
        void theGraceDoesNotHoldOffBleedingOut() {
            profile.setReviveGraceUntilTick(1000L);
            profile.setBloodMl(params.maxBloodMl() * (1.0D - params.bloodDeathLossFraction() - 0.01D));
            assertEquals(HealthState.DEAD, compute().state(), "CPR must not make anyone unkillable");
        }
    }

    @Nested
    class HealthPool {

        @Test
        void aMajorWoundLowersMaxHealthByItsHealthReduction() {
            Fixtures.wound(profile, registry, LimbType.TORSO, "laceration_large", 0.5F);
            DerivedStats s = compute();
            // laceration_large: 4.0 health reduction per severity.
            assertEquals(2.0F, s.healthModifier(), EPS);
            assertEquals(params.maxHealthPoints() - 2.0F, s.effectiveMaxHealth(), EPS);
        }

        @Test
        void aLimbCannotDrainMoreThanItsConfiguredShare() {
            // internal_bleeding is 5.0/severity on a max-1.0 severity, so a fully-severe arm asks for 5.0
            // points; the arm's share is 0.12 * 30 = 3.6. The cap is what stops one limb ending a player.
            Fixtures.wound(profile, registry, LimbType.LEFT_ARM, "internal_bleeding", 1.0F);
            float cap = params.healthShareArm() * params.maxHealthPoints();
            assertEquals(cap, compute().healthModifier(), EPS);
        }

        @Test
        void aMinorWoundCostsCurrentHealthButNotMaxHealth() {
            // blunt_force_trauma is minor with 12.0 health reduction per severity: current only.
            Fixtures.wound(profile, registry, LimbType.TORSO, "blunt_force_trauma", 0.5F);
            DerivedStats s = compute();
            assertEquals(0.0F, s.healthModifier(), EPS, "a minor wound must not lower max health");
            assertEquals(params.maxHealthPoints(), s.effectiveMaxHealth(), EPS);
            assertEquals(params.maxHealthPoints() - 6.0F, s.effectiveCurrentHealth(), EPS);
        }

        @Test
        void healthNeverGoesNegative() {
            for (LimbType lt : LimbType.VALUES) {
                Fixtures.wound(profile, registry, lt, "internal_bleeding", 1.0F);
                Fixtures.wound(profile, registry, lt, "laceration_large", 1.0F);
            }
            DerivedStats s = compute();
            assertTrue(s.effectiveMaxHealth() >= 0.0F, "effectiveMaxHealth went negative");
            assertTrue(s.effectiveCurrentHealth() >= 0.0F, "effectiveCurrentHealth went negative");
        }
    }

    @Nested
    class Pain {

        @Test
        void perceivedPainSaturatesRatherThanSumming() {
            // local = raw / (raw + k), k = 1.0. Two wounds of raw 1.0 on one limb sum to raw 2.0 -> 0.667,
            // not 1.333 -- the point of the saturation curve.
            setLimbPain(LimbType.TORSO, 2.0F);
            assertEquals(2.0F / 3.0F, compute().totalPain(), 1.0e-3F);
        }

        @Test
        void perceivedPainIsTheWorstLimbNotTheSum() {
            setLimbPain(LimbType.TORSO, 1.0F);
            setLimbPain(LimbType.LEFT_ARM, 1.0F);
            // Both limbs sit at 0.5 local; the max is 0.5, not 1.0.
            assertEquals(0.5F, compute().totalPain(), 1.0e-3F);
        }

        @Test
        void analgesiaSubtractsFromEveryLimbAndCanZeroPainOut() {
            setLimbPain(LimbType.TORSO, 1.0F);
            assertEquals(0.5F, compute().totalPain(), 1.0e-3F);
            profile.setPainSuppression(0.5F);
            assertEquals(0.0F, compute().totalPain(), EPS);
        }

        @Test
        void aStimulantActsAsAnalgesiaWhenItIsTheStronger() {
            setLimbPain(LimbType.TORSO, 1.0F);
            profile.setPainSuppression(0.1F);
            profile.setStimulant(0.5F);
            assertEquals(0.0F, compute().totalPain(), EPS, "the stronger of the two should apply");
        }

        @Test
        void localNumbingOnlyAffectsTheLimbItIsOn() {
            setLimbPain(LimbType.LEFT_ARM, 1.0F);
            setLimbPain(LimbType.RIGHT_ARM, 1.0F);
            profile.limb(LimbType.LEFT_ARM).setLocalNumbing(1.0F);
            // The numbed arm drops out; the other still reports 0.5.
            assertEquals(0.5F, compute().totalPain(), 1.0e-3F);

            profile.limb(LimbType.RIGHT_ARM).setLocalNumbing(1.0F);
            assertEquals(0.0F, compute().totalPain(), EPS);
        }

        @Test
        void systemicPainWeightsTorsoAboveArms() {
            setLimbPain(LimbType.TORSO, 1.0F);
            float torso = compute().systemicPain();

            profile = Fixtures.profile();
            setLimbPain(LimbType.LEFT_ARM, 1.0F);
            float arm = compute().systemicPain();

            assertTrue(torso > arm, "torso pain must weigh more systemically than an arm's");
            assertEquals(params.painShareTorso() * 0.5F, torso, 1.0e-3F);
            assertEquals(params.painShareArm() * 0.5F, arm, 1.0e-3F);
        }

        @Test
        void systemicPainIsCappedAtOne() {
            for (LimbType lt : LimbType.VALUES) {
                setLimbPain(lt, 1000.0F);
            }
            assertTrue(compute().systemicPain() <= 1.0F);
        }

        @Test
        void painAboveTheShockThresholdEatsMaxHealth() {
            for (LimbType lt : LimbType.VALUES) {
                setLimbPain(lt, 100.0F);
            }
            DerivedStats s = compute();
            assertTrue(s.systemicPain() > params.painShockThreshold());
            assertTrue(s.healthModifier() > 0.0F, "pain shock should remove health");
        }
    }

    @Nested
    class BloodLoss {

        @Test
        void bloodAboveTheLowFractionCostsNothing() {
            profile.setBloodMl(params.maxBloodMl() * 0.9D);
            DerivedStats s = compute();
            assertEquals(0.0F, s.healthModifier(), EPS);
            assertEquals(1.0F, s.movementMultiplier(), EPS);
        }

        @Test
        void bloodBelowTheLowFractionRampsAHealthPenalty() {
            profile.setBloodMl(params.maxBloodMl() * 0.5D);
            float half = compute().healthModifier();
            profile.setBloodMl(params.maxBloodMl() * 0.2D);
            float deeper = compute().healthModifier();
            assertTrue(half > 0.0F, "penalty should have begun below bloodLowFraction");
            assertTrue(deeper > half, "the penalty must grow as blood falls");
        }

        @Test
        void losingPastTheDeathFractionIsDeadRegardlessOfBleedout() {
            profile.setBloodMl(params.maxBloodMl() * (1.0D - params.bloodDeathLossFraction()) - 1.0D);
            assertEquals(HealthState.DEAD, compute().state(),
                    "blood-out death is unconditional; it is not downgraded to unconscious");
        }

        @Test
        void movementIsPenalisedOnlyPastTheMovementLossFraction() {
            profile.setBloodMl(params.maxBloodMl() * (1.0D - params.bloodMovementPenaltyLossFraction() * 0.5D));
            assertEquals(1.0F, compute().movementMultiplier(), EPS);

            profile.setBloodMl(params.maxBloodMl() * (1.0D - params.bloodMovementPenaltyLossFraction() - 0.05D));
            DerivedStats s = compute();
            assertTrue(s.movementMultiplier() < 1.0F);
            assertTrue(s.sprintBlocked(), "severe blood loss blocks sprinting");
        }
    }

    @Nested
    class States {

        @Test
        void deepBloodLossDownsRatherThanKillsWhenBleedoutIsOn() {
            // Past the unconscious fraction but short of the death fraction.
            profile.setBloodMl(params.maxBloodMl() * (1.0D - params.bloodUnconsciousLossFraction() - 0.02D));
            assertEquals(HealthState.UNCONSCIOUS, compute().state());
        }

        @Test
        void withBleedoutDisabledTheSameLossIsFatal() {
            PhysiologyParams noBleedout = Fixtures.paramsWithBleedout(params, false);
            profile.setBloodMl(params.maxBloodMl() * (1.0D - params.bloodUnconsciousLossFraction() - 0.02D));
            assertEquals(HealthState.DEAD, profile.recompute(noBleedout).state());
        }

        @Test
        void anOverdoseForcesUnconsciousOverAHealthyBody() {
            assertEquals(HealthState.HEALTHY, compute().state());
            profile.setOverdoseUnconscious(true);
            assertEquals(HealthState.UNCONSCIOUS, compute().state());
            assertTrue(profile.isDowned());
        }

        @Test
        void asphyxiaAndTheUnconsciousLatchAlsoForceItDown() {
            profile.setAsphyxiaUnconscious(true);
            assertEquals(HealthState.UNCONSCIOUS, compute().state());

            profile = Fixtures.profile();
            profile.setUnconsciousLatched(true);
            assertEquals(HealthState.UNCONSCIOUS, compute().state());
        }

        @Test
        void aForcedStateOnlyEverEscalates() {
            profile.setForcedState(HealthState.DEAD);
            assertEquals(HealthState.DEAD, compute().state());

            // A forced HEALTHY must not resurrect a genuinely dead body.
            profile.setBloodMl(0.0D);
            profile.setForcedState(HealthState.HEALTHY);
            assertEquals(HealthState.DEAD, compute().state(),
                    "forcedState must not downgrade a worse computed state");
        }

        @Test
        void anUnconsciousBodyCannotMoveOrJump() {
            profile.setOverdoseUnconscious(true);
            DerivedStats s = compute();
            assertEquals(0.0F, s.movementMultiplier(), EPS);
            assertEquals(0.0F, s.jumpMultiplier(), EPS);
            assertTrue(s.sprintBlocked());
        }
    }

    @Nested
    class Adrenaline {

        @Test
        void painAloneCannotKnockYouOutWhileAdrenalineHolds() {
            for (LimbType lt : LimbType.VALUES) {
                setLimbPain(lt, 1000.0F);
            }
            DerivedStats s = compute();
            assertNotEquals(HealthState.UNCONSCIOUS, s.state(),
                    "with adrenaline available, pain must not down the player yet");
            assertTrue(s.painKoPending(), "the pending flag is what the engine's grace timer watches");
        }

        @Test
        void onceAdrenalineIsExhaustedTheSamePainDownsYou() {
            for (LimbType lt : LimbType.VALUES) {
                setLimbPain(lt, 1000.0F);
            }
            profile.setAdrenalineExhausted(true);
            assertEquals(HealthState.UNCONSCIOUS, compute().state());
        }

        @Test
        void bloodDrivenKnockoutIsNeverHeldOffByAdrenaline() {
            profile.setBloodMl(params.maxBloodMl() * (1.0D - params.bloodUnconsciousLossFraction() - 0.02D));
            assertEquals(HealthState.UNCONSCIOUS, compute().state());
            assertFalse(compute().painKoPending(), "a blood KO is not pending, it has happened");
        }
    }

    @Nested
    class Movement {

        @Test
        void aLegFractureSlowsYouAndBlocksSprinting() {
            Fixtures.wound(profile, registry, LimbType.LEFT_LEG, "fracture", 1.0F);
            DerivedStats s = compute();
            assertTrue(s.anyLegFracture());
            assertTrue(s.sprintBlocked());
            assertEquals(0.0F, s.jumpMultiplier(), EPS, "a broken leg cannot jump");
            assertEquals(params.legFractureSpeedMultiplier(), s.movementMultiplier(), EPS);
        }

        @Test
        void twoFracturedLegsCompoundTheSlowDown() {
            Fixtures.wound(profile, registry, LimbType.LEFT_LEG, "fracture", 1.0F);
            Fixtures.wound(profile, registry, LimbType.RIGHT_LEG, "fracture", 1.0F);
            float m = params.legFractureSpeedMultiplier();
            assertEquals(Math.max(m * m, params.painSpeedFloor()), compute().movementMultiplier(), EPS);
        }

        @Test
        void aStabilizedFractureNoLongerCountsAsAFracture() {
            var t = Fixtures.wound(profile, registry, LimbType.LEFT_LEG, "fracture", 1.0F);
            assertTrue(compute().anyLegFracture());
            t.setStabilized(true);
            profile.limb(LimbType.LEFT_LEG).markDirty();
            DerivedStats s = compute();
            assertFalse(s.anyLegFracture(), "a splinted leg should walk again");
            assertFalse(s.sprintBlocked());
        }

        @Test
        void anArmFractureIsReportedSeparatelyAndDoesNotSlowYou() {
            Fixtures.wound(profile, registry, LimbType.RIGHT_ARM, "fracture", 1.0F);
            DerivedStats s = compute();
            assertTrue(s.anyArmFracture());
            assertFalse(s.anyLegFracture());
            assertEquals(1.0F, s.movementMultiplier(), EPS);
            assertFalse(s.sprintBlocked());
        }

        @Test
        void movementNeverFallsBelowTheSpeedFloorWhileConscious() {
            for (LimbType lt : LimbType.VALUES) {
                Fixtures.wound(profile, registry, lt, "crush_injury", 1.0F);
            }
            profile.limb(LimbType.LEFT_LEG).setTourniquet(true);
            profile.limb(LimbType.RIGHT_LEG).setTourniquet(true);
            DerivedStats s = compute();
            if (!s.unconscious()) {
                assertTrue(s.movementMultiplier() >= params.painSpeedFloor() - EPS,
                        "movement " + s.movementMultiplier() + " dropped below the floor");
            }
        }

        @Test
        void aStimulantCanPushSpeedAboveNormalAndRestoreJumping() {
            Fixtures.wound(profile, registry, LimbType.LEFT_LEG, "crush_injury", 1.0F);
            float hurt = compute().movementMultiplier();
            profile.setStimulant(1.0F);
            DerivedStats s = compute();
            assertTrue(s.movementMultiplier() > hurt);
            assertEquals(1.0F + params.stimulantSpeedBonus(), s.movementMultiplier(), EPS);
            assertEquals(1.0F, s.jumpMultiplier(), EPS);
        }

        @Test
        void tourniquetsCostSpeedSoTheyAreNotFreeToLeaveOn() {
            float clean = compute().movementMultiplier();
            profile.limb(LimbType.LEFT_LEG).setTourniquet(true);
            profile.limb(LimbType.LEFT_ARM).setTourniquet(true);
            DerivedStats s = compute();
            assertTrue(s.movementMultiplier() < clean);
            assertTrue(s.anyArmTourniquet());
            assertEquals(params.tourniquetLegSpeedMultiplier() * params.tourniquetArmSpeedMultiplier(),
                    s.movementMultiplier(), EPS);
        }
    }

    @Nested
    class LimbDepletion {

        /** Drain a limb past its health share so it reads as destroyed. */
        private void destroy(LimbType lt) {
            // internal_bleeding at max severity asks 5.0 points; every limb's share is below that except
            // the torso (0.55*30=16.5) and head (0.35*30=10.5), so stack enough to clear the cap.
            for (int i = 0; i < 6; i++) {
                Fixtures.wound(profile, registry, lt, "internal_bleeding", 1.0F);
            }
        }

        @Test
        void twoDestroyedArmsAreReportedAsBothArmsDisabled() {
            destroy(LimbType.LEFT_ARM);
            destroy(LimbType.RIGHT_ARM);
            assertTrue(compute().bothArmsDisabled());
        }

        @Test
        void twoDestroyedLegsPinMovementAtTheFloorAndStopJumps() {
            destroy(LimbType.LEFT_LEG);
            destroy(LimbType.RIGHT_LEG);
            DerivedStats s = compute();
            assertTrue(s.bothLegsDisabled());
            assertTrue(s.sprintBlocked());
            assertEquals(0.0F, s.jumpMultiplier(), EPS);
            if (!s.unconscious()) {
                assertEquals(params.painSpeedFloor(), s.movementMultiplier(), EPS);
            }
        }

        @Test
        void aDestroyedTorsoDownsYouButOnlyKillsWhenInstakillIsOn() {
            destroy(LimbType.TORSO);
            assertEquals(HealthState.UNCONSCIOUS, compute().state());

            PhysiologyParams instakill = withTorsoInstakill(true);
            assertEquals(HealthState.DEAD, profile.recompute(instakill).state());
        }

        private PhysiologyParams withTorsoInstakill(boolean on) {
            return Fixtures.paramsWithTorsoInstakill(params, on);
        }
    }

    @Nested
    class Asphyxia {

        @Test
        void aConsciousAsphyxiatingPlayerIsSlowedAndCannotSprintOrJump() {
            profile.setAsphyxiating(true);
            DerivedStats s = compute();
            assertTrue(s.asphyxiating());
            assertEquals(params.asphyxiaMoveMultiplier(), s.movementMultiplier(), EPS);
            assertTrue(s.sprintBlocked());
            assertEquals(0.0F, s.jumpMultiplier(), EPS);
        }

        @Test
        void anAlreadyUnconsciousBodyIsNotAlsoReportedAsAsphyxiating() {
            profile.setAsphyxiating(true);
            profile.setOverdoseUnconscious(true);
            assertFalse(compute().asphyxiating(), "the flag drives a conscious-struggle effect only");
        }
    }
}
