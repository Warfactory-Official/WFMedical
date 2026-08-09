package com.warfactory.medical.compat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class TaczCompat {

    public static final String MOD_ID = "tacz";

    private static final Set<String> BULLET_DAMAGE_IDS =
            new CopyOnWriteArraySet<>(Set.of("tacz", "bullet", "gun"));

    private TaczCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    public static Set<String> getBulletDamageIds() {
        return BULLET_DAMAGE_IDS;
    }

    public static void setBulletDamageIds(Collection<String> ids) {
        BULLET_DAMAGE_IDS.clear();
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                BULLET_DAMAGE_IDS.add(id.toLowerCase(Locale.ROOT));
            }
        }
    }

    public static boolean isGunDamage(DamageSource source) {
        if (source == null || !isLoaded()) {
            return false;
        }
        if (matches(source.getMsgId())) {
            return true;
        }
        ResourceLocation key = typeKeyLocation(source);
        if (key != null) {
            return matches(key.getPath()) || matches(key.getNamespace());
        }
        return false;
    }


    public static boolean isHeldGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        if (MOD_ID.equals(id.getNamespace())) {
            return true;
        }
        return matches(id.getNamespace()) || matches(id.getPath());
    }


    public static Optional<Vec3> bulletHitPos(DamageSource src) {
        return taczHit(src).map(TaczHitCapture.TaczHit::point);
    }

    /**
     * The bullet's real per-tick raycast segment, for entry-order (ray-based) OBB classification.
     * Prefer this over {@link #bulletHitPos} when picking a limb: a single point is ambiguous whenever
     * it lands near a shared boundary between two limb boxes (e.g. shoulder, hip), which happens on
     * essentially every close-range shot, whereas "first box the ray actually enters" is well-defined.
     */
    public static Optional<Vec3[]> bulletSegment(DamageSource src) {
        return taczHit(src).map(h -> new Vec3[]{h.start(), h.end()});
    }


    public static OptionalDouble bulletTotalDamage(DamageSource src) {
        if (!isGunDamage(src)) {
            return OptionalDouble.empty();
        }
        var direct = src.getDirectEntity();
        if (direct == null) {
            return OptionalDouble.empty();
        }
        return TaczHitCapture.totalDamage(direct.getId());
    }


    public static boolean claimBulletHit(DamageSource src, long tick) {
        var direct = src == null ? null : src.getDirectEntity();
        if (direct == null) {
            return true;
        }
        return TaczHitCapture.claim(direct.getId(), tick);
    }

    private static Optional<TaczHitCapture.TaczHit> taczHit(DamageSource src) {
        if (src == null) {
            return Optional.empty();
        }
        var direct = src.getDirectEntity();
        if (direct == null) {
            return Optional.empty();
        }
        return TaczHitCapture.peek(direct.getId());
    }

    private static boolean matches(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String id : BULLET_DAMAGE_IDS) {
            if (!id.isEmpty() && lower.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation typeKeyLocation(DamageSource source) {
        try {
            Optional<ResourceKey<DamageType>> key = source.typeHolder().unwrapKey();
            return key.map(ResourceKey::location).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
