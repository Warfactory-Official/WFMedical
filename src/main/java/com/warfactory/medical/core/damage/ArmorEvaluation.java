package com.warfactory.medical.core.damage;

import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ArmorEvaluation {

    private ArmorEvaluation() {
    }

    public static Outcome evaluate(LivingEntity victim, LimbType limb, DamageCategory cat,
                                   float amount, RandomSource rand) {
        if (victim == null || rand == null) {
            return Outcome.FULL;
        }
        if (cat == DamageCategory.FIRE || cat == DamageCategory.CHEMICAL
                || cat == DamageCategory.RADIATION || cat == DamageCategory.FALL) {
            return Outcome.FULL;
        }

        double armor = attr(victim, Attributes.ARMOR);
        double toughness = attr(victim, Attributes.ARMOR_TOUGHNESS);
        float durabilityFactor = pieceDurabilityFactor(victim, limb);

        float effectiveness = categoryEffectiveness(cat);

        double score = (armor * 0.04D) + (toughness * 0.05D) + (durabilityFactor * 0.15D);
        double mitigation = (score * effectiveness) / (1.0D + score * effectiveness);
        double loadPenalty = amount / (amount + 12.0D);
        mitigation *= (1.0D - 0.5D * loadPenalty);
        mitigation = clamp(mitigation, 0.0D, 0.95D);

        double blockedThreshold = mitigation * mitigation;
        double partialThreshold = mitigation;

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
                return 0.45F;
            case PIERCING:
                return 0.6F;
            case EXPLOSION:
                return 0.7F;
            case SLASHING:
                return 1.0F;
            case BLUNT:
                return 1.2F;
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
            return 1.0F;
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

    private static double attr(LivingEntity entity, Holder<Attribute> attribute) {
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

    public enum Outcome {
        BLOCKED,
        PARTIAL,
        FULL
    }
}
