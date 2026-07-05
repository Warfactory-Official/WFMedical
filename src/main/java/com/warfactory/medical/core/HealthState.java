package com.warfactory.medical.core;

/**
 * High-level physiological state of a player, driving the Healthy -> Critical -> Unconscious -> Dead
 * progression described in the design.
 *
 * <p>{@link #UNCONSCIOUS} is the SINGLE externally-visible "passed out" state. It is entered from two
 * internally-distinct causes that the engine keeps apart so the outcomes stay correct: a bleed-out
 * unconsciousness (lethal damage / blood loss, which runs a death timer and kills if untreated) and an opioid
 * overdose unconsciousness (which runs a wake timer and recovers automatically, unless the overdose is lethal).
 * From the outside — HUD, overlays, downed pose, commands, API — there is exactly one unconscious state.</p>
 *
 * <p>The declaration ORDER is significant: {@link #ordinal()} is used for severity comparisons (a worse
 * derived state is never downgraded by an admin-forced override) and states are persisted by name, so the
 * order must remain HEALTHY &lt; CRITICAL &lt; UNCONSCIOUS &lt; DEAD.</p>
 */
public enum HealthState {
    HEALTHY,
    CRITICAL,
    UNCONSCIOUS,
    DEAD;

    /**
     * Legacy persisted name for {@link #UNCONSCIOUS}, kept so old saves load cleanly after the merge.
     */
    private static final String LEGACY_KNOCKED_DOWN = "KNOCKED_DOWN";

    /**
     * Resolve a persisted state name to a constant, tolerating the pre-merge legacy {@code "KNOCKED_DOWN"}
     * string (which now maps to {@link #UNCONSCIOUS}), and falling back to {@code fallback} for anything
     * unrecognised or {@code null}.
     */
    public static HealthState byName(String name, HealthState fallback) {
        if (name == null) {
            return fallback;
        }
        if (LEGACY_KNOCKED_DOWN.equals(name)) {
            return UNCONSCIOUS;
        }
        for (HealthState s : values()) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return fallback;
    }
}
