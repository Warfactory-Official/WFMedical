package com.warfactory.medical.core.limb;

import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import com.warfactory.medical.support.Fixtures;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimbTest {

    private static final float EPS = 1.0e-4F;

    private TraumaRegistry registry;
    private Limb limb;

    @BeforeEach
    void setUp() {
        registry = Fixtures.registry();
        limb = new Limb(LimbType.LEFT_LEG);
    }

    private Trauma trauma(String id, float severity) {
        return new Trauma(registry.getOrThrow(id), LimbType.LEFT_LEG, severity, 0L);
    }

    @Nested
    class Cache {

        @Test
        void aFreshLimbCachesAsUninjured() {
            limb.rebuildCache();
            assertEquals(0.0D, limb.getCachedBleeding(), EPS);
            assertEquals(0.0F, limb.getCachedPain(), EPS);
            assertEquals(0.0F, limb.getCachedHealthReduction(), EPS);
            assertEquals(1.0F, limb.getCachedMovementMultiplier(), EPS);
            assertFalse(limb.hasCachedFracture());
        }

        @Test
        void theCacheSumsEveryTraumaOnTheLimb() {
            limb.addTrauma(trauma("laceration_large", 1.0F));  // bleed 1.2, pain 0.6, health 4.0
            limb.addTrauma(trauma("puncture", 1.0F));          // bleed 0.9, pain 0.5, health 3.5
            limb.rebuildCache();
            assertEquals(2.1D, limb.getCachedBleeding(), 1.0e-3D);
            assertEquals(1.1F, limb.getCachedPain(), 1.0e-3F);
            assertEquals(7.5F, limb.getCachedHealthReduction(), 1.0e-3F);
        }

        @Test
        void minorAndMajorHealthCostsLandInDifferentBuckets() {
            limb.addTrauma(trauma("blunt_force_trauma", 0.5F)); // minor, 12.0/severity
            limb.addTrauma(trauma("laceration_large", 0.5F));   // major, 4.0/severity
            limb.rebuildCache();
            assertEquals(2.0F, limb.getCachedHealthReduction(), EPS, "max-health cost is major-only");
            assertEquals(6.0F, limb.getCachedCurrentHealthReduction(), EPS, "current-health cost is minor-only");
        }

        @Test
        void movementModifiersOnlyApplyToLegs() {
            Limb leg = new Limb(LimbType.RIGHT_LEG);
            Limb arm = new Limb(LimbType.RIGHT_ARM);
            // crush_injury carries movementModifier 0.85.
            leg.addTrauma(new Trauma(registry.getOrThrow("crush_injury"), LimbType.RIGHT_LEG, 1.0F, 0L));
            arm.addTrauma(new Trauma(registry.getOrThrow("crush_injury"), LimbType.RIGHT_ARM, 1.0F, 0L));
            leg.rebuildCache();
            arm.rebuildCache();
            assertEquals(0.85F, leg.getCachedMovementMultiplier(), EPS);
            assertEquals(1.0F, arm.getCachedMovementMultiplier(), EPS, "an arm wound must not slow the player");
        }

        @Test
        void aStabilizedFractureClearsTheFractureFlag() {
            Trauma f = trauma("fracture", 1.0F);
            limb.addTrauma(f);
            limb.rebuildCache();
            assertTrue(limb.hasCachedFracture());
            f.setStabilized(true);
            limb.rebuildCache();
            assertFalse(limb.hasCachedFracture());
        }

        @Test
        void rebuildingClearsDirtyAndMutationSetsIt() {
            limb.rebuildCache();
            assertFalse(limb.isDirty());
            limb.addTrauma(trauma("bruise", 0.2F));
            assertTrue(limb.isDirty(), "adding a trauma must invalidate the cache");

            limb.rebuildCache();
            limb.setTourniquet(true);
            assertTrue(limb.isDirty());

            limb.rebuildCache();
            limb.setTourniquet(true);
            assertFalse(limb.isDirty(), "a no-op write must not dirty the limb");
        }
    }

    @Nested
    class Merging {

        @Test
        void aMergeableTraumaOfTheSameTypeCombinesRatherThanStacking() {
            limb.tryMerge(trauma("laceration_small", 0.3F), 8);
            limb.tryMerge(trauma("laceration_small", 0.4F), 8);
            assertEquals(1, limb.getTraumas().size());
            assertEquals(0.7F, limb.getTraumas().get(0).getSeverity(), EPS);
        }

        @Test
        void anUnmergeableTypeAlwaysStacks() {
            // fracture declares mergeable(false): two breaks stay two entries.
            limb.tryMerge(trauma("fracture", 0.5F), 8);
            limb.tryMerge(trauma("fracture", 0.5F), 8);
            assertEquals(2, limb.getTraumas().size());
        }

        @Test
        void aClosedWoundDoesNotAbsorbFreshDamage() {
            Trauma sutured = trauma("laceration_small", 0.3F);
            sutured.setClosed(true);
            limb.addTrauma(sutured);
            limb.tryMerge(trauma("laceration_small", 0.4F), 8);
            assertEquals(2, limb.getTraumas().size(), "a sutured wound must not silently reopen by merge");
            assertEquals(0.3F, sutured.getSeverity(), EPS);
        }

        @Test
        void mergingIntoAMaxedWoundStacksInsteadOfBeingLost() {
            limb.tryMerge(trauma("laceration_small", 1.0F), 8);
            limb.tryMerge(trauma("laceration_small", 0.5F), 8);
            assertEquals(2, limb.getTraumas().size(),
                    "a full-severity wound cannot absorb more, so the damage must land as a second wound");
        }
    }

    @Nested
    class Cap {

        @Test
        void theCapIsNeverExceeded() {
            for (int i = 0; i < 20; i++) {
                limb.tryMerge(trauma("fracture", 0.1F * (i + 1)), 3);
            }
            assertTrue(limb.getTraumas().size() <= 3,
                    "cap breached: " + limb.getTraumas().size());
        }

        @Test
        void aZeroOrNegativeCapMeansUnlimited() {
            for (int i = 0; i < 5; i++) {
                limb.tryMerge(trauma("fracture", 0.5F), 0);
            }
            assertEquals(5, limb.getTraumas().size());
        }

        @Test
        void evictedSeverityIsFoldedIntoAKeptWoundRatherThanDiscarded() {
            // Three unmergeable fractures, cap 2: the smallest is dropped, and its severity must survive.
            limb.tryMerge(trauma("fracture", 0.5F), 2);
            limb.tryMerge(trauma("fracture", 0.4F), 2);
            float before = totalSeverity();
            limb.tryMerge(trauma("fracture", 0.1F), 2);
            assertEquals(2, limb.getTraumas().size());
            assertTrue(totalSeverity() >= before,
                    "capping lost damage: " + totalSeverity() + " < " + before);
        }

        private float totalSeverity() {
            float s = 0.0F;
            for (Trauma t : limb.getTraumas()) {
                s += t.getSeverity();
            }
            return s;
        }
    }

    @Nested
    class Fields {

        @Test
        void localNumbingIsClampedToTheUnitRange() {
            limb.setLocalNumbing(5.0F);
            assertEquals(1.0F, limb.getLocalNumbing(), EPS);
            limb.setLocalNumbing(-1.0F);
            assertEquals(0.0F, limb.getLocalNumbing(), EPS);
        }

        @Test
        void minorDamageIsClampedNonNegative() {
            limb.setMinorDamage(-3.0F);
            assertEquals(0.0F, limb.getMinorDamage(), EPS);
        }

        @Test
        void removingATraumaReportsWhetherItWasThere() {
            Trauma t = trauma("bruise", 0.2F);
            assertFalse(limb.removeTrauma(t));
            limb.addTrauma(t);
            assertTrue(limb.removeTrauma(t));
            assertTrue(limb.getTraumas().isEmpty());
        }
    }

    @Nested
    class Persistence {

        @Test
        void aLimbSurvivesASaveLoadRoundTrip() {
            limb.setMaxHealth(14.0F);
            limb.setMinorDamage(2.5F);
            limb.setLocalNumbing(0.6F);
            limb.setTourniquet(true);
            limb.addTrauma(trauma("laceration_large", 0.7F));
            limb.addTrauma(trauma("fracture", 1.0F));

            CompoundTag tag = limb.save();
            Limb loaded = new Limb(LimbType.LEFT_LEG);
            loaded.load(tag, registry);

            assertEquals(14.0F, loaded.getMaxHealth(), EPS);
            assertEquals(2.5F, loaded.getMinorDamage(), EPS);
            assertEquals(0.6F, loaded.getLocalNumbing(), EPS);
            assertTrue(loaded.hasTourniquet());
            assertEquals(2, loaded.getTraumas().size());
            assertEquals("laceration_large", loaded.getTraumas().get(0).getType().getId());
            assertEquals(0.7F, loaded.getTraumas().get(0).getSeverity(), EPS);
        }

        @Test
        void aTraumaWhoseTypeNoLongerExistsIsDroppedNotFatal() {
            TraumaType gone = TraumaType.builder("mod_removed_wound",
                    com.warfactory.medical.core.trauma.TraumaCategory.LACERATION).build();
            limb.addTrauma(new Trauma(gone, LimbType.LEFT_LEG, 0.5F, 0L));
            limb.addTrauma(trauma("bruise", 0.3F));

            Limb loaded = new Limb(LimbType.LEFT_LEG);
            loaded.load(limb.save(), registry);

            assertEquals(1, loaded.getTraumas().size(),
                    "an unknown trauma id must be skipped so an uninstalled addon cannot corrupt a save");
            assertEquals("bruise", loaded.getTraumas().get(0).getType().getId());
        }

        @Test
        void loadingReplacesRatherThanAppendsAndLeavesTheLimbDirty() {
            limb.addTrauma(trauma("bruise", 0.3F));
            CompoundTag oneWound = limb.save();

            limb.addTrauma(trauma("puncture", 0.5F));
            limb.load(oneWound, registry);

            assertEquals(1, limb.getTraumas().size());
            assertTrue(limb.isDirty(), "a loaded limb must recompute before it is read");
        }

        @Test
        void loadedNumbingIsReclampedInCaseTheFileWasEdited() {
            CompoundTag tag = limb.save();
            tag.putFloat("LocalNumb", 99.0F);
            limb.load(tag, registry);
            assertEquals(1.0F, limb.getLocalNumbing(), EPS);
        }
    }

    @Test
    void theTypeIsFixedAtConstruction() {
        assertSame(LimbType.LEFT_LEG, limb.getType());
        assertNotNull(new Limb(LimbType.HEAD).getType());
        assertEquals(10.0F, new Limb(LimbType.HEAD).getMaxHealth(), EPS, "default limb pool");
    }
}
