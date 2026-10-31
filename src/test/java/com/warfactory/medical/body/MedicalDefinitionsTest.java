package com.warfactory.medical.body;

import com.warfactory.medical.substance.Substance;
import com.warfactory.medical.substance.Substances;
import com.warfactory.medical.body.trauma.TraumaCategory;
import com.warfactory.medical.body.trauma.TraumaTypes;
import com.warfactory.medical.body.trauma.TraumaResponse;
import com.warfactory.medical.body.trauma.TraumaType;
import com.warfactory.medical.treatment.Treatment;
import com.warfactory.medical.body.trauma.TreatmentAction;
import com.warfactory.medical.support.Fixtures;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.warfactory.medical.treatment.TreatmentDefinitions;
import com.warfactory.medical.substance.SubstanceDefinitions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The data-driven definition file: whether a pack can actually retune wounds, treatments and drugs. */
class MedicalDefinitionsTest {

    private TraumaTypes.Table registry;
    private Map<String, Treatment> treatments;
    private List<Substance> substances;

    private void load(Path dir) {
        registry = MedicalDefinitions.traumas(dir);
        treatments = TreatmentDefinitions.load(dir);
        substances = SubstanceDefinitions.load(dir);
    }

    private void loadToml(Path dir, String body) throws IOException {
        Files.writeString(dir.resolve(MedicalDefinitions.FILE_NAME), body, StandardCharsets.UTF_8);
        load(dir);
    }

    private Substance substance(String item) {
        return substances.stream().filter(s -> s.itemId().equals(item)).findFirst().orElse(null);
    }

    @Nested
    class Defaults {

        @Test
        void theHardcodedDefaultsCoverEveryWoundTheGeneratorCanEmit() {
            TraumaTypes.Table r = Fixtures.registry();
            for (String id : new String[]{"bruise", "blunt_force_trauma", "laceration_small",
                    "laceration_large", "fracture", "burn", "internal_bleeding", "puncture",
                    "crush_injury", "radiation_burn", "chemical_burn"}) {
                assertNotNull(r.get(id), "TraumaGenerator emits '" + id + "' but nothing defines it");
            }
        }

        @Test
        void everyShippedItemHasATreatment() {
            Map<String, Treatment> t = Fixtures.treatments();
            for (String id : new String[]{"wfmedical:bandage", "wfmedical:splint", "wfmedical:suture_kit",
                    "wfmedical:blood_bag", "wfmedical:painkillers", "wfmedical:local_anesthetic",
                    "wfmedical:hemostatic", "wfmedical:tourniquet", "wfmedical:medkit",
                    "wfmedical:burn_ointment", "wfmedical:antirad_shot"}) {
                assertNotNull(t.get(id), "no treatment defined for " + id);
            }
        }

        @Test
        void everyDefaultTreatmentHasSomeWoundThatAnswersIt() {
            TraumaTypes.Table r = Fixtures.registry();
            for (Map.Entry<String, Treatment> e : Fixtures.treatments().entrySet()) {
                TreatmentAction action = e.getValue().action();
                if (action.isGlobal() || action == TreatmentAction.APPLY_TOURNIQUET
                        || action == TreatmentAction.NUMB_LIMB) {
                    continue; // these act on the player, not on a wound
                }
                assertTrue(r.all().stream().anyMatch(t -> t.respondsTo(action)),
                        e.getKey() + " (" + action + ") would be a no-op on every shipped wound");
            }
        }

        @Test
        void theDefaultSubstancesAreTheThreeSyringes() {
            List<Substance> s = Fixtures.substances();
            assertEquals(List.of(Substances.MORPHINE.toString(), Substances.NALOXONE.toString(),
                    Substances.COMBAT_STIMULANT.toString()), s.stream().map(Substance::itemId).toList());
        }
    }

    @Nested
    class Loading {

        @Test
        void theShippedTreatmentTablesEqualTheCodeDefaults(@TempDir Path dir) {
            load(dir);
            assertEquals(TreatmentDefinitions.defaultTreatments(), treatments);
        }

