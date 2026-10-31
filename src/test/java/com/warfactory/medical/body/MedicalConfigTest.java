package com.warfactory.medical.body;

import com.warfactory.medical.damage.DamageCategory;
import com.warfactory.medical.support.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.warfactory.medical.damage.DamageConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalConfigTest {

    @BeforeAll
    static void loadConfig() {
        TestConfig.load();
    }

    @Test
    void theSpecIsActuallyLoaded() {
        // Guard: every other test in this file (and TraumaGeneratorTest, TreatmentServiceTest, ...) would
        // throw rather than pass vacuously if this were false, but naming it makes the failure legible.
        assertTrue(MedicalConfig.SPEC.isLoaded(), "TestConfig.load() did not bind the spec");
    }

    @Nested
    class Physiology {

        @Test
        void toPhysiologyParamsCarriesTheConfiguredValues() {
            PhysiologyParams p = MedicalConfig.toPhysiologyParams();
            assertEquals(MedicalConfig.maxHealthPoints(), p.maxHealthPoints());
            assertEquals(MedicalConfig.maxBloodMl(), p.maxBloodMl());
            assertEquals(MedicalConfig.bloodLowFraction(), p.bloodLowFraction());
            assertEquals(MedicalConfig.bloodCriticalFraction(), p.bloodCriticalFraction());
            assertEquals(MedicalConfig.painShockThreshold(), p.painShockThreshold());
            assertEquals(MedicalConfig.bloodDeathLossFraction(), p.bloodDeathLossFraction());
            assertEquals(MedicalConfig.bloodUnconsciousLossFraction(), p.bloodUnconsciousLossFraction());
            assertEquals(MedicalConfig.enableBleedout(), p.bleedoutEnabled());
            assertEquals(MedicalConfig.bleedoutTicks(), p.bleedoutTicks());
            assertEquals(MedicalConfig.tourniquetBleedMultiplier(), p.tourniquetBleedMultiplier());
            assertEquals(MedicalConfig.headDepletionInstakill(), p.headDepletionInstakill());
            assertEquals(MedicalConfig.torsoDepletionInstakill(), p.torsoDepletionInstakill());
        }

        @Test
        void theFieldsConfigDoesNotExposeKeepTheirHardcodedDefault() {
            // These five are deliberately not configurable; toPhysiologyParams pulls them from
            // PhysiologyParams.defaults(). If one ever gains a config key, this test should be updated
            // rather than silently keeping the old constant.
            PhysiologyParams d = PhysiologyParams.defaults();
            PhysiologyParams p = MedicalConfig.toPhysiologyParams();
            assertEquals(d.bloodDeathMl(), p.bloodDeathMl());
            assertEquals(d.painMaxHealthPenalty(), p.painMaxHealthPenalty());
            assertEquals(d.painSpeedFloor(), p.painSpeedFloor());
        }

        @Test
        void theShippedDefaultsAreASurvivableBaseline() {
            PhysiologyParams p = MedicalConfig.toPhysiologyParams();
            assertTrue(p.maxHealthPoints() > 0.0F);
            assertTrue(p.maxBloodMl() > 0.0D);
            // Death must be a strictly deeper loss than unconsciousness or a player dies before ever downing.
            assertTrue(p.bloodDeathLossFraction() > p.bloodUnconsciousLossFraction(),
                    "bloodDeathLossFraction must exceed bloodUnconsciousLossFraction");
            // Pain must reach shock before it reaches knockout.
            assertTrue(p.painUnconsciousThreshold() > p.painShockThreshold(),
                    "painUnconsciousThreshold must exceed painShockThreshold");
            // Critical must be reachable before death.
            assertTrue(p.bloodCriticalFraction() < p.bloodLowFraction());
        }
    }

    @Nested
    class DamageMapping {

        @Test
        void everyCategoryResolvesAMajorTraumaFraction() {
            for (DamageCategory cat : DamageCategory.values()) {
                assertTrue(DamageConfig.majorTraumaFraction(cat) > 0.0D, "no fraction for " + cat);
            }
            assertTrue(DamageConfig.majorTraumaFraction(null) > 0.0D, "null must fall back to the default");
        }

        @Test
        void burningAndIrradiationNeverInstakillOnImpact() {
            assertFalse(DamageConfig.canInstakillOnImpact(DamageCategory.FIRE));
            assertFalse(DamageConfig.canInstakillOnImpact(DamageCategory.CHEMICAL));
            assertFalse(DamageConfig.canInstakillOnImpact(DamageCategory.RADIATION));
            assertTrue(DamageConfig.canInstakillOnImpact(DamageCategory.BALLISTIC));
            assertTrue(DamageConfig.canInstakillOnImpact(DamageCategory.EXPLOSION));
        }

        @Test
        void anUnmappedDamageSourceHasNoConfiguredCategory() {
            assertNull(DamageConfig.damageSourceCategory("minecraft:definitely_not_a_damage_type"));
            assertNull(DamageConfig.damageSourceCategory(null));
            assertNull(DamageConfig.damageSourceCategory(""));
        }

        @Test
        void aConfiguredOverrideIsParsedCaseInsensitivelyOnTheCategory() {
            Object prev = TestConfig.set("compat.damageSourceCategories",
                    java.util.List.of("mymod:railgun=ballistic", "mymod:acid=CHEMICAL", "broken", "=x", "y="));
            try {
                assertEquals(DamageCategory.BALLISTIC, DamageConfig.damageSourceCategory("mymod:railgun"));
                assertEquals(DamageCategory.CHEMICAL, DamageConfig.damageSourceCategory("mymod:acid"));
                // Malformed entries are skipped, not fatal.
                assertNull(DamageConfig.damageSourceCategory("broken"));
                assertNull(DamageConfig.damageSourceCategory("y"));
            } finally {
                TestConfig.set("compat.damageSourceCategories", prev);
            }
        }
    }
}
