package com.warfactory.medical.server;

import com.warfactory.medical.attachment.MedicalData;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.treatment.Treatment;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.support.Fixtures;
import com.warfactory.medical.support.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Treatment resolution: which wound an item picks, and what it does to it.
 *
 * <p>{@code applyTargeted(IMedicalData, long, Treatment, LimbType)} is the whole decision, and it takes the
 * attachment rather than a player, so all of it is reachable without a server. The gametest layer only has
 * to prove the item -> service plumbing, not the rules.
 */
class TreatmentServiceTest {

    private static final float EPS = 1.0e-4F;

    private TraumaRegistry registry;
    private MedicalData data;
    private MedicalProfile profile;
    private Map<String, Treatment> items;

    @BeforeAll
    static void loadConfig() {
        // BOOST_CLOTTING reads clottingAgentDurationTicks.
        TestConfig.load();
    }

    @BeforeEach
    void setUp() {
        registry = Fixtures.registry();
        TraumaRegistry.setActive(registry);
        items = Fixtures.treatments();
        data = new MedicalData();
        profile = data.getProfile();
    }

    private Treatment item(String id) {
        Treatment t = items.get(id);
        if (t == null) {
            throw new AssertionError("no shipped treatment for " + id);
        }
        return t;
    }

    private boolean apply(Treatment treatment, LimbType limb) {
        return TreatmentService.applyTargeted(data, 1000L, treatment, limb);
    }

    private Trauma wound(LimbType limb, String id, float severity) {
        Trauma t = Fixtures.wound(profile, registry, limb, id, severity);
        profile.limb(limb).rebuildCache();
        return t;
    }

    @Nested
    class Bandage {

        @Test
        void aBandageStopsALacerationsBleedWithoutTreatingIt() {
            Trauma cut = wound(LimbType.LEFT_ARM, "laceration_large", 0.8F);
            assertTrue(cut.bleeding() > 0.0F);

            assertTrue(apply(item("wfmedical:bandage"), LimbType.LEFT_ARM));

            assertEquals(0.0F, cut.getBleedFactor(), EPS);
            assertEquals(0.0F, cut.bleeding(), EPS);
            assertFalse(cut.isTreated(), "a dressing manages the bleed; the wound still needs real treatment");
            assertTrue(cut.isBleedControlledOnly());
            assertEquals(0.8F, cut.getSeverity(), EPS, "a bandage does not shrink the wound");
        }

        @Test
        void bandagingTwiceIsANoOpTheSecondTime() {
            wound(LimbType.LEFT_ARM, "laceration_large", 0.8F);
            assertTrue(apply(item("wfmedical:bandage"), LimbType.LEFT_ARM));
            assertFalse(apply(item("wfmedical:bandage"), LimbType.LEFT_ARM),
                    "reporting a change would consume a second bandage for nothing");
        }

        @Test
        void aBandageCannotTouchInternalBleeding() {
            Trauma internal = wound(LimbType.TORSO, "internal_bleeding", 0.9F);
            assertFalse(apply(item("wfmedical:bandage"), LimbType.TORSO));
            assertEquals(1.0F, internal.getBleedFactor(), EPS);
        }

        @Test
        void aHemostaticSlowsInternalBleedingWithoutStoppingIt() {
            Trauma internal = wound(LimbType.TORSO, "internal_bleeding", 0.9F);
            float before = internal.bleeding();
            assertTrue(apply(item("wfmedical:hemostatic"), LimbType.TORSO));
            assertEquals(0.3F, internal.getBleedFactor(), EPS);
            assertTrue(internal.bleeding() > 0.0F, "clotting slows an internal bleed, it does not seal it");
            assertTrue(internal.bleeding() < before);
        }

        @Test
        void aHemostaticAlsoAppliesItsGlobalClottingBuff() {
            assertEquals(0.0F, profile.getClottingBoost(), EPS);
            assertTrue(apply(item("wfmedical:hemostatic"), null));
            assertTrue(profile.getClottingBoost() > 0.0F);
            assertTrue(profile.getClottingBoostEndTick() > 1000L, "the buff must expire in the future");
        }
    }

    @Nested
    class SutureAndSplint {

        @Test
        void aSutureClosesTheWoundStopsTheBleedAndMarksItTreated() {
            Trauma cut = wound(LimbType.TORSO, "laceration_large", 0.8F);
            assertTrue(apply(item("wfmedical:suture_kit"), LimbType.TORSO));
            assertTrue(cut.isClosed());
            assertTrue(cut.isTreated(), "a closed wound mends on its own from here");
            assertEquals(0.0F, cut.getBleedFactor(), EPS);
        }

