package com.warfactory.medical.core.substance;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the set of known {@link Substance} definitions keyed by item registry-name, populated from config.
 * A single {@link #active()} instance is exposed; hardcoded defaults act as an IO-free safety net.
 */
public final class SubstanceRegistry {

    public static final String MORPHINE_ITEM_ID = "wfmedical:morphine_syringe";
    public static final String NALOXONE_ITEM_ID = "wfmedical:naloxone_syringe";
    public static final String COMBAT_STIMULANT_ITEM_ID = "wfmedical:combat_stimulant_i";
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
     * Bundled morphine opioid (safety-net default).
     */
    public static Substance defaultMorphine() {
        return new Substance(
                "morphine", MORPHINE_ITEM_ID,
                0.95F,   // painSuppression
                0.5F,    // doseLoad
                1.0F,    // overdoseThreshold
                200,     // unconsciousTicks
                1.6F,    // lethalThreshold
                false,   // antidote
                0.0F,    // reversalAmount (unused for opioid)
                40,      // useDurationTicks
                0.0D,    // bloodRestoreMl
                0.0F,    // clottingBoost
                0.0F,    // stimulantStrength
                0);      // effectTicks
    }

    /**
     * Bundled naloxone antidote (safety-net default).
     */
    public static Substance defaultNaloxone() {
        return new Substance(
                "naloxone", NALOXONE_ITEM_ID,
                0.0F,    // painSuppression (irrelevant for antidote)
                0.0F,    // doseLoad
                0.0F,    // overdoseThreshold (unused)
                0,       // unconsciousTicks (unused)
                0.0F,    // lethalThreshold (disabled)
                true,    // antidote
                3.0F,    // reversalAmount
                30,      // useDurationTicks
                0.0D,    // bloodRestoreMl
                0.0F,    // clottingBoost
                0.0F,    // stimulantStrength
                0);      // effectTicks
    }

    /**
     * The bundled Combat Stimulant I injectable (safety-net default). A heavily risky, high-dose stimulant:
     * near-total anesthesia, a big speed boost + cleared jump penalty, and unnatural blood clotting for 3
     * minutes — but a single dose leaves a large drug load whose come-down outlasts the effect, and a second
     * dose overdoses hard (past the lethal line).
     */
    public static Substance defaultCombatStimulant() {
        return new Substance(
                "combat_stimulant_i", COMBAT_STIMULANT_ITEM_ID,
                0.0F,    // painSuppression (anesthesia comes from the stimulant strength below)
                1.4F,    // doseLoad (HIGH — a single dose sits just under the lethal line; a second is fatal)
                1.6F,    // overdoseThreshold
                200,     // unconsciousTicks (if it does overdose)
                2.6F,    // lethalThreshold
                false,   // antidote
                0.0F,    // reversalAmount
                40,      // useDurationTicks
                0.0D,    // bloodRestoreMl
                1.0F,    // clottingBoost (unnatural — even severe bleeds clot)
                0.97F,   // stimulantStrength (very insusceptible to pain + speed + jump clear)
                3600);   // effectTicks (3 minutes)
    }

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

    public void registerDefaults() {
        register(defaultMorphine());
        register(defaultNaloxone());
        register(defaultCombatStimulant());
    }
}
