package com.warfactory.medical.core.damage.rig;

import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.damage.HitAuthority;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-tick memoisation of {@link HumanoidRig#compute} plus the server-side store of client-streamed poses
 * for {@link HitAuthority#CLIENT_HINT}.
 *
 * <p><b>Why.</b> The rig is rebuilt several times for one shot &mdash; the pre-hit gap rejection
 * ({@code onLivingAttackGapReject}), the hit itself ({@code resolveHit}), and once per pellet / blast ray
 * &mdash; and again every frame by the debug overlay. Rebuilding is the costly part (a full
 * {@code setupAnim} replica + six {@code toObb} conversions). Within a single tick a victim's pose is fixed,
 * so caching the built {@link HumanoidRig.LocalRig} by {@code (side, entityId, gameTime)} collapses all of
 * those to one rebuild. This is the cheap, always-on fix for the per-hit cost.</p>
 *
 * <p><b>Authority.</b> {@link #resolve} is the entry point damage classification should use. Under
 * {@link HitAuthority#CLIENT_HINT} it prefers a fresh, validated pose the victim's own client streamed
 * (skipping the rebuild entirely), but still leaves the ray test to the server &mdash; the victim only
 * vouches for its own pose, never the hit. When the hint is absent, stale, or implausible it transparently
 * falls back to {@link #get} (the per-tick cache / a server rebuild).</p>
 */
public final class RigCache {

    private static final ConcurrentHashMap<Long, Entry> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Hint> HINTS = new ConcurrentHashMap<>();
    /**
     * Cheap unbounded-growth guard: players/persistent bodies are the only rig targets, so this is tiny in
     * practice; a hard clear keeps a pathological entity-id churn from leaking.
     */
    private static final int MAX_ENTRIES = 1024;

    private RigCache() {
    }

    /**
     * The posed rig to classify a hit against: a validated client-streamed pose under
     * {@link HitAuthority#CLIENT_HINT} when one is available, else the per-tick cache (a server rebuild).
     */
    public static HumanoidRig.LocalRig resolve(LivingEntity victim) {
        if (!victim.level().isClientSide && MedicalConfig.useClientPose()) {
            HumanoidRig.LocalRig hinted = validHint(victim);
            if (hinted != null) {
                return hinted;
            }
        }
        return get(victim);
    }

    /**
     * The victim's rig for the current tick, rebuilt once and reused. Safe to call many times per tick.
     */
    public static HumanoidRig.LocalRig get(LivingEntity victim) {
        long gameTime = victim.level().getGameTime();
        long k = key(victim);
        Entry e = CACHE.get(k);
        if (e != null && e.tick == gameTime) {
            return e.rig;
        }
        HumanoidRig.LocalRig rig = HumanoidRig.compute(victim);
        if (CACHE.size() > MAX_ENTRIES) {
            CACHE.clear();
        }
        CACHE.put(k, new Entry(gameTime, rig));
        return rig;
    }

    // ---------------------------------------------------------------- client-streamed pose store (server)

    /**
     * Record a pose the victim's client streamed (server side). {@code receivedTick} is the server game time
     * at receipt; a hint older than {@link MedicalConfig#poseHintMaxAgeTicks} is treated as stale.
     */
    public static void submitHint(int entityId, HumanoidRig.LocalRig rig, long receivedTick) {
        if (HINTS.size() > MAX_ENTRIES) {
            HINTS.clear();
        }
        HINTS.put(entityId, new Hint(rig, receivedTick));
    }

    /**
     * Drop a victim's streamed pose (e.g. on logout).
     */
    public static void clearHint(int entityId) {
        HINTS.remove(entityId);
    }

    private static HumanoidRig.LocalRig validHint(LivingEntity victim) {
        Hint h = HINTS.get(victim.getId());
        if (h == null) {
            return null;
        }
        long age = victim.level().getGameTime() - h.receivedTick;
        if (age < 0 || age > MedicalConfig.poseHintMaxAgeTicks()) {
            return null; // stale (or clock skew) -> server rebuild
        }
        return plausible(victim, h.rig) ? h.rig : null;
    }

    /**
     * Cheap sanity bound on a streamed pose: every limb box must sit within a body-length radius of the feet
     * and carry a plausible size. Rejects a client that removed, shrank to nothing, ballooned, or flung away
     * its own hitboxes (invulnerability exploits). It cannot detect a subtle limb-swap &mdash; that residual
     * trust is inherent to {@link HitAuthority#CLIENT_HINT} and is why it is opt-in.
     */
    private static boolean plausible(LivingEntity victim, HumanoidRig.LocalRig rig) {
        double margin = MedicalConfig.poseHintMargin();
        // A prone/downed body's AABB is short but the rig still spans a full body length from the feet
        // origin, so bound by a fixed body-length floor, never the (possibly short) current AABB height.
        double reach = Math.max(victim.getBbHeight(), 1.9) + margin;
        double reachSq = reach * reach;
        double maxHalf = 1.0 + margin;
        double minHalf = 0.01;
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            Obb o = slot.get(rig);
            if (o == null) {
                return false;
            }
            Vec3 c = o.center();
            if (c.x * c.x + c.y * c.y + c.z * c.z > reachSq) {
                return false;
            }
            Vec3 hf = o.half();
            if (hf.x < minHalf || hf.y < minHalf || hf.z < minHalf
                    || hf.x > maxHalf || hf.y > maxHalf || hf.z > maxHalf) {
                return false;
            }
        }
        return true;
    }

    private static long key(LivingEntity e) {
        long id = e.getId() & 0xFFFFFFFFL;
        long side = e.level().isClientSide ? 1L : 0L;
        return (side << 32) | id;
    }

    private record Entry(long tick, HumanoidRig.LocalRig rig) {
    }

    private record Hint(HumanoidRig.LocalRig rig, long receivedTick) {
    }
}
