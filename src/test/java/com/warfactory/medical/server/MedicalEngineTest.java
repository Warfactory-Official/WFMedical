package com.warfactory.medical.server;

import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.PhysiologyParams;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.support.Fixtures;
import com.warfactory.medical.support.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-tick wound step -- self-heal, worsening, drug decay -- and the wake-up score.
 *
 * <p>These are the slow-moving rules nobody notices being wrong until a wound that should have closed is
 * still open twenty minutes later. {@code advanceTrauma} is pure over the profile plus config, so it can be
 * run for thousands of simulated ticks here in milliseconds instead of being play-tested.
 */
class MedicalEngineTest {

    private static final float EPS = 1.0e-4F;
    private static final PhysiologyParams PARAMS = PhysiologyParams.defaults();

    private TraumaRegistry registry;
    private MedicalProfile profile;

    @BeforeAll
    static void loadConfig() {
        TestConfig.load();
    }

    @BeforeEach
    void setUp() {
        registry = Fixtures.registry();
        profile = Fixtures.profile();
    }

    /** Run {@code ticks} game ticks through the wound step at the engine's real update interval. */
    private void advance(int ticks) {
        int interval = 5;
        for (int t = 0; t < ticks; t += interval) {
            MedicalEngine.advanceTrauma(profile, interval);
        }
        profile.recompute(PARAMS);
    }

    private Trauma wound(LimbType limb, String id, float severity) {
        Trauma t = Fixtures.wound(profile, registry, limb, id, severity);
        profile.limb(limb).rebuildCache();
        return t;
    }

    private static int minutes(int m) {
        return m * 60 * 20;
    }

    @Nested
    class SelfHealing {

        @Test
        void aMinorWoundClosesOnItsOwn() {
            wound(LimbType.TORSO, "bruise", 0.3F);
            advance(minutes(20));
            assertTrue(profile.limb(LimbType.TORSO).getTraumas().isEmpty(),
                    "a bruise must not be permanent");
        }

        @Test
        void aFallsBluntTraumaHealsAndGivesBackTheCurrentHealthItCost() {
            wound(LimbType.LEFT_LEG, "blunt_force_trauma", 0.6F);
            profile.recompute(PARAMS);
            assertTrue(profile.cached().effectiveCurrentHealth() < PARAMS.maxHealthPoints());

            advance(minutes(30));
            assertEquals(PARAMS.maxHealthPoints(), profile.cached().effectiveCurrentHealth(), EPS,
                    "blunt-force health loss is recoverable by design");
        }

        @Test
        void aSmallBleedClotsByItself() {
            // Below bleedingSelfHealThreshold even a major bleeding wound closes without treatment. Above it
            // the wound worsens instead -- that threshold is the whole "do I need a bandage?" decision.
            double threshold = com.warfactory.medical.config.MedicalConfig.bleedingSelfHealThreshold();
            Trauma nick = wound(LimbType.LEFT_ARM, "laceration_large", (float) threshold * 0.5F);
            assertTrue(nick.bleeding() > 0.0F);
            advance(minutes(30));
            assertEquals(0.0D, profile.cached().totalBleeding(), 1.0e-6D);
            assertTrue(profile.limb(LimbType.LEFT_ARM).getTraumas().isEmpty());
        }

        @Test
        void anUntreatedMajorBleedGetsWorseInsteadOfBetter() {
            Trauma gash = wound(LimbType.TORSO, "laceration_large", 0.9F);
            float before = gash.getSeverity();
            advance(minutes(10));
            assertTrue(gash.getSeverity() > before,
                    "an open wound above the clot threshold must worsen: " + before + " -> "
                            + gash.getSeverity());
        }

        @Test
        void aTreatedMajorWoundMendsInstead() {
            Trauma gash = wound(LimbType.TORSO, "laceration_large", 0.9F);
            gash.setTreated(true);
            gash.setBleedFactor(0.0F);
            advance(minutes(60));
            assertTrue(profile.limb(LimbType.TORSO).getTraumas().isEmpty(),
                    "a sutured wound should be gone after an hour");
        }

        @Test
        void aDressedButUntreatedWoundIsFrozenRatherThanHealingOrWorsening() {
            Trauma crush = wound(LimbType.TORSO, "crush_injury", 0.7F);
            crush.setBleedFactor(0.0F);
            assertTrue(crush.isBleedControlledOnly());
            advance(minutes(30));
            assertEquals(0.7F, crush.getSeverity(), EPS,
                    "bandaging a crush buys time; it must neither heal it nor let it run away");
        }

        @Test
        void aStabilisedFractureKnitsOverTheConfiguredTime() {
            Trauma broken = wound(LimbType.RIGHT_LEG, "fracture", 1.0F);
            broken.setStabilized(true);
            advance(minutes(10));
            assertTrue(broken.getSeverity() < 1.0F, "a splinted bone should be mending");
        }