        @Test
        void anEditedTreatmentValueLandsInTheActiveTreatmentMap(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[treatment]]
                    item = "wfmedical:bandage"
                    action = "REDUCE_BLEEDING"
                    categories = ["LACERATION"]
                    magnitude = 0.77
                    useDurationTicks = 7
                    """);
            Treatment t = treatments.get("wfmedical:bandage");
            assertEquals(0.77F, t.magnitude(), 1.0e-6F);
            assertEquals(7, t.useDurationTicks());
        }

        @Test
        void aMissingFileIsWrittenFromTheBundledCopyAndThenParsed(@TempDir Path dir) {
            load(dir);
            assertTrue(Files.exists(dir.resolve(MedicalDefinitions.FILE_NAME)),
                    "the default definitions should be written out so a pack can edit them");
            assertTrue(registry.size() > 0);
        }

        @Test
        void anEmptyFileFallsBackToTheHardcodedDefaults(@TempDir Path dir) throws IOException {
            loadToml(dir, "");
            assertTrue(registry.size() > 0, "an empty file must not leave a player with no wound types");
            assertNotNull(registry.get("laceration_large"));
            assertEquals(3, substances.size());
        }

        @Test
        void aMalformedFileFallsBackRatherThanCrashingTheServer(@TempDir Path dir) throws IOException {
            loadToml(dir, "this is not [ valid toml at all ===");
            assertTrue(registry.size() > 0);
            assertNotNull(registry.get("fracture"));
        }

        @Test
        void aTableTheFileOmitsTakesItsDefaults(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[trauma]]
                    id = "only_wound"
                    category = "LACERATION"
                    """);
            assertEquals(1, registry.size());
            assertEquals(TreatmentDefinitions.defaultTreatments(), treatments);
            assertEquals(3, substances.size());
        }

        @Test
        void aDuplicateKeyDiscardsThatTableForTheDefaults(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[trauma]]
                    id = "twice"
                    category = "BURN"

                    [[trauma]]
                    id = "twice"
                    category = "BURN"

                    [[treatment]]
                    item = "mypack:a"
                    action = "HEAL_TRAUMA"

                    [[treatment]]
                    item = "mypack:a"
                    action = "HEAL_TRAUMA"

                    [[substance]]
                    id = "x"
                    item = "mypack:x"

                    [[substance]]
                    id = "y"
                    item = "mypack:x"
                    """);
            assertNull(registry.get("twice"));
            assertNotNull(registry.get("fracture"));
            assertEquals(TreatmentDefinitions.defaultTreatments(), treatments);
            assertNull(substance("mypack:x"));
            assertEquals(3, substances.size());
        }
    }

    @Nested
    class TraumaParsing {

        @Test
        void everyDeclaredFieldIsRead(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[trauma]]
                    id = "test_wound"
                    category = "PUNCTURE"
                    major = false
                    severityContribution = 0.42
                    painPerSeverity = 0.7
                    bleedingPerSeverity = 1.9
                    healSpeedPerTick = 0.005
                    canReopen = true
                    permanent = true
                    movementModifier = 0.6
                    healthReductionPerSeverity = 8.5
                    maxSeverity = 2.5
                    mergeable = false
                    """);
            TraumaType t = registry.getOrThrow("test_wound");
            assertEquals(TraumaCategory.PUNCTURE, t.getCategory());
            assertFalse(t.isMajor(), "an explicit major=false must beat the category default");
            assertEquals(0.42F, t.getSeverityContribution(), 1.0e-5F);
            assertEquals(0.7F, t.getPainPerSeverity(), 1.0e-5F);
            assertEquals(1.9F, t.getBleedingPerSeverity(), 1.0e-5F);
            assertEquals(0.005F, t.getHealSpeedPerTick(), 1.0e-6F);
            assertTrue(t.canReopen());
            assertTrue(t.isPermanent());
            assertEquals(0.6F, t.getMovementModifier(), 1.0e-5F);
            assertEquals(8.5F, t.getHealthReductionPerSeverity(), 1.0e-5F);
            assertEquals(2.5F, t.getMaxSeverity(), 1.0e-5F);
            assertFalse(t.isMergeable());
        }

        @Test
        void omittedFieldsTakeTheirDefaultsAndTheCategoryDecidesMajor(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[trauma]]
                    id = "sparse"
                    category = "LACERATION"
                    """);
            TraumaType t = registry.getOrThrow("sparse");
            assertTrue(t.isMajor(), "LACERATION is major by default");
            assertEquals(1.0F, t.getMaxSeverity(), 1.0e-5F);
            assertEquals(1.0F, t.getMovementModifier(), 1.0e-5F);
            assertTrue(t.isMergeable());
        }

        @Test
        void anEntryWithNoIdIsSkippedRatherThanRegisteredBlank(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[trauma]]
                    category = "BURN"

                    [[trauma]]
                    id = "good"
                    category = "BURN"
                    """);
            assertEquals(1, registry.size());
            assertNotNull(registry.get("good"));
        }

        @Test
        void anUnknownCategoryFallsBackToBruise(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[trauma]]
                    id = "weird"
                    category = "NOT_A_CATEGORY"
                    """);
            assertEquals(TraumaCategory.BRUISE, registry.getOrThrow("weird").getCategory());
        }
    }

    @Nested
    class ResponseParsing {

        private TraumaType withActions(Path dir, String actions) throws IOException {
            loadToml(dir, """
                    [[trauma]]
                    id = "w"
                    category = "LACERATION"
                    treatmentActions = [%s]
                    """.formatted(actions));
            return registry.getOrThrow("w");
        }

        @Test
        void aBareActionUsesItsDefaultResponse(@TempDir Path dir) throws IOException {
            TraumaResponse r = withActions(dir, "\"REDUCE_BLEEDING\"").response(TreatmentAction.REDUCE_BLEEDING);
            assertEquals(TraumaResponse.Effect.REDUCE_BLEED, r.effect());
            assertEquals(0.25F, r.factor(), 1.0e-5F);
        }

        @Test
        void anExplicitEffectOverridesTheDefault(@TempDir Path dir) throws IOException {
            TraumaResponse r = withActions(dir, "\"REDUCE_BLEEDING=STOP\"")
                    .response(TreatmentAction.REDUCE_BLEEDING);
            assertEquals(TraumaResponse.Effect.STOP_BLEED, r.effect());
        }

        @Test
        void aFactorSuffixSetsTheResidualBleed(@TempDir Path dir) throws IOException {
            TraumaResponse r = withActions(dir, "\"REDUCE_BLEEDING=REDUCE:0.6\"")
                    .response(TreatmentAction.REDUCE_BLEEDING);
            assertEquals(TraumaResponse.Effect.REDUCE_BLEED, r.effect());
            assertEquals(0.6F, r.factor(), 1.0e-5F);
        }

        @Test
        void everyEffectAliasIsAccepted(@TempDir Path dir) throws IOException {
            TraumaType t = withActions(dir, """
                    "REDUCE_BLEEDING=stop_bleed", "SUTURE_WOUND=suture",
                    "STABILIZE_FRACTURE=Stabilize", "HEAL_TRAUMA=HEAL", "BOOST_CLOTTING=reduce:0.1"
                    """);
            assertEquals(TraumaResponse.Effect.STOP_BLEED, t.response(TreatmentAction.REDUCE_BLEEDING).effect());
            assertEquals(TraumaResponse.Effect.SUTURE, t.response(TreatmentAction.SUTURE_WOUND).effect());
            assertEquals(TraumaResponse.Effect.STABILIZE,
                    t.response(TreatmentAction.STABILIZE_FRACTURE).effect());
            assertEquals(TraumaResponse.Effect.HEAL, t.response(TreatmentAction.HEAL_TRAUMA).effect());
            assertEquals(0.1F, t.response(TreatmentAction.BOOST_CLOTTING).factor(), 1.0e-5F);
        }

        @Test
        void anUnknownEffectFallsBackToTheActionsDefault(@TempDir Path dir) throws IOException {
            TraumaResponse r = withActions(dir, "\"SUTURE_WOUND=TELEPORT\"")
                    .response(TreatmentAction.SUTURE_WOUND);
            assertEquals(TraumaResponse.Effect.SUTURE, r.effect(),
                    "a typo in a pack must degrade to the sane default, not drop the treatment");
        }

        @Test
        void anUnparseableFactorKeepsTheDefaultFactor(@TempDir Path dir) throws IOException {
            TraumaResponse r = withActions(dir, "\"REDUCE_BLEEDING=REDUCE:banana\"")
                    .response(TreatmentAction.REDUCE_BLEEDING);
            assertEquals(0.25F, r.factor(), 1.0e-5F);
        }

        @Test
        void anUnknownActionIsSkipped(@TempDir Path dir) throws IOException {
            assertTrue(withActions(dir, "\"NOT_AN_ACTION\", \"\"").getResponses().isEmpty());
        }
    }

    @Nested
    class TreatmentAndSubstanceParsing {

        @Test
        void aTreatmentEntryBindsAnItemToAnAction(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[treatment]]
                    item = "mypack:field_dressing"
                    action = "REDUCE_BLEEDING"
                    categories = ["LACERATION", "PUNCTURE", "NOT_A_CATEGORY"]
                    magnitude = 0.75
                    bloodRestoreMl = 120.0
                    useDurationTicks = 33
                    removesTrauma = true
                    """);
            Treatment t = treatments.get("mypack:field_dressing");
            assertNotNull(t);
            assertEquals(TreatmentAction.REDUCE_BLEEDING, t.action());
            assertEquals(0.75F, t.magnitude(), 1.0e-5F);
            assertEquals(120.0D, t.bloodRestoreMl(), 1.0e-6D);
            assertEquals(33, t.useDurationTicks());
            assertTrue(t.removesTrauma());
            assertEquals(2, t.applicableCategories().size(), "the bogus category must be dropped, not fatal");
            assertTrue(t.appliesTo(TraumaCategory.LACERATION));
            assertFalse(t.appliesTo(TraumaCategory.BURN));
        }

        @Test
        void aTreatmentWithNoItemOrNoActionIsSkipped(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[trauma]]
                    id = "keeps_the_file_non_empty"
                    category = "LACERATION"

                    [[treatment]]
                    action = "HEAL_TRAUMA"

                    [[treatment]]
                    item = "mypack:mystery"
                    action = "NOT_AN_ACTION"

                    [[treatment]]
                    item = "mypack:good"
                    action = "HEAL_TRAUMA"
                    """);
            assertEquals(1, treatments.size(), "only the well-formed entry should survive");
            assertNotNull(treatments.get("mypack:good"));
            assertNull(treatments.get("mypack:mystery"));
        }

        @Test
        void aSubstanceEntryIsReadWithItsLegacyBlackoutAlias(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[substance]]
                    id = "test_opioid"
                    item = "mypack:syrette"
                    painSuppression = 0.8
                    doseLoad = 0.4
                    overdoseThreshold = 1.2
                    blackoutTicks = 321
                    lethalThreshold = 2.0
                    antidote = false
                    useDurationTicks = 25
                    clottingBoost = 0.1
                    stimulantStrength = 0.2
                    effectTicks = 600
                    """);
            Substance s = substance("mypack:syrette");
            assertNotNull(s);
            assertEquals("test_opioid", s.id());
            assertEquals(0.8F, s.painSuppression(), 1.0e-5F);
            assertEquals(0.4F, s.doseLoad(), 1.0e-5F);
            assertEquals(1.2F, s.overdoseThreshold(), 1.0e-5F);
            assertEquals(321, s.unconsciousTicks(),
                    "pre-rename packs wrote blackoutTicks; dropping the alias would silently zero it");
            assertEquals(2.0F, s.lethalThreshold(), 1.0e-5F);
            assertEquals(25, s.useDurationTicks());
            assertEquals(600, s.effectTicks());
        }

        @Test
        void unconsciousTicksWinsOverTheAliasWhenBothArePresent(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[substance]]
                    id = "x"
                    item = "mypack:x"
                    unconsciousTicks = 111
                    blackoutTicks = 999
                    """);
            assertEquals(111, substance("mypack:x").unconsciousTicks());
        }

        @Test
        void aSubstanceWithNoIdOrItemIsSkipped(@TempDir Path dir) throws IOException {
            loadToml(dir, """
                    [[substance]]
                    id = "no_item"

                    [[substance]]
                    item = "mypack:no_id"
                    """);
            assertEquals(3, substances.size());
            assertNull(substance("mypack:no_id"));
        }
    }
}
