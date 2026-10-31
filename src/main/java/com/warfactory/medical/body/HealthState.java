package com.warfactory.medical.body;

public enum HealthState {
    HEALTHY,
    CRITICAL,
    UNCONSCIOUS,
    DEAD;

    private static final String LEGACY_KNOCKED_DOWN = "KNOCKED_DOWN";

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