        @Test
        void anUnsplintedFractureAlsoKnitsButFarMoreSlowly() {
            double fractureMinutes = com.warfactory.medical.config.MedicalConfig.fractureSelfHealMinutes();
            Trauma broken = wound(LimbType.RIGHT_LEG, "fracture", 1.0F);
            advance(minutes((int) fractureMinutes + 5));
            assertTrue(profile.limb(LimbType.RIGHT_LEG).getTraumas().isEmpty(),
                    "a bone left alone still knits eventually, over fractureSelfHealMinutes");
        }

        @Test
        void aPermanentWoundNeverDisappears() {
            // internal_bleeding is flagged permanent: it can be reduced to nothing but not removed by time.
            Trauma internal = wound(LimbType.TORSO, "internal_bleeding", 0.2F);
            internal.setTreated(true);
            advance(minutes(120));
            assertEquals(1, profile.limb(LimbType.TORSO).getTraumas().size(),
                    "a permanent wound must survive as a scar rather than vanishing");
        }

        @Test
        void minorLimbDamageRegeneratesToZero() {
            profile.limb(LimbType.HEAD).setMinorDamage(4.0F);
            advance(minutes(20));
            assertEquals(0.0F, profile.limb(LimbType.HEAD).getMinorDamage(), EPS);
        }
    }

    @Nested
    class DrugDecay {

        @Test
        void painSuppressionWearsOffOverTenMinutes() {
            profile.setPainSuppression(1.0F);
            advance(minutes(5));
            assertTrue(profile.getPainSuppression() > 0.3F && profile.getPainSuppression() < 0.7F,
                    "half-life check: " + profile.getPainSuppression());
            advance(minutes(6));
            assertEquals(0.0F, profile.getPainSuppression(), EPS);
        }

        @Test
        void limbNumbingWearsOffAndDirtiesTheProfile() {
            profile.limb(LimbType.LEFT_ARM).setLocalNumbing(0.9F);
            profile.recompute(PARAMS);
            MedicalEngine.advanceTrauma(profile, 5);
            assertTrue(profile.isDirty(), "decaying numbing changes perceived pain, so it must resync");
            advance(minutes(10));
            assertEquals(0.0F, profile.limb(LimbType.LEFT_ARM).getLocalNumbing(), EPS);
        }

        @Test
        void aClottingBoostSpeedsUpSelfHealing() {
            // Three seconds: short enough that neither run finishes, so this compares rates rather than
            // two wounds that both happen to have closed.
            float start = (float) com.warfactory.medical.config.MedicalConfig.bleedingSelfHealThreshold() - 0.01F;
            Trauma withBoost = wound(LimbType.TORSO, "laceration_large", start);
            profile.setClottingBoost(1.0F);
            advance(60);
            float boosted = withBoost.getSeverity();

            setUp();
            Trauma plain = wound(LimbType.TORSO, "laceration_large", start);
            advance(60);

            assertTrue(boosted < plain.getSeverity(),
                    "hemostatic should out-heal an untreated wound: " + boosted + " vs " + plain.getSeverity());
            assertTrue(boosted > 0.0F, "the window was too long to be measuring a rate");
        }

        @Test
        void aClottingBoostLetsALargerWoundClotThanItOtherwiseCould() {
            // The boost raises bleedingSelfHealThreshold; above that line a wound worsens instead of clotting.
            float over = (float) com.warfactory.medical.config.MedicalConfig.bleedingSelfHealThreshold() + 0.05F;

            Trauma untreated = wound(LimbType.TORSO, "laceration_large", over);
            advance(minutes(2));
            assertTrue(untreated.getSeverity() > over,
                    "control: above the threshold and with no clotting, the wound must run away");

            setUp();
            Trauma clotting = wound(LimbType.TORSO, "laceration_large", over);
            profile.setClottingBoost(1.0F);
            advance(minutes(2));
            assertTrue(clotting.getSeverity() < over,
                    "with clotting active the same wound should close instead");
        }
    }

    @Nested
    class DeathProgress {

        @Test
        void aFullPlayerHasNoBleedOutProgress() {
            MedicalEngine.updateDeathProgress(profile, PARAMS);
            assertEquals(0.0F, profile.getDeathProgress(), EPS);
        }

        @Test
        void progressRunsFromTheUnconsciousBandToTheDeathBand() {
            double max = PARAMS.maxBloodMl();
            profile.setBloodMl(max * (1.0D - PARAMS.bloodUnconsciousLossFraction()));
            MedicalEngine.updateDeathProgress(profile, PARAMS);
            assertEquals(0.0F, profile.getDeathProgress(), 1.0e-3F, "progress starts where downing starts");

            profile.setBloodMl(max * (1.0D - PARAMS.bloodDeathLossFraction()));
            MedicalEngine.updateDeathProgress(profile, PARAMS);
            assertEquals(1.0F, profile.getDeathProgress(), 1.0e-3F, "and reaches 1 at the death fraction");
        }

