package com.warfactory.medical.core.damage;

import com.warfactory.medical.compat.TaczCompat;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Maps a vanilla {@link DamageSource} to a {@link DamageCategory} using damage-type tags, well-known
 * damage-type keys and the source msgId. TACZ gun damage is detected first via {@link TaczCompat}.
 * All tag/key lookups are null-guarded so unusual modded sources fall back to {@link DamageCategory#GENERIC}.
 */
public final class DamageClassifier {

    private DamageClassifier() {
    }

    public static DamageCategory classify(DamageSource source) {
        if (source == null) {
            return DamageCategory.GENERIC;
        }

        // Firearms (TACZ) take priority so bullets are never mis-tagged as generic projectiles.
        if (TaczCompat.isGunDamage(source)) {
            return DamageCategory.BALLISTIC;
        }

        String msg = source.getMsgId();
        String msgLower = msg == null ? "" : msg.toLowerCase(Locale.ROOT);

        // Environmental / elemental categories.
        if (is(source, DamageTypeTags.IS_FIRE) || is(source, DamageTypes.LAVA)
                || is(source, DamageTypes.HOT_FLOOR) || contains(msgLower, "fire", "lava", "burn", "hot")) {
            return DamageCategory.FIRE;
        }
        if (is(source, DamageTypeTags.IS_EXPLOSION) || contains(msgLower, "explos", "blast")) {
            return DamageCategory.EXPLOSION;
        }
        if (is(source, DamageTypeTags.IS_FALL) || is(source, DamageTypes.FALL)
                || is(source, DamageTypes.STALAGMITE) || contains(msgLower, "fall")) {
            return DamageCategory.FALL;
        }

        // Modded chemical / radiation sources, recognised heuristically by msgId.
        if (contains(msgLower, "radiat", "nuclear", "rad")) {
            return DamageCategory.RADIATION;
        }
        if (contains(msgLower, "chemical", "acid", "poison", "toxic", "gas")) {
            return DamageCategory.CHEMICAL;
        }

        // Piercing: arrows, tridents and other non-firearm projectiles.
        if (is(source, DamageTypes.ARROW) || is(source, DamageTypes.TRIDENT)
                || is(source, DamageTypes.MOB_PROJECTILE)
                || is(source, DamageTypeTags.IS_PROJECTILE)) {
            return DamageCategory.PIERCING;
        }

        // Melee from mobs/players. A bare-handed strike (empty main hand) is UNARMED – blunt, mostly bruising;
        // an armed strike (or an environmental sting/thorn) is treated as slashing.
        if (is(source, DamageTypes.PLAYER_ATTACK) || is(source, DamageTypes.MOB_ATTACK)
                || is(source, DamageTypes.MOB_ATTACK_NO_AGGRO)) {
            return isUnarmed(source) ? DamageCategory.UNARMED : DamageCategory.SLASHING;
        }
        if (is(source, DamageTypes.STING) || is(source, DamageTypes.SWEET_BERRY_BUSH)
                || is(source, DamageTypes.CACTUS)) {
            return DamageCategory.SLASHING;
        }

        // Blunt-ish crushing/impact sources.
        if (is(source, DamageTypes.FALLING_BLOCK) || is(source, DamageTypes.FALLING_ANVIL)
                || is(source, DamageTypes.FLY_INTO_WALL) || is(source, DamageTypes.CRAMMING)) {
            return DamageCategory.BLUNT;
        }

        return DamageCategory.GENERIC;
    }

    /** True when the melee attacker is striking with an empty main hand (a punch). */
    private static boolean isUnarmed(DamageSource source) {
        return source.getEntity() instanceof LivingEntity attacker && attacker.getMainHandItem().isEmpty();
    }

    private static boolean is(DamageSource source, net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> tag) {
        if (source == null || tag == null) {
            return false;
        }
        try {
            return source.is(tag);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean is(DamageSource source, net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> key) {
        if (source == null || key == null) {
            return false;
        }
        try {
            return source.is(key);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean contains(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
