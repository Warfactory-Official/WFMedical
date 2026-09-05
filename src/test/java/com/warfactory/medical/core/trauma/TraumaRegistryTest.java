package com.warfactory.medical.core.trauma;

import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.support.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraumaRegistryTest {

    @AfterEach
    void restoreActive() {
        // The active registry is a process-wide static; leaving a test one installed would leak into the
        // rest of the suite (Trauma.load and MedicalData.load both read it).
        TraumaRegistry.setActive(Fixtures.registry());
    }

    @Nested
    class Lookup {

        @Test
        void registeringMakesATypeFindableById() {
            TraumaRegistry r = new TraumaRegistry();
            TraumaType t = TraumaType.builder("x", TraumaCategory.BRUISE).build();
            assertSame(t, r.register(t));
            assertSame(t, r.get("x"));
            assertTrue(r.contains("x"));
            assertEquals(1, r.size());
        }

        @Test
        void anUnknownIdIsNullOrThrowsDependingOnTheAccessor() {
            TraumaRegistry r = new TraumaRegistry();
            assertNull(r.get("nope"));
            assertFalse(r.contains("nope"));
            assertThrows(IllegalArgumentException.class, () -> r.getOrThrow("nope"));
        }

        @Test
        void reRegisteringAnIdReplacesRatherThanDuplicates() {
            TraumaRegistry r = new TraumaRegistry();
            r.register(TraumaType.builder("x", TraumaCategory.BRUISE).painPerSeverity(1.0F).build());
            r.register(TraumaType.builder("x", TraumaCategory.BURN).painPerSeverity(2.0F).build());
            assertEquals(1, r.size());
            assertEquals(TraumaCategory.BURN, r.get("x").getCategory());
        }

        @Test
        void firstOfCategoryIsTheFallbackTraumaGeneratorLeansOn() {
            TraumaRegistry r = Fixtures.registry();
            // TraumaGenerator asks by id first and falls back to the category, so every category the
            // generator can emit must resolve to something or a wound is silently dropped.
            for (TraumaCategory c : new TraumaCategory[]{
                    TraumaCategory.BRUISE, TraumaCategory.LACERATION, TraumaCategory.FRACTURE,
                    TraumaCategory.BURN, TraumaCategory.INTERNAL_BLEEDING, TraumaCategory.PUNCTURE,
                    TraumaCategory.CRUSH_INJURY, TraumaCategory.RADIATION_BURN, TraumaCategory.CHEMICAL_BURN}) {
                assertNotNull(r.firstOfCategory(c), "no shipped trauma type for category " + c);
            }
        }

        @Test
        void clearingEmptiesTheRegistry() {
            TraumaRegistry r = Fixtures.registry();
            assertTrue(r.size() > 0);
            r.clear();
            assertEquals(0, r.size());
            assertTrue(r.all().isEmpty());
        }

        @Test
        void theCollectionViewIsUnmodifiable() {
            TraumaRegistry r = Fixtures.registry();
            assertThrows(UnsupportedOperationException.class, () -> r.all().clear());
        }
    }

    @Nested
    class Active {

        @Test
        void settingNullRestoresAnEmptyRegistryRatherThanLeavingNull() {
            TraumaRegistry.setActive(null);
            assertNotNull(TraumaRegistry.active(), "active() must never return null; loaders call it blindly");
            assertEquals(0, TraumaRegistry.active().size());
        }

        @Test
        void theActiveRegistryIsWhatWasInstalled() {
            TraumaRegistry mine = new TraumaRegistry();
            TraumaRegistry.setActive(mine);
            assertSame(mine, TraumaRegistry.active());
        }
    }

    @Nested
    class Builder {

        @Test
        void theCategoryDecidesMajorByDefault() {
            assertTrue(TraumaType.builder("a", TraumaCategory.LACERATION).build().isMajor());
            assertFalse(TraumaType.builder("b", TraumaCategory.BRUISE).build().isMajor());
            assertFalse(TraumaType.builder("c", TraumaCategory.LACERATION).major(false).build().isMajor());
        }

        @Test
        void theDefaultsAreTheHarmlessOnes() {
            TraumaType t = TraumaType.builder("d", TraumaCategory.BRUISE).build();
            assertEquals(0.0F, t.getPainPerSeverity());
            assertEquals(0.0F, t.getBleedingPerSeverity());
            assertEquals(0.0F, t.getHealthReductionPerSeverity());
            assertEquals(1.0F, t.getMovementModifier());
            assertEquals(1.0F, t.getMaxSeverity());
            assertEquals(1.0F, t.getSeverityContribution());
            assertTrue(t.isMergeable());
            assertFalse(t.isPermanent());
            assertFalse(t.canReopen());
            assertTrue(t.getResponses().isEmpty());
        }

        @Test
        void anExplicitResponseOverridesTheActionDefault() {
            TraumaType t = TraumaType.builder("e", TraumaCategory.LACERATION)
                    .treatment(TreatmentAction.REDUCE_BLEEDING)
                    .response(new TraumaResponse(TreatmentAction.REDUCE_BLEEDING,
                            TraumaResponse.Effect.STOP_BLEED, 0.0F))
                    .build();
            assertEquals(TraumaResponse.Effect.STOP_BLEED,
                    t.response(TreatmentAction.REDUCE_BLEEDING).effect());
        }

        @Test
        void anActionWithNoDefaultResponseIsSimplyNotRegistered() {
            // APPLY_TOURNIQUET / RESTORE_BLOOD have no per-wound effect, so a bare entry adds nothing.
            TraumaType t = TraumaType.builder("f", TraumaCategory.LACERATION)
                    .treatments(TreatmentAction.APPLY_TOURNIQUET, TreatmentAction.RESTORE_BLOOD)
                    .build();
            assertTrue(t.getResponses().isEmpty());
        }

        @Test
        void theResponseMapIsUnmodifiable() {
            TraumaType t = TraumaType.builder("g", TraumaCategory.LACERATION)
                    .treatment(TreatmentAction.SUTURE_WOUND).build();
            assertThrows(UnsupportedOperationException.class, () -> t.getResponses().clear());
        }
    }
}
