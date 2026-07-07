package com.warfactory.medical.core.trauma;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the known {@link TraumaType} definitions. A single {@link #active()} instance is exposed so other
 * modules can resolve trauma types by id during NBT load / networking without threading the registry through.
 */
public final class TraumaRegistry {

    private static volatile TraumaRegistry active = new TraumaRegistry();

    private final Map<String, TraumaType> byId = new LinkedHashMap<>();

    public TraumaRegistry() {
    }

    /**
     * The globally active registry (never null).
     */
    public static TraumaRegistry active() {
        return active;
    }

    public static void setActive(TraumaRegistry registry) {
        active = registry != null ? registry : new TraumaRegistry();
    }

    public TraumaType register(TraumaType type) {
        byId.put(type.getId(), type);
        return type;
    }

    /**
     * @return the type, or {@code null} if unknown.
     */
    public TraumaType get(String id) {
        return byId.get(id);
    }

    public TraumaType getOrThrow(String id) {
        TraumaType t = byId.get(id);
        if (t == null) {
            throw new IllegalArgumentException("Unknown trauma type id: " + id);
        }
        return t;
    }

    public boolean contains(String id) {
        return byId.containsKey(id);
    }

    public Collection<TraumaType> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /**
     * First registered type of a given category (insertion order), or {@code null}.
     */
    public TraumaType firstOfCategory(TraumaCategory category) {
        for (TraumaType t : byId.values()) {
            if (t.getCategory() == category) {
                return t;
            }
        }
        return null;
    }

    public int size() {
        return byId.size();
    }

    public void clear() {
        byId.clear();
    }
}