        @Test
        void aSuturedWoundIsStillThereJustNoLongerBleeding() {
            Trauma cut = wound(LimbType.TORSO, "laceration_large", 0.8F);
            apply(item("wfmedical:suture_kit"), LimbType.TORSO);
            assertEquals(1, profile.limb(LimbType.TORSO).getTraumas().size());
            assertEquals(0.8F, cut.getSeverity(), EPS);
        }

        @Test
        void aSplintStabilisesAFractureWithoutHealingIt() {
            Trauma broken = wound(LimbType.RIGHT_LEG, "fracture", 1.0F);
            assertTrue(apply(item("wfmedical:splint"), LimbType.RIGHT_LEG));
            assertTrue(broken.isStabilized());
            assertFalse(broken.isTreated(), "a splint holds the bone; it does not knit it");
            assertEquals(1.0F, broken.getSeverity(), EPS);
        }

        @Test
        void aSplintDoesNothingToASoftTissueWound() {
            wound(LimbType.RIGHT_LEG, "laceration_large", 0.8F);
            assertFalse(apply(item("wfmedical:splint"), LimbType.RIGHT_LEG));
        }

        @Test
        void aSutureKitDoesNothingToAFracture() {
            wound(LimbType.RIGHT_LEG, "fracture", 1.0F);
            assertFalse(apply(item("wfmedical:suture_kit"), LimbType.RIGHT_LEG));
        }
    }

    @Nested
    class Medkit {

        @Test
        void aMedkitRemovesTheWoundEntirely() {
            wound(LimbType.HEAD, "laceration_large", 0.8F);
            assertTrue(apply(item("wfmedical:medkit"), LimbType.HEAD));
            assertTrue(profile.limb(LimbType.HEAD).getTraumas().isEmpty());
        }

        @Test
        void aMedkitAlsoRestoresBlood() {
            profile.setBloodMl(1000.0D);
            wound(LimbType.HEAD, "laceration_large", 0.8F);
            apply(item("wfmedical:medkit"), LimbType.HEAD);
            assertEquals(1250.0D, profile.getBloodMl(), 1.0e-6D);
        }

        @Test
        void aMedkitWithNoWoundStillTopsUpBlood() {
            profile.setBloodMl(1000.0D);
            assertTrue(apply(item("wfmedical:medkit"), null),
                    "there is nothing to treat, but the transfusion still helps");
            assertEquals(1250.0D, profile.getBloodMl(), 1.0e-6D);
        }

        @Test
        void aMedkitOnAnUninjuredPlayerAtFullBloodDoesNothing() {
            assertFalse(apply(item("wfmedical:medkit"), null));
        }

        @Test
        void aBurnOintmentTreatsBurnsButNotCuts() {
            Trauma burn = wound(LimbType.TORSO, "burn", 0.5F);
            assertTrue(apply(item("wfmedical:burn_ointment"), LimbType.TORSO));
            assertTrue(burn.isTreated());

            wound(LimbType.LEFT_ARM, "laceration_large", 0.8F);
            assertFalse(apply(item("wfmedical:burn_ointment"), LimbType.LEFT_ARM));
        }

        @Test
        void anAntiradShotOnlyAnswersRadiationBurns() {
            wound(LimbType.TORSO, "burn", 0.5F);
            assertFalse(apply(item("wfmedical:antirad_shot"), LimbType.TORSO));
            wound(LimbType.TORSO, "radiation_burn", 0.5F);
            assertTrue(apply(item("wfmedical:antirad_shot"), LimbType.TORSO));
        }

        @Test
        void aHealingItemThatDoesNotDepleteTheWoundOnlyShrinksIt() {
            Trauma cut = wound(LimbType.HEAD, "laceration_large", 1.0F);
            Treatment weak = new Treatment(TreatmentAction.HEAL_TRAUMA,
                    EnumSet.of(TraumaCategory.LACERATION), 0.25F, 0.0D, 20, false);
            assertTrue(apply(weak, LimbType.HEAD));
            assertEquals(0.75F, cut.getSeverity(), EPS);
            assertEquals(1, profile.limb(LimbType.HEAD).getTraumas().size());
            assertTrue(cut.isTreated());
        }

