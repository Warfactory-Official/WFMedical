package com.warfactory.medical.damage;

import com.warfactory.medical.body.MedicalConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Damage reads of the shared {@link MedicalConfig} spec. */
public final class DamageConfig {

    private static volatile List<? extends String> parsedFrom;
    private static volatile Map<String, DamageCategory> categories = Map.of();

    private DamageConfig() {
    }

    public static double majorTraumaFraction(DamageCategory cat) {
        if (cat == null) {
            return MedicalConfig.majorTraumaFractionDefault();
        }
        return switch (cat) {
            case BALLISTIC -> MedicalConfig.majorTraumaFractionBallistic();
            case EXPLOSION -> MedicalConfig.majorTraumaFractionExplosion();
            case BLUNT -> MedicalConfig.majorTraumaFractionBlunt();
            case UNARMED -> MedicalConfig.majorTraumaFractionUnarmed();
            case FALL -> MedicalConfig.majorTraumaFractionFall();
            default -> MedicalConfig.majorTraumaFractionDefault();
        };
    }

    public static boolean canInstakillOnImpact(DamageCategory cat) {
        return cat != DamageCategory.FIRE && cat != DamageCategory.CHEMICAL && cat != DamageCategory.RADIATION;
    }

    /**
     * Look up a configured hurt category for a damage source key (its registry id or msgId), or null if
     * no mapping is configured. Backs the data-driven {@code damageSourceCategories} override.
     */
    public static DamageCategory damageSourceCategory(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        List<? extends String> raw;
        try {
            raw = MedicalConfig.damageSourceCategories();
        } catch (IllegalStateException notLoaded) {
            return null;
        }
        Map<String, DamageCategory> map = categories;
        if (raw != parsedFrom) {
            map = parse(raw);
            categories = map;
            parsedFrom = raw;
        }
        return map.get(key);
    }

    private static Map<String, DamageCategory> parse(List<? extends String> raw) {
        Map<String, DamageCategory> parsed = new HashMap<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq >= entry.length() - 1) {
                continue;
            }
            String k = entry.substring(0, eq).trim();
            String v = entry.substring(eq + 1).trim();
            if (k.isEmpty() || v.isEmpty()) {
                continue;
            }
            try {
                parsed.put(k, DamageCategory.valueOf(v.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignoredBadCategory) {
                // skip entries with an unknown category name
            }
        }
        return Map.copyOf(parsed);
    }
}
