package com.warfactory.medical.body;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.LogUtils;
import com.warfactory.medical.body.trauma.TraumaCategory;
import com.warfactory.medical.body.trauma.TraumaTypes;
import com.warfactory.medical.body.trauma.TraumaResponse;
import com.warfactory.medical.body.trauma.TraumaType;
import com.warfactory.medical.body.trauma.TreatmentAction;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class MedicalDefinitions {

    public static final String FILE_NAME = "wfmedical_definitions.toml";
    private static final Logger LOGGER = LogUtils.getLogger();

    private MedicalDefinitions() {
    }

    /**
     * One table of {@code configDir/wfmedical_definitions.toml} (written from the bundled copy when absent).
     * Unreadable file, parse error or duplicate key => {@code defaults} (ERROR logged); empty table => {@code defaults}.
     */
    public static <T> T load(Path configDir, String table, Function<Config, T> read, Predicate<T> isEmpty,
                             Supplier<T> defaults) {
        Path file = configDir.resolve(FILE_NAME);
        T out = null;
        try {
            if (!Files.exists(file)) {
                copyBundled(file);
            }
            if (Files.exists(file)) {
                CommentedConfig root;
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    root = TomlFormat.instance().createParser().parse(reader);
                }
                out = read.apply(root);
            }
        } catch (Exception e) {
            LOGGER.error("[wfmedical] Failed to load [[{}]] from {} - using hardcoded defaults", table, FILE_NAME, e);
            out = null;
        }
        return out == null || isEmpty.test(out) ? defaults.get() : out;
    }

    public static TraumaTypes.Table traumas(Path configDir) {
        TraumaTypes.Table out = load(configDir, "trauma", MedicalDefinitions::readTraumas, t -> t.size() == 0,
                MedicalDefinitions::defaultTraumas);
        LOGGER.info("[wfmedical] Loaded {} trauma types", out.size());
        return out;
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

    /** {@code [[trauma]]}; duplicate id throws. */
    public static TraumaTypes.Table readTraumas(Config root) {
        TraumaTypes.Table out = new TraumaTypes.Table();
        for (Config t : tables(root, "trauma")) {
            TraumaType type = readTrauma(t);
            if (type != null) {
                out.register(type);
            }
        }
        return out;
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

        for (String entry : strList(t, "treatmentActions")) {
            TraumaResponse resp = parseResponse(entry);
            if (resp != null) {
                b.response(resp);
            }
        }
        return b.build();
    }

    /** Parse a {@code treatmentActions} entry of the form {@code ACTION[=EFFECT[:factor]]}. */
    private static TraumaResponse parseResponse(String entry) {
        if (entry == null) {
            return null;
        }
        String s = entry.trim();
        if (s.isEmpty()) {
            return null;
        }
        int eq = s.indexOf('=');
        TreatmentAction action = parseAction(eq < 0 ? s : s.substring(0, eq));
        if (action == null) {
            return null;
        }
        if (eq < 0) {
            return TraumaResponse.defaultFor(action);
        }
        String spec = s.substring(eq + 1).trim();
        int colon = spec.indexOf(':');
        String effectName = colon < 0 ? spec : spec.substring(0, colon);
        String param = colon < 0 ? null : spec.substring(colon + 1).trim();
        TraumaResponse.Effect effect = parseEffect(effectName);
        if (effect == null) {
            LOGGER.warn("[wfmedical] Unknown treatment effect '{}' in '{}' - using default", effectName, entry);
            return TraumaResponse.defaultFor(action);
        }
        float factor = 0.0F;
        if (effect == TraumaResponse.Effect.REDUCE_BLEED) {
            TraumaResponse def = TraumaResponse.defaultFor(action);
            factor = def != null && def.effect() == TraumaResponse.Effect.REDUCE_BLEED ? def.factor() : 0.25F;
        }
        if (param != null && !param.isEmpty()) {
            try {
                factor = Float.parseFloat(param);
            } catch (NumberFormatException ignored) {
                LOGGER.warn("[wfmedical] Bad treatment factor '{}' in '{}'", param, entry);
            }
        }
        return new TraumaResponse(action, effect, factor);
    }

    private static TraumaResponse.Effect parseEffect(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "STOP", "STOP_BLEED" -> TraumaResponse.Effect.STOP_BLEED;
            case "REDUCE", "REDUCE_BLEED" -> TraumaResponse.Effect.REDUCE_BLEED;
            case "SUTURE" -> TraumaResponse.Effect.SUTURE;
            case "STABILIZE" -> TraumaResponse.Effect.STABILIZE;
            case "HEAL" -> TraumaResponse.Effect.HEAL;
            default -> null;
        };
    }

    public static TraumaTypes.Table defaultTraumas() {
        TraumaTypes.Table registry = new TraumaTypes.Table();
        registry.register(TraumaType.builder("bruise", TraumaCategory.BRUISE)
                .major(false).severityContribution(0.3F).painPerSeverity(0.15F).bleedingPerSeverity(0.0F)
                .healSpeedPerTick(0.0008F).canReopen(false).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(0.0F).maxSeverity(1.0F).mergeable(true)
                .treatments(TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("blunt_force_trauma", TraumaCategory.BRUISE)
                .major(false).severityContribution(1.0F).painPerSeverity(0.35F).bleedingPerSeverity(0.0F)
                .healSpeedPerTick(0.0006F).canReopen(false).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(12.0F).maxSeverity(1.0F).mergeable(true)
                .treatments(TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("laceration_small", TraumaCategory.LACERATION)
                .major(false).severityContribution(0.4F).painPerSeverity(0.2F).bleedingPerSeverity(0.4F)
                .healSpeedPerTick(0.0010F).canReopen(true).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(1.0F).maxSeverity(1.0F).mergeable(true)
                .response(new TraumaResponse(TreatmentAction.REDUCE_BLEEDING, TraumaResponse.Effect.STOP_BLEED, 0.0F))
                .treatments(TreatmentAction.SUTURE_WOUND, TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("laceration_large", TraumaCategory.LACERATION)
                .major(true).severityContribution(0.8F).painPerSeverity(0.6F).bleedingPerSeverity(1.2F)
                .healSpeedPerTick(0.0010F).canReopen(true).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(4.0F).maxSeverity(1.0F).mergeable(true)
                .response(new TraumaResponse(TreatmentAction.REDUCE_BLEEDING, TraumaResponse.Effect.STOP_BLEED, 0.0F))
                .treatments(TreatmentAction.SUTURE_WOUND, TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("fracture", TraumaCategory.FRACTURE)
                .major(true).severityContribution(0.9F).painPerSeverity(0.5F).bleedingPerSeverity(0.0F)
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
                // Bandages do nothing to internal bleeding: a hemostatic only slows it, and only a suture kit
                // stops it outright. The wound itself is permanent and still needs a medkit to clear.
                .response(new TraumaResponse(TreatmentAction.BOOST_CLOTTING, TraumaResponse.Effect.REDUCE_BLEED, 0.3F))
                .response(new TraumaResponse(TreatmentAction.SUTURE_WOUND, TraumaResponse.Effect.SUTURE, 0.0F))
                .treatments(TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("puncture", TraumaCategory.PUNCTURE)
                .major(true).severityContribution(0.7F).painPerSeverity(0.5F).bleedingPerSeverity(0.9F)
                .healSpeedPerTick(0.0010F).canReopen(true).permanent(false).movementModifier(1.0F)
                .healthReductionPerSeverity(3.5F).maxSeverity(1.0F).mergeable(true)
                // A bandage fully stops the bleed; a suture closes it and it then mends over ~1 minute.
                .response(new TraumaResponse(TreatmentAction.REDUCE_BLEEDING, TraumaResponse.Effect.STOP_BLEED, 0.0F))
                .treatments(TreatmentAction.SUTURE_WOUND, TreatmentAction.HEAL_TRAUMA).build());

        registry.register(TraumaType.builder("crush_injury", TraumaCategory.CRUSH_INJURY)
                .major(true).severityContribution(0.8F).painPerSeverity(0.6F).bleedingPerSeverity(0.3F)
                .healSpeedPerTick(0.00008F).canReopen(false).permanent(false).movementModifier(0.85F)
                .healthReductionPerSeverity(4.0F).maxSeverity(1.0F).mergeable(true)
                // A dressing fully stops the bleed, but the crush itself only heals from a medkit.
                .response(new TraumaResponse(TreatmentAction.REDUCE_BLEEDING, TraumaResponse.Effect.STOP_BLEED, 0.0F))
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

        return registry;
    }

    @SuppressWarnings("unchecked")
    public static List<Config> tables(Config root, String key) {
        Object o = root.get(Collections.singletonList(key));
        if (o instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Config) {
            return (List<Config>) list;
        }
        return Collections.emptyList();
    }

    public static TreatmentAction parseAction(String name) {
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

    public static float flt(Config c, String key, float def) {
        Object o = raw(c, key);
        return o instanceof Number n ? n.floatValue() : def;
    }

    public static double dbl(Config c, String key, double def) {
        Object o = raw(c, key);
        return o instanceof Number n ? n.doubleValue() : def;
    }

    public static int intOf(Config c, String key, int def) {
        Object o = raw(c, key);
        return o instanceof Number n ? n.intValue() : def;
    }

    public static boolean bool(Config c, String key, boolean def) {
        Object o = raw(c, key);
        return o instanceof Boolean b ? b : def;
    }

    public static String str(Config c, String key, String def) {
        Object o = raw(c, key);
        return o instanceof String s ? s : def;
    }

    public static List<String> strList(Config c, String key) {
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
