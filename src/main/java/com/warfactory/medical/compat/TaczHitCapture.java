package com.warfactory.medical.compat;

import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Server-thread-only cache bridging {@code EntityKineticBulletMixin} (which captures TACZ's own
 * precise raytrace hit -- both the resolved intersection point AND the real per-tick ray segment that
 * produced it -- at the moment of impact) to {@link TaczCompat}, which runs later in the same
 * synchronous call stack once {@code entity.hurt()} fires the vanilla hurt/damage events. Capped so a
 * long session of unconsumed entries (e.g. shots that never trigger a hurt event) can't grow unbounded.
 */
public final class TaczHitCapture {

    /**
     * @param point resolved intersection point (against TACZ's own, envelope-widened, box) -- used for
     *              point-based classification (Tier-1 banding fallback, or the nearest-OBB fallback if
     *              the ray below doesn't cross any limb box at all).
     * @param start the bullet's true per-tick raycast start -- used with {@code end} for ray-based
     *              (entry-order) OBB classification, which is unambiguous even where two limb boxes touch.
     * @param end   the bullet's true per-tick raycast end.
     */
    public record TaczHit(Vec3 point, Vec3 start, Vec3 end) {
    }

    private static final int MAX_ENTRIES = 256;

    private static final Map<Integer, TaczHit> HITS = new LinkedHashMap<>(32, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, TaczHit> eldest) {
            return size() > MAX_ENTRIES;
        }
    };


    private static final Map<Integer, Float> DAMAGE = new LinkedHashMap<>(32, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Float> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private static final Map<Integer, Long> CLAIMED = new LinkedHashMap<>(32, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Long> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private TaczHitCapture() {
    }

    public static void capture(int bulletEntityId, Vec3 point, Vec3 start, Vec3 end) {
        HITS.put(bulletEntityId, new TaczHit(point, start, end));
    }

    public static Optional<TaczHit> peek(int bulletEntityId) {
        return Optional.ofNullable(HITS.get(bulletEntityId));
    }

    public static void captureDamage(int bulletEntityId, float totalDamage) {
        DAMAGE.put(bulletEntityId, totalDamage);
    }

    public static OptionalDouble totalDamage(int bulletEntityId) {
        Float v = DAMAGE.get(bulletEntityId);
        return v == null ? OptionalDouble.empty() : OptionalDouble.of(v.doubleValue());
    }

    public static boolean claim(int bulletEntityId, long tick) {
        Long prev = CLAIMED.get(bulletEntityId);
        if (prev != null && prev.longValue() == tick) {
            return false;
        }
        CLAIMED.put(bulletEntityId, tick);
        return true;
    }
}
