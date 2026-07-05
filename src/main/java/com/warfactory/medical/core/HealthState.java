package com.warfactory.medical.core;

/**
 * High-level physiological state of a player, driving the Healthy -> Critical -> Knocked Down -> Dead
 * progression described in the design.
 */
public enum HealthState {
    HEALTHY,
    CRITICAL,
    KNOCKED_DOWN,
    DEAD;

    public static HealthState byName(String name, HealthState fallback) {
        if (name == null) {
            return fallback;
        }
        for (HealthState s : values()) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return fallback;
    }
}