        @Test
        void healingPastZeroRemovesTheWoundEvenWithoutRemovesTrauma() {
            wound(LimbType.HEAD, "laceration_large", 0.2F);
            Treatment weak = new Treatment(TreatmentAction.HEAL_TRAUMA,
                    EnumSet.of(TraumaCategory.LACERATION), 0.25F, 0.0D, 20, false);
            assertTrue(apply(weak, LimbType.HEAD));
            assertTrue(profile.limb(LimbType.HEAD).getTraumas().isEmpty());
        }
    }

    @Nested
    class GlobalActions {

        @Test
        void aBloodBagRefillsUpToTheMaximumAndNoFurther() {
            profile.setBloodMl(4500.0D);
            assertTrue(apply(item("wfmedical:blood_bag"), null));
            assertEquals(profile.getMaxBloodMl(), profile.getBloodMl(), 1.0e-6D);
            assertFalse(apply(item("wfmedical:blood_bag"), null), "already full");
        }

        @Test
        void painkillersRaiseSuppressionButNeverLowerIt() {
            assertTrue(apply(item("wfmedical:painkillers"), null));
            float after = profile.getPainSuppression();
            assertEquals(0.5F, after, EPS);

            profile.setPainSuppression(0.9F);
            assertFalse(apply(item("wfmedical:painkillers"), null),
                    "a weaker dose must not undo a stronger one");
            assertEquals(0.9F, profile.getPainSuppression(), EPS);
        }

        @Test
        void anAnestheticNumbsOnlyTheLimbItIsAimedAt() {
            assertTrue(apply(item("wfmedical:local_anesthetic"), LimbType.LEFT_LEG));
            assertEquals(0.9F, profile.limb(LimbType.LEFT_LEG).getLocalNumbing(), EPS);
            assertEquals(0.0F, profile.limb(LimbType.RIGHT_LEG).getLocalNumbing(), EPS);
        }

        @Test
        void anAnestheticWithNoLimbIsRejected() {
            assertFalse(apply(item("wfmedical:local_anesthetic"), null),
                    "a targeted drug with no target must not silently do nothing and be consumed");
        }
    }

    @Nested
    class TargetSelection {

        @Test
        void withNoLimbHintTheServicePicksAcrossTheWholeBody() {
            Trauma leg = wound(LimbType.RIGHT_LEG, "laceration_large", 0.8F);
            assertTrue(apply(item("wfmedical:bandage"), null));
            assertEquals(0.0F, leg.getBleedFactor(), EPS);
        }

        @Test
        void aLimbHintConfinesTheSearchToThatLimb() {
            Trauma arm = wound(LimbType.LEFT_ARM, "laceration_large", 0.8F);
            assertFalse(apply(item("wfmedical:bandage"), LimbType.RIGHT_LEG),
                    "there is nothing to bandage on the leg");
            assertEquals(1.0F, arm.getBleedFactor(), EPS);
        }

        @Test
        void aBleedingWoundIsPreferredOverAnAlreadyDressedOne() {
            Trauma dressed = wound(LimbType.TORSO, "laceration_large", 0.9F);
            dressed.setBleedFactor(0.0F);
            Trauma bleeding = wound(LimbType.TORSO, "puncture", 0.3F);

            assertTrue(apply(item("wfmedical:bandage"), LimbType.TORSO));
            assertEquals(0.0F, bleeding.getBleedFactor(), EPS,
                    "the smaller but still-bleeding wound is the one that needed the bandage");
        }

        @Test
        void amongEquallyUrgentWoundsTheWorstIsTreatedFirst() {
            Trauma small = wound(LimbType.TORSO, "puncture", 0.2F);
            Trauma big = wound(LimbType.TORSO, "puncture", 0.9F);
            assertTrue(apply(item("wfmedical:bandage"), LimbType.TORSO));
            assertEquals(0.0F, big.getBleedFactor(), EPS);
            assertEquals(1.0F, small.getBleedFactor(), EPS);
        }

        @Test
        void anUnstabilisedFractureIsPreferredOverASplintedOne() {
            Trauma splinted = wound(LimbType.RIGHT_LEG, "fracture", 1.0F);
            splinted.setStabilized(true);
            Trauma fresh = wound(LimbType.RIGHT_LEG, "fracture", 0.4F);
            assertTrue(apply(item("wfmedical:splint"), LimbType.RIGHT_LEG));
            assertTrue(fresh.isStabilized());
        }
    }

    @Nested
    class Bookkeeping {

