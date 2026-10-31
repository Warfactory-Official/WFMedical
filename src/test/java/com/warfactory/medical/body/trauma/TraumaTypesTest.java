package com.warfactory.medical.body.trauma;

import com.warfactory.medical.support.Fixtures;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraumaTypesTest {

    @Nested
    class Table {

        @Test
        void registeringMakesATypeFindableById() {
            TraumaTypes.Table r = new TraumaTypes.Table();
            TraumaType t = TraumaType.builder("x", TraumaCategory.BRUISE).build();
            assertSame(t, r.register(t));
            assertSame(t, r.get("x"));
            assertSame(t, r.get("wfmedical:x"), "a bare id is the wfmedical namespace");
            assertEquals(1, r.size());
        }

        @Test
        void anUnknownIdIsNullOrThrowsDependingOnTheAccessor() {
            TraumaTypes.Table r = new TraumaTypes.Table();
            assertNull(r.get("nope"));
            assertThrows(IllegalArgumentException.class, () -> r.getOrThrow("nope"));
        }

        @Test
        void aDuplicateIdThrows() {
            TraumaTypes.Table r = new TraumaTypes.Table();
            r.register(TraumaType.builder("x", TraumaCategory.BRUISE).build());
            assertThrows(IllegalArgumentException.class,
                    () -> r.register(TraumaType.builder("wfmedical:x", TraumaCategory.BURN).build()));
            assertEquals(1, r.size());
        }

        @Test
        void firstOfCategoryIsTheFallbackTraumaGeneratorLeansOn() {
            TraumaTypes.Table r = Fixtures.registry();
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
        void theCollectionViewIsUnmodifiable() {
            TraumaTypes.Table r = Fixtures.registry();
            assertThrows(UnsupportedOperationException.class, () -> r.all().clear());
        }
    }

    @Nested
    class Live {

        @Test
        void theModLoadedTheDefinitionsAndFroze() {
            assertNotNull(TraumaTypes.LIVE.get("puncture"));
            assertTrue(TraumaTypes.TYPES.isFrozen());
            assertThrows(IllegalStateException.class, () -> TraumaTypes.TYPES.register(TraumaTypes.key("late"),
                    TraumaType.builder("late", TraumaCategory.BRUISE).build()));
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
