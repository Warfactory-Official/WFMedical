package com.warfactory.medical.core.damage;

import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import com.warfactory.medical.support.Fixtures;
import com.warfactory.medical.support.TestConfig;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wound table: what a hit of a given category, energy and armour outcome actually leaves behind.
 * Pure, apart from the fracture roll and one config read (fallFractureMinBlocks).
 */
class TraumaGeneratorTest {

    private TraumaRegistry registry;

    @BeforeAll
    static void loadConfig() {
        TestConfig.load();
    }

    @BeforeEach
    void setUp() {
        registry = Fixtures.registry();
    }

    private List<Trauma> gen(DamageCategory cat, ArmorEvaluation.Outcome outcome, LimbType limb,
                             float energy, RandomSource rand) {
        return TraumaGenerator.generate(cat, outcome, limb, energy, registry, 0L, rand);
    }

    private List<Trauma> gen(DamageCategory cat, LimbType limb, float energy) {
        return gen(cat, ArmorEvaluation.Outcome.FULL, limb, energy, Fixtures.neverRolls());
    }

    private static boolean has(List<Trauma> out, String id) {
        return out.stream().anyMatch(t -> t.getType().getId().equals(id));
    }

    private static boolean hasCategory(List<Trauma> out, TraumaCategory cat) {
        return out.stream().anyMatch(t -> t.getType().getCategory() == cat);
    }

    @Nested
    class Ballistic {

        @Test
        void aBulletLeavesAPunctureALacerationAndInternalBleeding() {
            List<Trauma> out = gen(DamageCategory.BALLISTIC, LimbType.TORSO, 10.0F);
            assertTrue(has(out, "puncture"), "a bullet must leave an entry wound");
            assertTrue(has(out, "laceration_large"));
            assertTrue(has(out, "internal_bleeding"));
            assertTrue(out.size() >= 3, "one bullet legitimately makes several wounds");
        }

        @Test
        void moreEnergyMeansMoreSevereWounds() {
            float weak = severityOf(gen(DamageCategory.BALLISTIC, LimbType.TORSO, 2.0F), "puncture");
            float strong = severityOf(gen(DamageCategory.BALLISTIC, LimbType.TORSO, 15.0F), "puncture");
            assertTrue(strong > weak, weak + " -> " + strong);
        }

        @Test
        void theEnergyFactorIsClampedSoAnAbsurdHitDoesNotOverflow() {
            List<Trauma> out = gen(DamageCategory.BALLISTIC, LimbType.TORSO, 1.0e6F);
            for (Trauma t : out) {
                assertTrue(t.getSeverity() <= t.getType().getMaxSeverity(),
                        t.getType().getId() + " exceeded its max severity");
            }
        }

        @Test
        void aLimbHitCanBreakABoneAndATorsoHitCannot() {
            assertTrue(gen(DamageCategory.BALLISTIC, ArmorEvaluation.Outcome.FULL, LimbType.LEFT_LEG,
                    12.0F, Fixtures.alwaysRolls()).stream().anyMatch(Trauma::isFracture));
            assertFalse(gen(DamageCategory.BALLISTIC, ArmorEvaluation.Outcome.FULL, LimbType.TORSO,
                    12.0F, Fixtures.alwaysRolls()).stream().anyMatch(Trauma::isFracture),
                    "there is no bone in the torso rig box to break");
            assertFalse(gen(DamageCategory.BALLISTIC, ArmorEvaluation.Outcome.FULL, LimbType.HEAD,
                    12.0F, Fixtures.alwaysRolls()).stream().anyMatch(Trauma::isFracture));
        }

        @Test
        void aMissedRollLeavesNoFracture() {
            assertFalse(gen(DamageCategory.BALLISTIC, ArmorEvaluation.Outcome.FULL, LimbType.LEFT_LEG,
                    12.0F, Fixtures.neverRolls()).stream().anyMatch(Trauma::isFracture));
        }

