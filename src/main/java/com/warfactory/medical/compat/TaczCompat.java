package com.warfactory.medical.compat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraftforge.fml.ModList;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Soft-compat glue for TACZ (Timeless and Classics Zero). Deliberately has <b>no</b> hard reference
 * to any TACZ class: gun/bullet damage is recognised purely by matching {@link DamageSource} message
 * ids and damage-type registry keys against a configurable, lower-cased id substring set. This keeps
 * the integration robust across TACZ versions.
 */
public final class TaczCompat {

    public static final String MOD_ID = "tacz";

    /**
     * Substrings (lower-case) matched against a damage source's msgId and its damage-type registry
     * key namespace/path. Mutable so config can extend/replace it at load time.
     */
    private static final Set<String> BULLET_DAMAGE_IDS =
            new CopyOnWriteArraySet<>(Set.of("tacz", "bullet", "gun"));

    private TaczCompat() {
    }

    /** @return true when the TACZ mod is present in the running instance. */
    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    /** Live view of the recognised gun/bullet damage id substrings. */
    public static Set<String> getBulletDamageIds() {
        return BULLET_DAMAGE_IDS;
    }

    /** Replace the recognised id set (values are lower-cased and blanks dropped). */
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

    /**
     * @return true only when TACZ is loaded and the given source looks like gun/bullet damage.
     *         Matches on the source msgId as well as the damage-type registry key namespace and path.
     */
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