        @Test
        void progressIsClampedAtBothEnds() {
            profile.setBloodMl(0.0D);
            MedicalEngine.updateDeathProgress(profile, PARAMS);
            assertEquals(1.0F, profile.getDeathProgress(), EPS);

            profile.setBloodMl(PARAMS.maxBloodMl());
            MedicalEngine.updateDeathProgress(profile, PARAMS);
            assertEquals(0.0F, profile.getDeathProgress(), EPS);
        }

        @Test
        void progressIsMonotonicInBloodLoss() {
            float last = -1.0F;
            for (int pct = 100; pct >= 0; pct -= 5) {
                profile.setBloodMl(PARAMS.maxBloodMl() * pct / 100.0D);
                MedicalEngine.updateDeathProgress(profile, PARAMS);
                assertTrue(profile.getDeathProgress() >= last,
                        "progress went backwards at " + pct + "% blood");
                last = profile.getDeathProgress();
            }
        }
    }

    @Nested
    class WakeUp {

        private DerivedStats stats(float systemicPain, double bleeding) {
            return new DerivedStats(30.0F, 0.0F, 30.0F, bleeding, systemicPain, systemicPain, 1.0F, false,
                    1.0F, com.warfactory.medical.core.HealthState.UNCONSCIOUS,
                    false, false, false, false, false, false, false);
        }

        @Test
        void anOtherwiseFineDownedPlayerScoresZeroAndCanWake() {
            assertEquals(0.0D, MedicalEngine.wakeupScore(profile, stats(0.0F, 0.0D)), 1.0e-9D);
        }

        @Test
        void everyContributorPushesTheScoreUp() {
            double base = MedicalEngine.wakeupScore(profile, stats(0.0F, 0.0D));

            profile.setBloodMl(profile.getMaxBloodMl() * 0.5D);
            double withBlood = MedicalEngine.wakeupScore(profile, stats(0.0F, 0.0D));
            assertTrue(withBlood > base, "blood loss should keep you down");

            profile.setBloodMl(profile.getMaxBloodMl());
            assertTrue(MedicalEngine.wakeupScore(profile, stats(1.0F, 0.0D)) > base, "pain should keep you down");
            assertTrue(MedicalEngine.wakeupScore(profile, stats(0.0F, 10.0D)) > base,
                    "an active bleed should keep you down");

            profile.setDrugLoad(3.0F);
            assertTrue(MedicalEngine.wakeupScore(profile, stats(0.0F, 0.0D)) > base,
                    "a drug load should keep you down");
        }

        @Test
        void theScoreIsNeverNegativeEvenWithAnOverfullProfile() {
            profile.setMaxBloodMl(1000.0D);
            profile.setBloodMl(1000.0D);
            assertTrue(MedicalEngine.wakeupScore(profile, stats(0.0F, 0.0D)) >= 0.0D);
        }

        @Test
        void treatingTheCauseLowersTheScore() {
            profile.setBloodMl(profile.getMaxBloodMl() * 0.4D);
            double hurt = MedicalEngine.wakeupScore(profile, stats(0.8F, 5.0D));
            profile.setBloodMl(profile.getMaxBloodMl());
            double treated = MedicalEngine.wakeupScore(profile, stats(0.0F, 0.0D));
            assertTrue(treated < hurt, "a medic's work has to actually move the wake-up threshold");
        }
    }

    @Nested
    class Idempotence {

        @Test
        void anUninjuredProfileIsNotDirtiedByTicking() {
            profile.recompute(PARAMS);
            assertFalse(profile.isDirty());
            for (int i = 0; i < 200; i++) {
                MedicalEngine.advanceTrauma(profile, 5);
            }
            assertFalse(profile.isDirty(),
                    "a healthy player must not generate a sync packet every tick");
        }

        @Test
        void severityNeverGoesNegativeOrPastTheCeiling() {
            for (LimbType lt : LimbType.VALUES) {
                wound(lt, "laceration_large", 0.05F);
                wound(lt, "internal_bleeding", 0.9F);
                wound(lt, "bruise", 0.1F);
            }
            advance(minutes(120));
            for (Trauma t : profile.allTraumas()) {
                assertTrue(t.getSeverity() >= 0.0F, t.getType().getId() + " went negative");
                assertTrue(t.getSeverity() <= t.getType().getMaxSeverity(),
                        t.getType().getId() + " exceeded its ceiling");
            }
        }
    }
}
