package com.warfactory.medical.core.damage;

import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Decides whether the victim's armor blocked, partially blocked, or was fully penetrated by a hit.
 * Reads {@link Attributes#ARMOR} / {@link Attributes#ARMOR_TOUGHNESS} plus (for players) the durability
 * of the piece covering the struck limb. Higher armor raises the BLOCKED/PARTIAL probability; ballistic
 * and piercing hits penetrate far more readily than blunt ones. The result is probabilistic.
 */
public final class ArmorEvaluation {

    public enum Outcome {
        BLOCKED,
        PARTIAL,
        FULL
    }

    private ArmorEvaluation() {
    }

    public static Outcome evaluate(LivingEntity victim, LimbType limb, DamageCategory cat,
                                   float amount, RandomSource rand) {
        if (victim == null || rand == null) {
            return Outcome.FULL;
        }
        // Purely elemental / internal categories are not stopped by physical armor here.
        if (cat == DamageCategory.FIRE || cat == DamageCategory.CHEMICAL
                || cat == DamageCategory.RADIATION || cat == DamageCategory.FALL) {
            return Outcome.FULL;
        }

        double armor = attr(victim, Attributes.ARMOR);
        double toughness = attr(victim, Attributes.ARMOR_TOUGHNESS);
        float durabilityFactor = pieceDurabilityFactor(victim, limb);

        // How effective conventional armor is at stopping this category (1.0 = nominal).
        float effectiveness = categoryEffectiveness(cat);

        // Composite defensive score, then squash into a 0..0.95 mitigation fraction.
        double score = (armor * 0.04D) + (toughness * 0.05D) + (durabilityFactor * 0.15D);
        double mitigation = (score * effectiveness) / (1.0D + score * effectiveness);
        // Heavier hits are more likely to defeat armor.
        double loadPenalty = amount / (amount + 12.0D);
        mitigation *= (1.0D - 0.5D * loadPenalty);
        mitigation = clamp(mitigation, 0.0D, 0.95D);

        double blockedThreshold = mitigation * mitigation; // needs strong armor to fully stop
        double partialThreshold = mitigation;              // partial is easier to reach

        double roll = rand.nextDouble();
        if (roll < blockedThreshold) {
            return Outcome.BLOCKED;
        }
        if (roll < partialThreshold) {
            return Outcome.PARTIAL;
        }
        return Outcome.FULL;
    }

    private static float categoryEffectiveness(DamageCategory cat) {
        if (cat == null) {
            return 1.0F;
        }
        switch (cat) {
            case BALLISTIC:
                return 0.45F; // bullets punch through
            case PIERCING:
                return 0.6F;
            case EXPLOSION:
                return 0.7F;
            case SLASHING:
                return 1.0F;
            case BLUNT:
                return 1.2F; // plate is very good at stopping impact penetration
            default:
                return 1.0F;
        }
    }

    private static float pieceDurabilityFactor(LivingEntity victim, LimbType limb) {
        if (!(victim instanceof Player)) {
            return 0.0F;
        }
        EquipmentSlot slot = slotFor(limb);
        ItemStack stack = victim.getItemBySlot(slot);
        if (stack == null || stack.isEmpty()) {
            return 0.0F;
        }
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return 1.0F; // indestructible piece is at full protection
        }
        float remaining = 1.0F - ((float) stack.getDamageValue() / (float) stack.getMaxDamage());
        return clampF(remaining, 0.0F, 1.0F);
    }

    private static EquipmentSlot slotFor(LimbType limb) {
        switch (limb) {
            case HEAD:
                return EquipmentSlot.HEAD;
            case LEFT_LEG:
            case RIGHT_LEG:
                return EquipmentSlot.LEGS;
            case TORSO:
            case LEFT_ARM:
            case RIGHT_ARM:
            default:
                return EquipmentSlot.CHEST;
        }
    }

    private static double attr(LivingEntity entity, Attribute attribute) {
        if (entity.getAttribute(attribute) == null) {
            return 0.0D;
        }
        return entity.getAttributeValue(attribute);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
