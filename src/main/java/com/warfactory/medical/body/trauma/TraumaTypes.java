package com.warfactory.medical.body.trauma;

import com.warfactory.medical.WFMedical;
import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Trauma types by RL; a bare id ({@code TraumaType#getId}, NBT {@code Type}) means {@code wfmedical:<id>}. */
public final class TraumaTypes {

    public static final WFRegistry<TraumaType> TYPES = WFRegistry.create("trauma types");

    /** The live registry as a {@link Lookup}. */
    public static final Lookup LIVE = new Lookup() {
        @Override
        public TraumaType get(String id) {
            return id == null || id.isEmpty() ? null : TYPES.get(key(id));
        }

        @Override
        public Collection<TraumaType> all() {
            return TYPES.values();
        }
    };

    private TraumaTypes() {
    }

    public static ResourceLocation key(String id) {
        return id.indexOf(':') < 0 ? ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, id) : ResourceLocation.parse(id);
    }

    /** Read side of a trauma table: {@link #LIVE} in game, a {@link Table} in pure code and tests. */
    public interface Lookup {
        @Nullable
        TraumaType get(String id);

        /** Declaration order. */
        Collection<TraumaType> all();

        default TraumaType getOrThrow(String id) {
            TraumaType t = get(id);
            if (t == null) {
                throw new IllegalArgumentException("Unknown trauma type id: " + id);
            }
            return t;
        }

        @Nullable
        default TraumaType firstOfCategory(TraumaCategory category) {
            for (TraumaType t : all()) {
                if (t.getCategory() == category) {
                    return t;
                }
            }
            return null;
        }
    }

    /** Unregistered table: definitions file contents, test fixtures. Duplicate id throws. */
    public static final class Table implements Lookup {
        private final Map<ResourceLocation, TraumaType> byKey = new LinkedHashMap<>();

        public TraumaType register(TraumaType type) {
            if (byKey.putIfAbsent(key(type.getId()), type) != null) {
                throw new IllegalArgumentException("Duplicate trauma id: " + type.getId());
            }
            return type;
        }

        @Override
        public TraumaType get(String id) {
            return id == null || id.isEmpty() ? null : byKey.get(key(id));
        }

        @Override
        public Collection<TraumaType> all() {
            return Collections.unmodifiableCollection(byKey.values());
        }

        public int size() {
            return byKey.size();
        }
    }
}