        @Test
        void aNullRandomSourceNeverFractures() {
            assertFalse(gen(DamageCategory.BALLISTIC, ArmorEvaluation.Outcome.FULL, LimbType.LEFT_LEG,
                    12.0F, null).stream().anyMatch(Trauma::isFracture));
        }
    }

    @Nested
    class Armour {

        @Test
        void fullyBlockedLeavesOnlyABruise() {
            List<Trauma> out = gen(DamageCategory.BALLISTIC, ArmorEvaluation.Outcome.BLOCKED,
                    LimbType.TORSO, 20.0F, Fixtures.alwaysRolls());
            assertEquals(1, out.size());
            assertEquals("bruise", out.get(0).getType().getId());
            assertFalse(has(out, "internal_bleeding"), "stopped by plate means no penetrating wound");
        }

        @Test
        void aPartialStopLeavesABruiseAndASmallCut() {
            List<Trauma> out = gen(DamageCategory.BALLISTIC, ArmorEvaluation.Outcome.PARTIAL,
                    LimbType.TORSO, 20.0F, Fixtures.alwaysRolls());
            assertTrue(has(out, "bruise"));
            assertTrue(has(out, "laceration_small"));
            assertFalse(has(out, "puncture"));
        }

        @Test
        void armourDoesNotStopFireChemicalRadiationOrFalls() {
            // These branches return before the armour switch is consulted, matching ArmorEvaluation, which
            // reports FULL for them anyway. If either side changed alone, a plate carrier would stop a fall.
            for (DamageCategory cat : new DamageCategory[]{DamageCategory.FIRE, DamageCategory.CHEMICAL,
                    DamageCategory.RADIATION, DamageCategory.FALL}) {
                List<Trauma> blocked = gen(cat, ArmorEvaluation.Outcome.BLOCKED, LimbType.TORSO, 8.0F,
                        Fixtures.neverRolls());
                assertFalse(blocked.isEmpty(), cat + " produced nothing at all");
                assertFalse(has(blocked, "bruise") && blocked.size() == 1,
                        cat + " was reduced to a bruise by armour");
            }
        }

        @Test
        void aNullOutcomeIsTreatedAsUnarmoured() {
            List<Trauma> out = gen(DamageCategory.BALLISTIC, null, LimbType.TORSO, 10.0F,
                    Fixtures.neverRolls());
            assertTrue(has(out, "puncture"));
        }
    }

    @Nested
    class Elemental {

        @Test
        void fireLeavesOnlyABurn() {
            List<Trauma> out = gen(DamageCategory.FIRE, LimbType.TORSO, 5.0F);
            assertEquals(1, out.size());
            assertEquals("burn", out.get(0).getType().getId());
        }

        @Test
        void radiationAndChemicalHaveTheirOwnWoundTypes() {
            assertEquals("radiation_burn", gen(DamageCategory.RADIATION, LimbType.TORSO, 5.0F)
                    .get(0).getType().getId());
            assertEquals("chemical_burn", gen(DamageCategory.CHEMICAL, LimbType.TORSO, 5.0F)
                    .get(0).getType().getId());
        }

        @Test
        void anExplosionCrushesAndBurnsAtOnce() {
            List<Trauma> out = gen(DamageCategory.EXPLOSION, LimbType.TORSO, 12.0F);
            assertTrue(has(out, "crush_injury"));
            assertTrue(has(out, "burn"));
        }

        @Test
        void anExplosionOnALimbCanAlsoBreakIt() {
            assertTrue(gen(DamageCategory.EXPLOSION, ArmorEvaluation.Outcome.FULL, LimbType.RIGHT_ARM,
                    12.0F, Fixtures.alwaysRolls()).stream().anyMatch(Trauma::isFracture));
        }
    }

    @Nested
    class Falls {

        @Test
        void aFallIsBluntForceThatNeverBleedsAndNeverCrushes() {
            List<Trauma> out = gen(DamageCategory.FALL, LimbType.LEFT_LEG, 7.0F);
            assertTrue(has(out, "blunt_force_trauma"));
            assertFalse(hasCategory(out, TraumaCategory.CRUSH_INJURY),
                    "a landing is not a crush; that distinction is why falls self-heal");
            for (Trauma t : out) {
                if (!t.isFracture()) {
                    assertEquals(0.0F, t.bleeding(), 1.0e-6F, t.getType().getId() + " bled");
                }
            }
        }

