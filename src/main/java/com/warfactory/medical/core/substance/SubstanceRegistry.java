package com.warfactory.medical.core.substance;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the set of known {@link Substance} definitions, keyed by the registry-name string of the item that
 * injects them (populated from config at load time). Mirrors
 * {@link com.warfactory.medical.core.trauma.TraumaRegistry}.
 *
 * <p>A single {@link #active()} instance is exposed so the server can resolve a substance by item id
 * without threading the registry through every call. Hardcoded morphine + naloxone defaults are provided
 * as a safety net via {@link #withDefaults()} / {@link #registerDefaults()}.</p>
 */
public final class SubstanceRegistry {

    /**
     * Item id of the bundled morphine opioid injectable.
     */
    public static final String MORPHINE_ITEM_ID = "wfmedical:morphine_syringe";
    /**
     * Item id of the bundled naloxone antidote injectable.
     */
    public static final String NALOXONE_ITEM_ID = "wfmedical:naloxone_syringe";
    private static volatile SubstanceRegistry active = withDefaults();
    private final Map<String, Substance> byItemId = new LinkedHashMap<>();

    public SubstanceRegistry() {
    }

    /**
     * The globally active registry (never null).
     */
    public static SubstanceRegistry active() {
        return active;
    }

    public static void setActive(SubstanceRegistry registry) {
        active = registry != null ? registry : withDefaults();
    }

    /**
     * The bundled morphine opioid substance (safety-net default).
     */
    public static Substance defaultMorphine() {
        return new Substance(
                "morphine", MORPHINE_ITEM_ID,
                0.95F,   // painSuppression
                0.5F,    // doseLoad
                1.0F,    // overdoseThreshold
                200,     // blackoutTicks
                1.6F,    // lethalThreshold
                false,   // antidote
                0.0F,    // reversalAmount (unused for opioid)
                40,      // useDurationTicks
                0.0D);   // bloodRestoreMl
    }

    /**
     * The bundled naloxone antidote substance (safety-net default).
     */
    public static Substance defaultNaloxone() {
        return new Substance(
                "naloxone", NALOXONE_ITEM_ID,
                0.0F,    // painSuppression (irrelevant for antidote)
                0.0F,    // doseLoad
                0.0F,    // overdoseThreshold (unused)
                0,       // blackoutTicks (unused)
                0.0F,    // lethalThreshold (disabled)
                true,    // antidote
                3.0F,    // reversalAmount
                30,      // useDurationTicks
                0.0D);   // bloodRestoreMl
    }

    /**
     * @return a fresh registry pre-populated with the hardcoded morphine + naloxone defaults.
     */
    public static SubstanceRegistry withDefaults() {
        SubstanceRegistry r = new SubstanceRegistry();
        r.registerDefaults();
        return r;
    }

    public Substance register(Substance substance) {
        byItemId.put(substance.itemId(), substance);
        return substance;
    }

    // ---------------------------------------------------------------------
    // Hardcoded fallback defaults (IO-free safety net; mirrors the bundled TOML).
    // ---------------------------------------------------------------------

    /**
     * @return the substance bound to {@code itemId}, or {@code null} if unknown.
     */
    public Substance get(String itemId) {
        return itemId == null ? null : byItemId.get(itemId);
    }

    public boolean contains(String itemId) {
        return byItemId.containsKey(itemId);
    }

    public Collection<Substance> all() {
        return Collections.unmodifiableCollection(byItemId.values());
    }

    public int size() {
        return byItemId.size();
    }

    public void clear() {
        byItemId.clear();
    }

    /**
     * Register the hardcoded morphine + naloxone defaults into this registry.
     */
    public void registerDefaults() {
        register(defaultMorphine());
        register(defaultNaloxone());
    }
}
