package com.warfactory.medical.core.trauma;

/**
 * Broad classification of an injury. {@code majorByDefault} is a hint used when a
 * {@link TraumaType} does not override its major/minor status.
 */
public enum TraumaCategory {
    BRUISE(false),
    LACERATION(true),
    FRACTURE(true),
    BURN(false),
    INTERNAL_BLEEDING(true),
    PUNCTURE(true),
    CRUSH_INJURY(true),
    RADIATION_BURN(true),
    CHEMICAL_BURN(true);

    private final boolean majorByDefault;

    TraumaCategory(boolean majorByDefault) {
        this.majorByDefault = majorByDefault;
    }

    public boolean isMajorByDefault() {
        return majorByDefault;
    }

    public static TraumaCategory byName(String name, TraumaCategory fallback) {
        if (name == null) {
            return fallback;
        }
        for (TraumaCategory c : values()) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        return fallback;
    }
}