        @Test
        void aShortFallCannotBreakALegHoweverTheDiceLand() {
            // fallFractureMinBlocks defaults to 5 blocks, i.e. an energy floor of 5 - 3 = 2.
            List<Trauma> out = gen(DamageCategory.FALL, ArmorEvaluation.Outcome.FULL, LimbType.LEFT_LEG,
                    1.0F, Fixtures.alwaysRolls());
            assertFalse(out.stream().anyMatch(Trauma::isFracture),
                    "below the configured floor the chance must be exactly zero, not merely small");
        }

        @Test
        void aLongFallCanBreakALegOnALuckyRoll() {
            assertTrue(gen(DamageCategory.FALL, ArmorEvaluation.Outcome.FULL, LimbType.LEFT_LEG,
                    20.0F, Fixtures.alwaysRolls()).stream().anyMatch(Trauma::isFracture));
        }

        @Test
        void theConfiguredFloorActuallyMovesTheThreshold() {
            // Guard: without this, the previous test would pass with the floor hardcoded to anything.
            Object prev = TestConfig.set("balance.fallFractureMinBlocks", 60.0D);
            try {
                assertFalse(gen(DamageCategory.FALL, ArmorEvaluation.Outcome.FULL, LimbType.LEFT_LEG,
                        20.0F, Fixtures.alwaysRolls()).stream().anyMatch(Trauma::isFracture),
                        "raising fallFractureMinBlocks must make a 20-energy landing safe again");
            } finally {
                TestConfig.set("balance.fallFractureMinBlocks", prev);
            }
        }

        @Test
        void deeperFallsHurtMore() {
            float shallow = severityOf(gen(DamageCategory.FALL, LimbType.LEFT_LEG, 2.0F),
                    "blunt_force_trauma");
            float deep = severityOf(gen(DamageCategory.FALL, LimbType.LEFT_LEG, 10.0F),
                    "blunt_force_trauma");
            assertTrue(deep > shallow, shallow + " -> " + deep);
        }

        @Test
        void fallDamageCostsCurrentHealthNotMaxHealth() {
            for (Trauma t : gen(DamageCategory.FALL, LimbType.LEFT_LEG, 10.0F)) {
                if (t.isFracture()) {
                    continue;
                }
                assertEquals(0.0F, t.healthReduction(), 1.0e-6F,
                        "a bruise from a fall must recover on its own, not cap max health");
                assertTrue(t.currentHealthReduction() > 0.0F);
            }
        }
    }

    @Nested
    class MeleeAndGeneric {

        @Test
        void aFistIsAlwaysALightBruiseRegardlessOfEnergy() {
            List<Trauma> light = gen(DamageCategory.UNARMED, LimbType.HEAD, 1.0F);
            List<Trauma> heavy = gen(DamageCategory.UNARMED, LimbType.HEAD, 100.0F);
            assertEquals(1, light.size());
            assertEquals("blunt_force_trauma", light.get(0).getType().getId());
            assertEquals(light.get(0).getSeverity(), heavy.get(0).getSeverity(), 1.0e-6F);
        }

        @Test
        void aLightBluntBlowBruisesAndAHeavyOneCrushes() {
            List<Trauma> light = gen(DamageCategory.BLUNT, LimbType.TORSO, 1.0F);
            assertTrue(has(light, "blunt_force_trauma"));
            assertFalse(has(light, "crush_injury"));

            List<Trauma> heavy = gen(DamageCategory.BLUNT, LimbType.TORSO, 10.0F);
            assertTrue(has(heavy, "crush_injury"));
            assertTrue(has(heavy, "bruise"));
        }

