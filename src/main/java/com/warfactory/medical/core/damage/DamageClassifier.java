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

        // Non-TACZ firearms (e.g. SuperbWarfare's "gunfire"/"gunfire_headshot"): recognise gun/bullet msgIds
        // BEFORE the elemental checks so they classify as ballistic instead of leaking into the FIRE branch
        // ("fire" is a substring of "gunfire", "hot" of "gunshot").
        if (msgLower.contains("gunfire") || msgLower.contains("gunshot") || msgLower.contains("bullet")) {
            return DamageCategory.BALLISTIC;
        }

        // Environmental / elemental categories. Short, ambiguous words are matched as WHOLE TOKENS so
        // "fire"/"burn" never trip on "gunfire" and "fall" never trips inside a longer id.
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

        // Modded chemical / radiation sources, recognised heuristically by msgId (distinctive substrings
        // only; the bare 3-char "rad" was dropped -- it matched words like "gradual"/"comrade").
        if (contains(msgLower, "radiat", "nuclear")) {
            return DamageCategory.RADIATION;
        }
        if (contains(msgLower, "chemical", "acid", "poison", "toxic") || hasToken(msgLower, "gas")) {
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

    /**
     * True when the melee attacker is striking with an empty main hand (a punch).
     */
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

    /**
     * Whole-token match: splits the msgId on non-alphanumeric boundaries and compares whole tokens, so a
     * short token like {@code "fire"} or {@code "gas"} never matches inside {@code "gunfire"}/{@code "gasket"}.
     */
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
