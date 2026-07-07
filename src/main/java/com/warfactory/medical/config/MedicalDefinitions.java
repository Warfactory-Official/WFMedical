package com.warfactory.medical.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.LogUtils;
import com.warfactory.medical.core.substance.Substance;
import com.warfactory.medical.core.substance.SubstanceRegistry;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import com.warfactory.medical.core.treatment.Treatment;
import com.warfactory.medical.core.treatment.TreatmentAction;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Loads the data-driven trauma / treatment definitions from a TOML file (parsed with Forge's bundled
 * NightConfig). On first run the bundled classpath resource is copied into the config directory so
 * users can edit it.
 *
 * <p>Everything is defensive: missing keys fall back to sensible defaults, and if the file cannot be
 * read/parsed at all we fall back to {@link #loadDefaults} which builds the same
 * set programmatically. The populated registry is installed via {@link TraumaRegistry#setActive}.</p>
 */
public final class MedicalDefinitions {

    public static final String FILE_NAME = "wfmedical_definitions.toml";
    private static final Logger LOGGER = LogUtils.getLogger();

    private MedicalDefinitions() {
    }

    /**
     * Loads definitions from TOML (copying bundled defaults if missing), then activates both registries.
     */
    public static void load(Path configDir, TraumaRegistry registry, Map<String, Treatment> itemTreatments,
                            SubstanceRegistry substances) {
        registry.clear();
        itemTreatments.clear();
        substances.clear();

        Path file = configDir.resolve(FILE_NAME);
        try {
            if (!Files.exists(file)) {
                copyBundled(file);
            }
            if (Files.exists(file)) {
                parse(file, registry, itemTreatments, substances);
            }
        } catch (Exception e) {
            LOGGER.error("[wfmedical] Failed to load {} - using hardcoded defaults", FILE_NAME, e);
            registry.clear();
            itemTreatments.clear();
            substances.clear();
        }

        if (registry.size() == 0) {
            loadDefaults(registry, itemTreatments, substances);
        }
        // Injectables are independent of trauma; fall back to the hardcoded substances if none were parsed.
        if (substances.size() == 0) {
            substances.registerDefaults();
        }

        TraumaRegistry.setActive(registry);
        SubstanceRegistry.setActive(substances);
        LOGGER.info("[wfmedical] Loaded {} trauma types, {} treatments and {} substances",
                registry.size(), itemTreatments.size(), substances.size());
    }

    private static void copyBundled(Path target) throws Exception {
        Files.createDirectories(target.getParent());
        try (InputStream in = MedicalDefinitions.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in == null) {
                LOGGER.warn("[wfmedical] Bundled {} not found on classpath", FILE_NAME);
                return;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[wfmedical] Wrote default {} to {}", FILE_NAME, target);
        }
    }

    private static void parse(Path file, TraumaRegistry registry, Map<String, Treatment> itemTreatments,
                              SubstanceRegistry substances) throws Exception {
        CommentedConfig root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = TomlFormat.instance().createParser().parse(reader);
        }

        List<Config> traumaTables = getTableList(root, "trauma");
        for (Config t : traumaTables) {
            TraumaType type = readTrauma(t);
            if (type != null) {
                registry.register(type);
            }
        }

        List<Config> treatmentTables = getTableList(root, "treatment");
        for (Config t : treatmentTables) {
            String item = str(t, "item", null);
            Treatment treatment = readTreatment(t);
            if (item != null && treatment != null) {
                itemTreatments.put(item, treatment);
            }
        }

        List<Config> substanceTables = getTableList(root, "substance");
        for (Config t : substanceTables) {
            Substance substance = readSubstance(t);
            if (substance != null) {
                substances.register(substance);
            }
        }
    }

    private static TraumaType readTrauma(Config t) {
        String id = str(t, "id", null);
        if (id == null || id.isEmpty()) {
            return null;
        }
        TraumaCategory category = TraumaCategory.byName(str(t, "category", null), TraumaCategory.BRUISE);

        TraumaType.Builder b = TraumaType.builder(id, category)
                .major(bool(t, "major", category.isMajorByDefault()))
                .severityContribution(flt(t, "severityContribution", 1.0F))
                .painPerSeverity(flt(t, "painPerSeverity", 0.0F))
                .bleedingPerSeverity(flt(t, "bleedingPerSeverity", 0.0F))
                .healSpeedPerTick(flt(t, "healSpeedPerTick", 0.0F))
                .canReopen(bool(t, "canReopen", false))
                .permanent(bool(t, "permanent", false))
                .movementModifier(flt(t, "movementModifier", 1.0F))
                .healthReductionPerSeverity(flt(t, "healthReductionPerSeverity", 0.0F))
                .maxSeverity(flt(t, "maxSeverity", 1.0F))
                .mergeable(bool(t, "mergeable", true));

        for (String actionName : strList(t, "treatmentActions")) {
            TreatmentAction action = parseAction(actionName);
            if (action != null) {
                b.treatment(action);
            }
        }
        return b.build();
    }

    private static Treatment readTreatment(Config t) {
        TreatmentAction action = parseAction(str(t, "action", null));
        if (action == null) {
            return null;
        }
        Set<TraumaCategory> categories = EnumSet.noneOf(TraumaCategory.class);
        for (String catName : strList(t, "categories")) {
            TraumaCategory c = TraumaCategory.byName(catName, null);
            if (c != null) {
                categories.add(c);
            }
        }
        return new Treatment(
                action,
                categories,
                flt(t, "magnitude", 0.0F),
                dbl(t, "bloodRestoreMl", 0.0D),
                intOf(t, "useDurationTicks", 20),
                bool(t, "removesTrauma", false)
        );
    }

    private static Substance readSubstance(Config t) {
        String id = str(t, "id", null);
        String item = str(t, "item", null);
        if (id == null || id.isEmpty() || item == null || item.isEmpty()) {
            return null;
        }
        return new Substance(
                id,
                item,
                flt(t, "painSuppression", 0.0F),
                flt(t, "doseLoad", 0.0F),
                flt(t, "overdoseThreshold", 1.0F),
                intOf(t, "unconsciousTicks", intOf(t, "blackoutTicks", 200)),
                flt(t, "lethalThreshold", 0.0F),
                bool(t, "antidote", false),
                flt(t, "reversalAmount", 0.0F),
                intOf(t, "useDurationTicks", 40),
                dbl(t, "bloodRestoreMl", 0.0D),
                flt(t, "clottingBoost", 0.0F),
                flt(t, "stimulantStrength", 0.0F),
                intOf(t, "effectTicks", 0)
        );
    }

    // ---------------------------------------------------------------------
    // Hardcoded fallback that mirrors wfmedical_definitions.toml exactly.
    // ---------------------------------------------------------------------

    /**
     * Populates the SAME definitions as the bundled TOML, programmatically (IO-free safety net).
     */
    public static void loadDefaults(TraumaRegistry registry, Map<String, Treatment> itemTreatments,
                                    SubstanceRegistry substances) {
        registry.register(TraumaType.builder("bruise", TraumaCategory.BRUISE)
                .major(false).severityContribution(0.3F).painPerSeverity(0.15F).bleedingPerSeverity(0.0F)
                .healSpeedPerTick(0.0008F).canReopen(false).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(0.0F).maxSeverity(1.0F).mergeable(true)
                .treatments(TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("laceration_small", TraumaCategory.LACERATION)
                .major(false).severityContribution(0.4F).painPerSeverity(0.2F).bleedingPerSeverity(0.4F)
                .healSpeedPerTick(0.0004F).canReopen(true).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(1.0F).maxSeverity(1.0F).mergeable(true)
                .treatments(TreatmentAction.REDUCE_BLEEDING, TreatmentAction.SUTURE_WOUND, TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("laceration_large", TraumaCategory.LACERATION)
                .major(true).severityContribution(0.8F).painPerSeverity(0.6F).bleedingPerSeverity(1.2F)
                .healSpeedPerTick(0.0001F).canReopen(true).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(4.0F).maxSeverity(1.0F).mergeable(true)
                .treatments(TreatmentAction.REDUCE_BLEEDING, TreatmentAction.SUTURE_WOUND, TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("fracture", TraumaCategory.FRACTURE)
                .major(true).severityContribution(0.9F).painPerSeverity(0.5F).bleedingPerSeverity(0.0F)
                // movementModifier stays 1.0: the leg-fracture speed penalty is applied once in Physiology
                // via legFractureSpeedMultiplier, and only for legs (an arm fracture must not slow walking).
                .healSpeedPerTick(0.00005F).canReopen(false).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(3.0F).maxSeverity(1.0F).mergeable(false)
                .treatments(TreatmentAction.STABILIZE_FRACTURE, TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("burn", TraumaCategory.BURN)
                .major(true).severityContribution(0.6F).painPerSeverity(0.55F).bleedingPerSeverity(0.1F)
                .healSpeedPerTick(0.0002F).canReopen(false).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(3.0F).maxSeverity(1.0F).mergeable(true)
                .treatments(TreatmentAction.TREAT_BURN, TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("internal_bleeding", TraumaCategory.INTERNAL_BLEEDING)
                .major(true).severityContribution(0.9F).painPerSeverity(0.4F).bleedingPerSeverity(2.0F)
                .healSpeedPerTick(0.0F).canReopen(false).permanent(true).movementModifier(1.0F)
                .healthReductionPerSeverity(5.0F).maxSeverity(1.0F).mergeable(true)
                .treatments(TreatmentAction.REDUCE_BLEEDING, TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("puncture", TraumaCategory.PUNCTURE)
                .major(true).severityContribution(0.7F).painPerSeverity(0.5F).bleedingPerSeverity(0.9F)
                .healSpeedPerTick(0.0002F).canReopen(true).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(3.5F).maxSeverity(1.0F).mergeable(true)
                .treatments(TreatmentAction.REDUCE_BLEEDING, TreatmentAction.SUTURE_WOUND, TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("crush_injury", TraumaCategory.CRUSH_INJURY)
                .major(true).severityContribution(0.8F).painPerSeverity(0.6F).bleedingPerSeverity(0.3F)
                .healSpeedPerTick(0.00008F).canReopen(false).permanent(false).movementModifier(0.85F)
                .healthReductionPerSeverity(4.0F).maxSeverity(1.0F).mergeable(true)
                .treatments(TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("radiation_burn", TraumaCategory.RADIATION_BURN)
                .major(true).severityContribution(0.7F).painPerSeverity(0.45F).bleedingPerSeverity(0.0F)
                .healSpeedPerTick(0.0F).canReopen(false).permanent(true).movementModifier(1.0F)
                .healthReductionPerSeverity(4.0F).maxSeverity(1.0F).mergeable(true)
                .treatments(TreatmentAction.TREAT_RADIATION, TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("chemical_burn", TraumaCategory.CHEMICAL_BURN)
                .major(true).severityContribution(0.65F).painPerSeverity(0.5F).bleedingPerSeverity(0.2F)
                .healSpeedPerTick(0.0001F).canReopen(false).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(3.5F).maxSeverity(1.0F).mergeable(true)
                .treatments(TreatmentAction.TREAT_BURN, TreatmentAction.HEAL_TRAUMA).build());

        itemTreatments.put("wfmedical:bandage", new Treatment(TreatmentAction.REDUCE_BLEEDING,
                EnumSet.of(TraumaCategory.LACERATION, TraumaCategory.PUNCTURE), 0.5F, 0.0D, 40, false));
        itemTreatments.put("wfmedical:splint", new Treatment(TreatmentAction.STABILIZE_FRACTURE,
                EnumSet.of(TraumaCategory.FRACTURE), 1.0F, 0.0D, 60, false));
        itemTreatments.put("wfmedical:suture_kit", new Treatment(TreatmentAction.SUTURE_WOUND,
                EnumSet.of(TraumaCategory.LACERATION, TraumaCategory.PUNCTURE), 1.0F, 0.0D, 100, false));
        itemTreatments.put("wfmedical:blood_bag", new Treatment(TreatmentAction.RESTORE_BLOOD,
                Collections.emptySet(), 0.0F, 1000.0D, 120, false));
        itemTreatments.put("wfmedical:painkillers", new Treatment(TreatmentAction.REDUCE_PAIN,
                Collections.emptySet(), 0.5F, 0.0D, 30, false));
        itemTreatments.put("wfmedical:local_anesthetic", new Treatment(TreatmentAction.NUMB_LIMB,
                Collections.emptySet(), 0.9F, 0.0D, 50, false));
        itemTreatments.put("wfmedical:hemostatic", new Treatment(TreatmentAction.BOOST_CLOTTING,
                Collections.emptySet(), 0.6F, 0.0D, 50, false));
        itemTreatments.put("wfmedical:tourniquet", new Treatment(TreatmentAction.APPLY_TOURNIQUET,
                Collections.emptySet(), 0.0F, 0.0D, 20, false));
        itemTreatments.put("wfmedical:medkit", new Treatment(TreatmentAction.HEAL_TRAUMA,
                Collections.emptySet(), 1.0F, 250.0D, 160, true));
        itemTreatments.put("wfmedical:burn_ointment", new Treatment(TreatmentAction.TREAT_BURN,
                EnumSet.of(TraumaCategory.BURN, TraumaCategory.CHEMICAL_BURN), 0.8F, 0.0D, 80, false));
        itemTreatments.put("wfmedical:antirad_shot", new Treatment(TreatmentAction.TREAT_RADIATION,
                EnumSet.of(TraumaCategory.RADIATION_BURN), 1.0F, 0.0D, 40, true));

        // Injectable substances (mirror the bundled TOML [[substance]] tables).
        substances.register(SubstanceRegistry.defaultMorphine());
        substances.register(SubstanceRegistry.defaultNaloxone());
        substances.register(SubstanceRegistry.defaultCombatStimulant());
    }

    // ---------------------------------------------------------------------
    // NightConfig read helpers (tolerant of missing / wrong-typed keys).
    // ---------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<Config> getTableList(Config root, String key) {
        Object o = root.get(Collections.singletonList(key));
        if (o instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Config) {
            return (List<Config>) list;
        }
        return Collections.emptyList();
    }

    private static TreatmentAction parseAction(String name) {
        if (name == null) {
            return null;
        }
        try {
            return TreatmentAction.valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[wfmedical] Unknown treatment action: {}", name);
            return null;
        }
    }

    private static Object raw(Config c, String key) {
        return c.get(Collections.singletonList(key));
    }

    private static float flt(Config c, String key, float def) {
        Object o = raw(c, key);
        return o instanceof Number n ? n.floatValue() : def;
    }

    private static double dbl(Config c, String key, double def) {
        Object o = raw(c, key);
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private static int intOf(Config c, String key, int def) {
        Object o = raw(c, key);
        return o instanceof Number n ? n.intValue() : def;
    }

    private static boolean bool(Config c, String key, boolean def) {
        Object o = raw(c, key);
        return o instanceof Boolean b ? b : def;
    }

    private static String str(Config c, String key, String def) {
        Object o = raw(c, key);
        return o instanceof String s ? s : def;
    }

    private static List<String> strList(Config c, String key) {
        Object o = raw(c, key);
        if (o instanceof List<?> list) {
            java.util.ArrayList<String> out = new java.util.ArrayList<>(list.size());
            for (Object e : list) {
                if (e instanceof String s) {
                    out.add(s);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
