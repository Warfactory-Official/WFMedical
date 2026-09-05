package com.warfactory.medical.core.trauma;

import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.support.Fixtures;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraumaTest {

    private static final float EPS = 1.0e-4F;

    private TraumaRegistry registry;

    @BeforeEach
    void setUp() {
        registry = Fixtures.registry();
    }

    private Trauma of(String id, float severity) {
        return new Trauma(registry.getOrThrow(id), LimbType.TORSO, severity, 100L);
    }

    @Nested
    class Severity {

        @Test
        void severityIsClampedToTheTypesCeilingAndFloor() {
            assertEquals(1.0F, of("laceration_large", 5.0F).getSeverity(), EPS);
            assertEquals(0.0F, of("laceration_large", -5.0F).getSeverity(), EPS);
        }

        @Test
        void theSetterClampsToo() {
            Trauma t = of("laceration_large", 0.5F);
            t.setSeverity(99.0F);
            assertEquals(1.0F, t.getSeverity(), EPS);
            t.setSeverity(-1.0F);
            assertEquals(0.0F, t.getSeverity(), EPS);
        }
    }

    @Nested
    class Effects {

        @Test
        void bleedingScalesWithSeverityAndTheBleedFactor() {
            Trauma t = of("laceration_large", 1.0F); // 1.2 per severity
            assertEquals(1.2F, t.bleeding(), EPS);
            t.setBleedFactor(0.25F);
            assertEquals(0.3F, t.bleeding(), EPS);
            t.setBleedFactor(0.0F);
            assertEquals(0.0F, t.bleeding(), EPS);
        }

        @Test
        void healingAWoundBelowItsOriginalSizeShrinksTheBleed() {
            Trauma t = of("laceration_large", 1.0F);
            t.setSeverity(0.5F);
            assertEquals(0.6F, t.bleeding(), EPS, "bleeding tracks the lesser of current and base severity");
        }

        @Test
        void aWoundThatWorsensPastItsBaseDoesNotBleedMoreThanTheOriginal() {
            Trauma t = of("laceration_large", 0.4F);
            float atBase = t.bleeding();
            t.setSeverity(1.0F);
            assertEquals(atBase, t.bleeding(), EPS,
                    "bleeding is capped at the base severity, so worsening cannot outrun the wound it came from");
        }

        @Test
        void stabilisingHalvesPain() {
            Trauma t = of("fracture", 1.0F); // 0.5 pain per severity
            assertEquals(0.5F, t.pain(), EPS);
            t.setStabilized(true);
            assertEquals(0.25F, t.pain(), EPS);
        }

        @Test
        void majorAndMinorWoundsCostDifferentHealthPools() {
            Trauma major = of("laceration_large", 1.0F);
            assertEquals(4.0F, major.healthReduction(), EPS);
            assertEquals(0.0F, major.currentHealthReduction(), EPS);

            Trauma minor = of("blunt_force_trauma", 1.0F);
            assertEquals(0.0F, minor.healthReduction(), EPS);
            assertEquals(12.0F, minor.currentHealthReduction(), EPS);
            assertTrue(minor.isMinor());
        }

        @Test
        void onlyTheFractureCategoryReportsAsAFracture() {
            assertTrue(of("fracture", 1.0F).isFracture());
            assertFalse(of("crush_injury", 1.0F).isFracture());
        }

        @Test
        void bleedControlledOnlyMeansDressedButUntreated() {
            Trauma t = of("puncture", 1.0F);
            assertFalse(t.isBleedControlledOnly(), "an untouched wound is not controlled");
            t.setBleedFactor(0.0F);
            assertTrue(t.isBleedControlledOnly(), "a dressing without treatment");
            t.setTreated(true);
            assertFalse(t.isBleedControlledOnly(), "once treated it heals instead of being frozen");
        }

        @Test
        void bleedFactorIsClampedToTheUnitRange() {
            Trauma t = of("puncture", 1.0F);
            t.setBleedFactor(3.0F);
            assertEquals(1.0F, t.getBleedFactor(), EPS);
            t.setBleedFactor(-1.0F);
            assertEquals(0.0F, t.getBleedFactor(), EPS);
        }
    }

    @Nested
    class Merging {

        @Test
        void aWoundNeverMergesWithItself() {
            Trauma t = of("laceration_small", 0.3F);
            assertFalse(t.canMergeWith(t));
            assertFalse(t.canMergeWith(null));
        }

        @Test
        void mergingRequiresTheSameTypeAndLimb() {
            Trauma torso = of("laceration_small", 0.3F);
            Trauma otherLimb = new Trauma(registry.getOrThrow("laceration_small"), LimbType.HEAD, 0.3F, 0L);
            Trauma otherType = of("puncture", 0.3F);
            assertFalse(torso.canMergeWith(otherLimb));
            assertFalse(torso.canMergeWith(otherType));
        }

        @Test
        void mergingTakesTheLessControlledBleedAndTheEarlierTimestamp() {
            Trauma dressed = of("laceration_small", 0.3F);
            dressed.setBleedFactor(0.0F);
            dressed.setTreated(true);
            dressed.setHealProgress(0.8F);

            Trauma fresh = new Trauma(registry.getOrThrow("laceration_small"), LimbType.TORSO, 0.4F, 50L);
            dressed.mergeIn(fresh);

            assertEquals(0.7F, dressed.getSeverity(), EPS);
            assertEquals(1.0F, dressed.getBleedFactor(), EPS, "fresh damage must reopen a dressed wound");
            assertFalse(dressed.isTreated(), "a merged-in untreated wound is not treated");
            assertEquals(50L, dressed.getTimestamp(), "the wound is as old as its oldest component");
            assertEquals(0.0F, dressed.getHealProgress(), EPS);
        }

        @Test
        void mergedFlagsAreConjunctionsNotDisjunctions() {
            Trauma a = of("laceration_small", 0.2F);
            Trauma b = of("laceration_small", 0.2F);
            a.setStabilized(true);
            a.setClosed(true);
            b.setStabilized(true);
            b.setClosed(true);
            a.mergeIn(b);
            assertTrue(a.isStabilized());
            assertTrue(a.isClosed(), "two closed wounds merge to a closed wound");
        }
    }

    @Nested
    class Persistence {

        @Test
        void aTraumaSurvivesASaveLoadRoundTrip() {
            Trauma t = of("puncture", 0.6F);
            t.setBleedFactor(0.25F);
            t.setTreated(true);
            t.setStabilized(true);
            t.setClosed(true);
            t.setHealProgress(0.4F);

            Trauma loaded = Trauma.load(t.save(), registry);

            assertEquals("puncture", loaded.getType().getId());
            assertEquals(LimbType.TORSO, loaded.getLimb());
            assertEquals(0.6F, loaded.getSeverity(), EPS);
            assertEquals(0.25F, loaded.getBleedFactor(), EPS);
            assertTrue(loaded.isTreated());
            assertTrue(loaded.isStabilized());
            assertTrue(loaded.isClosed());
            assertEquals(100L, loaded.getTimestamp());
            assertEquals(0.4F, loaded.getHealProgress(), EPS);
        }

        @Test
        void anUnknownTypeIdLoadsAsNullRatherThanThrowing() {
            CompoundTag tag = of("puncture", 0.6F).save();
            tag.putString("Type", "some_uninstalled_addon:wound");
            assertNull(Trauma.load(tag, registry));
        }

        @Test
        void baseSeverityIsPreservedSoBleedStaysCappedAcrossASave() {
            Trauma t = of("laceration_large", 1.0F);
            t.setSeverity(0.3F);
            Trauma loaded = Trauma.load(t.save(), registry);
            loaded.setSeverity(1.0F);
            assertEquals(t.bleeding(), 1.2F * 0.3F, EPS);
            assertEquals(1.2F * 1.0F, loaded.bleeding(), EPS,
                    "base severity is the pre-heal size, so a reloaded wound bleeds to its original cap");
        }

        @Nested
        class LegacyMigration {

            /** A tag as written before the bleedFactor model: treated/sutured/bleedStopped booleans. */
            private CompoundTag legacy(boolean treated, boolean sutured, boolean bleedStopped) {
                CompoundTag tag = new CompoundTag();
                tag.putString("Type", "laceration_large");
                tag.putInt("Limb", LimbType.TORSO.ordinal());
                tag.putFloat("Severity", 0.8F);
                tag.putBoolean("Treated", treated);
                tag.putBoolean("Sutured", sutured);
                tag.putBoolean("BleedStopped", bleedStopped);
                tag.putLong("Timestamp", 7L);
                return tag;
            }

            @Test
            void anOldUntouchedWoundStillBleedsFully() {
                Trauma t = Trauma.load(legacy(false, false, false), registry);
                assertEquals(1.0F, t.getBleedFactor(), EPS);
                assertFalse(t.isTreated());
                assertFalse(t.isClosed());
            }

            @Test
            void anOldSuturedWoundBecomesClosedAndTreatedWithNoBleed() {
                Trauma t = Trauma.load(legacy(false, true, false), registry);
                assertEquals(0.0F, t.getBleedFactor(), EPS);
                assertTrue(t.isClosed());
                assertTrue(t.isTreated(), "a suture always implied treatment");
            }

            @Test
            void anOldBleedStoppedWoundLosesItsBleedButIsNotTreated() {
                Trauma t = Trauma.load(legacy(false, false, true), registry);
                assertEquals(0.0F, t.getBleedFactor(), EPS);
                assertFalse(t.isTreated());
            }

            @Test
            void anOldTreatedWoundKeepsAResidualBleed() {
                Trauma t = Trauma.load(legacy(true, false, false), registry);
                assertEquals(0.25F, t.getBleedFactor(), EPS);
                assertTrue(t.isTreated());
            }

            @Test
            void aMissingBaseSeverityFallsBackToTheCurrentSeverity() {
                Trauma t = Trauma.load(legacy(false, false, false), registry);
                assertEquals(0.8F * 1.2F, t.bleeding(), EPS);
            }
        }
    }

    @Nested
    class Responses {

        @Test
        void theShippedTypesDeclareTheTreatmentsTheirItemsTarget() {
            assertTrue(registry.getOrThrow("laceration_large").respondsTo(TreatmentAction.REDUCE_BLEEDING));
            assertTrue(registry.getOrThrow("laceration_large").respondsTo(TreatmentAction.SUTURE_WOUND));
            assertTrue(registry.getOrThrow("fracture").respondsTo(TreatmentAction.STABILIZE_FRACTURE));
            assertTrue(registry.getOrThrow("burn").respondsTo(TreatmentAction.TREAT_BURN));
            assertTrue(registry.getOrThrow("radiation_burn").respondsTo(TreatmentAction.TREAT_RADIATION));
        }

        @Test
        void aBandageDoesNothingForInternalBleedingButAHemostaticDoes() {
            TraumaType internal = registry.getOrThrow("internal_bleeding");
            assertFalse(internal.respondsTo(TreatmentAction.REDUCE_BLEEDING),
                    "you cannot bandage a bleed that is inside the body");
            assertTrue(internal.respondsTo(TreatmentAction.BOOST_CLOTTING));
        }

        @Test
        void aTypeWithNoResponseForAnActionReturnsNull() {
            assertNull(registry.getOrThrow("fracture").response(TreatmentAction.REDUCE_BLEEDING));
            assertNull(registry.getOrThrow("fracture").response(null));
            assertFalse(registry.getOrThrow("fracture").respondsTo(null));
        }
    }
}