        @Test
        void aSuccessfulTreatmentBumpsTheRevisionSoTheClientResyncs() {
            wound(LimbType.TORSO, "laceration_large", 0.8F);
            data.markSynced();
            assertFalse(data.needsSync());
            assertTrue(apply(item("wfmedical:bandage"), LimbType.TORSO));
            assertTrue(data.needsSync());
        }

        @Test
        void aFailedTreatmentDoesNotBumpTheRevision() {
            data.markSynced();
            assertFalse(apply(item("wfmedical:bandage"), LimbType.TORSO));
            assertFalse(data.needsSync(), "a no-op must not cost a snapshot");
        }

        @Test
        void aSuccessfulTreatmentDirtiesTheLimbSoTheCacheIsRebuilt() {
            wound(LimbType.TORSO, "laceration_large", 0.8F);
            Limb limb = profile.limb(LimbType.TORSO);
            limb.rebuildCache();
            assertFalse(limb.isDirty());
            apply(item("wfmedical:bandage"), LimbType.TORSO);
            assertTrue(limb.isDirty(), "without this the bleed would keep ticking off a stale cache");
        }

        @Test
        void nullArgumentsAreRejectedRatherThanThrowing() {
            assertFalse(TreatmentService.applyTargeted(null, 0L, item("wfmedical:bandage"), null));
            assertFalse(TreatmentService.applyTargeted(data, 0L, null, null));
        }
    }

    @Nested
    class Applicability {

        @Test
        void aGlobalActionIsOfferedOnEveryLimb() {
            for (LimbType lt : LimbType.VALUES) {
                assertTrue(TreatmentService.canTreatLimb(profile, item("wfmedical:blood_bag"), lt), "" + lt);
            }
            assertEquals(0b111111, TreatmentService.treatableMask(profile, item("wfmedical:blood_bag")));
        }

        @Test
        void aTourniquetIsOfferedOnlyOnBareArmsAndLegs() {
            Treatment tq = item("wfmedical:tourniquet");
            assertFalse(TreatmentService.canTreatLimb(profile, tq, LimbType.HEAD));
            assertFalse(TreatmentService.canTreatLimb(profile, tq, LimbType.TORSO));
            assertTrue(TreatmentService.canTreatLimb(profile, tq, LimbType.LEFT_ARM));
            assertTrue(TreatmentService.canTreatLimb(profile, tq, LimbType.RIGHT_LEG));

            profile.limb(LimbType.LEFT_ARM).setTourniquet(true);
            assertFalse(TreatmentService.canTreatLimb(profile, tq, LimbType.LEFT_ARM),
                    "a limb already wearing one is not a target");
        }

        @Test
        void anAnestheticIsOfferedOnlyOnALimbThatHurts() {
            Treatment num = item("wfmedical:local_anesthetic");
            assertFalse(TreatmentService.canTreatLimb(profile, num, LimbType.LEFT_LEG));
            wound(LimbType.LEFT_LEG, "laceration_large", 0.5F);
            assertTrue(TreatmentService.canTreatLimb(profile, num, LimbType.LEFT_LEG));
        }

        @Test
        void aBandageIsWithdrawnOnceTheWoundHasStoppedBleeding() {
            Treatment bandage = item("wfmedical:bandage");
            Trauma cut = wound(LimbType.TORSO, "laceration_large", 0.8F);
            assertTrue(TreatmentService.canTreatLimb(profile, bandage, LimbType.TORSO));
            cut.setBleedFactor(0.0F);
            assertFalse(TreatmentService.canTreatLimb(profile, bandage, LimbType.TORSO),
                    "the UI should not offer a dressing for a wound that is not bleeding");
        }

        @Test
        void theMaskHasExactlyTheBitsOfTheTreatableLimbs() {
            wound(LimbType.HEAD, "laceration_large", 0.5F);
            wound(LimbType.RIGHT_LEG, "laceration_large", 0.5F);
            int mask = TreatmentService.treatableMask(profile, item("wfmedical:bandage"));
            assertEquals((1 << LimbType.HEAD.ordinal()) | (1 << LimbType.RIGHT_LEG.ordinal()), mask);
        }

        @Test
        void nullArgumentsAreNotTreatable() {
            assertFalse(TreatmentService.canTreatLimb(null, item("wfmedical:bandage"), LimbType.HEAD));
            assertFalse(TreatmentService.canTreatLimb(profile, null, LimbType.HEAD));
            assertFalse(TreatmentService.canTreatLimb(profile, item("wfmedical:bandage"), null));
        }
    }
}
