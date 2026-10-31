package com.warfactory.medical.damage;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

public final class DamageClassifier {

    private DamageClassifier() {
    }

    public static DamageCategory classify(DamageSource source) {
        if (source == null) {
            return DamageCategory.GENERIC;
        }

        // Data-driven overrides (config: damageSourceCategories) win over the built-in guesser. //TODO: Ensure it works fine with gt shock and steam
        DamageCategory configured = configuredCategory(source);
        if (configured != null) {
            return configured;
        }
        DamageCategory strike = Strikes.category(source);
        if (strike != null) {
            return strike;
        }

        String msg = source.getMsgId();
        String msgLower = msg == null ? "" : msg.toLowerCase(Locale.ROOT);

        if (msgLower.contains("gunfire") || msgLower.contains("gunshot") || msgLower.contains("bullet")) {
            return DamageCategory.BALLISTIC;
        }

        if (is(source, DamageTypeTags.IS_FIRE) || is(source, DamageTypes.LAVA)
                || is(source, DamageTypes.HOT_FLOOR) || hasToken(msgLower, "fire", "lava", "burn", "flame")) {
            return DamageCategory.FIRE;
        }
        if (is(source, DamageTypeTags.IS_EXPLOSION) || contains(msgLower, "explos", "blast")) {
            return DamageCategory.EXPLOSION;
        }
        if (is(source, DamageTypeTags.IS_FALL) || is(source, DamageTypes.FALL)
                || is(source, DamageTypes.STALAGMITE) || hasToken(msgLower, "fall")) {
            return DamageCategory.FALL;
        }

        if (contains(msgLower, "radiat", "nuclear")) {
            return DamageCategory.RADIATION;
        }
        if (contains(msgLower, "chemical", "acid", "poison", "toxic") || hasToken(msgLower, "gas")) {
            return DamageCategory.CHEMICAL;
        }

        if (is(source, DamageTypes.ARROW) || is(source, DamageTypes.TRIDENT)
                || is(source, DamageTypes.MOB_PROJECTILE)
                || is(source, DamageTypeTags.IS_PROJECTILE)) {
            return DamageCategory.PIERCING;
        }

        if (is(source, DamageTypes.PLAYER_ATTACK) || is(source, DamageTypes.MOB_ATTACK)
                || is(source, DamageTypes.MOB_ATTACK_NO_AGGRO)) {
            return isUnarmed(source) ? DamageCategory.UNARMED : DamageCategory.SLASHING;
        }
        if (is(source, DamageTypes.STING) || is(source, DamageTypes.SWEET_BERRY_BUSH)
                || is(source, DamageTypes.CACTUS)) {
            return DamageCategory.SLASHING;
        }

        if (is(source, DamageTypes.FALLING_BLOCK) || is(source, DamageTypes.FALLING_ANVIL)
                || is(source, DamageTypes.FLY_INTO_WALL) || is(source, DamageTypes.CRAMMING)) {
            return DamageCategory.BLUNT;
        }

        return DamageCategory.GENERIC;
    }

    private static DamageCategory configuredCategory(DamageSource source) {
        ResourceLocation id = damageTypeId(source);
        if (id != null) {
            DamageCategory byId = DamageConfig.damageSourceCategory(id.toString());
            if (byId != null) {
                return byId;
            }
        }
        String msg = source.getMsgId();
        if (msg != null) {
            return DamageConfig.damageSourceCategory(msg);
        }
        return null;
    }

    private static ResourceLocation damageTypeId(DamageSource source) {
        try {
            return source.typeHolder().unwrapKey().map(ResourceKey::location).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isUnarmed(DamageSource source) {
        return source.getEntity() instanceof LivingEntity attacker && attacker.getMainHandItem().isEmpty();
    }

    private static boolean is(DamageSource source, TagKey<DamageType> tag) {
        if (source == null || tag == null) {
            return false;
        }
        try {
            return source.is(tag);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean is(DamageSource source, ResourceKey<DamageType> key) {
        if (source == null || key == null) {
            return false;
        }
        try {
            return source.is(key);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean hasToken(String haystack, String... tokens) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        for (String part : haystack.split("[^a-z0-9]+")) {
            for (String t : tokens) {
                if (part.equals(t)) {
                    return true;
                }
            }
        }
        return false;
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