        @Test
        void aLightCutIsSmallAndAHeavyOneOpensUp() {
            List<Trauma> light = gen(DamageCategory.SLASHING, LimbType.TORSO, 1.0F);
            assertTrue(has(light, "laceration_small"));
            assertFalse(has(light, "laceration_large"));

            List<Trauma> heavy = gen(DamageCategory.SLASHING, LimbType.TORSO, 10.0F);
            assertTrue(has(heavy, "laceration_large"));
            assertTrue(has(heavy, "internal_bleeding"));
        }

        @Test
        void genericAndPiercingUseTheSameOpenWoundTable() {
            assertTrue(has(gen(DamageCategory.GENERIC, LimbType.TORSO, 10.0F), "laceration_large"));
            assertTrue(has(gen(DamageCategory.PIERCING, LimbType.TORSO, 10.0F), "laceration_large"));
        }
    }

    @Nested
    class Robustness {

        @Test
        void aMissingLimbOrRegistryYieldsNothingRatherThanThrowing() {
            assertTrue(TraumaGenerator.generate(DamageCategory.BALLISTIC, ArmorEvaluation.Outcome.FULL,
                    null, 10.0F, registry, 0L, Fixtures.rand(1L)).isEmpty());
            assertTrue(TraumaGenerator.generate(DamageCategory.BALLISTIC, ArmorEvaluation.Outcome.FULL,
                    LimbType.TORSO, 10.0F, null, 0L, Fixtures.rand(1L)).isEmpty());
        }

        @Test
        void aNullCategoryIsTreatedAsGeneric() {
            assertTrue(has(TraumaGenerator.generate(null, ArmorEvaluation.Outcome.FULL, LimbType.TORSO,
                    10.0F, registry, 0L, Fixtures.neverRolls()), "laceration_large"));
        }

        @Test
        void negativeEnergyIsFlooredAtZero() {
            List<Trauma> out = gen(DamageCategory.BALLISTIC, LimbType.TORSO, -50.0F);
            assertFalse(out.isEmpty());
            for (Trauma t : out) {
                assertTrue(t.getSeverity() > 0.0F, t.getType().getId() + " came out at zero severity");
            }
        }

        @Test
        void everyGeneratedWoundHasANonZeroSeverity() {
            // add() floors severity at 0.01 precisely so a zero-contribution type is not a silent no-op.
            for (DamageCategory cat : DamageCategory.values()) {
                for (Trauma t : gen(cat, LimbType.LEFT_LEG, 0.0F)) {
                    assertTrue(t.getSeverity() > 0.0F, cat + "/" + t.getType().getId());
                }
            }
        }

        @Test
        void anIncompleteRegistryFallsBackToTheCategoryRatherThanDroppingTheWound() {
            // A pack that renames "puncture" must still get a puncture-category wound out of a bullet.
            TraumaRegistry sparse = new TraumaRegistry();
            sparse.register(TraumaType.builder("my_pack_hole", TraumaCategory.PUNCTURE)
                    .bleedingPerSeverity(1.0F).build());
            List<Trauma> out = TraumaGenerator.generate(DamageCategory.BALLISTIC,
                    ArmorEvaluation.Outcome.FULL, LimbType.TORSO, 10.0F, sparse, 0L, Fixtures.neverRolls());
            assertTrue(out.stream().anyMatch(t -> t.getType().getId().equals("my_pack_hole")));
        }

        @Test
        void everyGeneratedWoundIsTaggedWithTheLimbItWasAskedFor() {
            for (DamageCategory cat : DamageCategory.values()) {
                for (Trauma t : gen(cat, ArmorEvaluation.Outcome.FULL, LimbType.RIGHT_ARM, 10.0F,
                        Fixtures.alwaysRolls())) {
                    assertEquals(LimbType.RIGHT_ARM, t.getLimb(), cat + "/" + t.getType().getId());
                }
            }
        }
    }

    private static float severityOf(List<Trauma> out, String id) {
        return out.stream().filter(t -> t.getType().getId().equals(id))
                .map(Trauma::getSeverity).findFirst()
                .orElseThrow(() -> new AssertionError("no " + id + " in " + out.size() + " wounds"));
    }
}
