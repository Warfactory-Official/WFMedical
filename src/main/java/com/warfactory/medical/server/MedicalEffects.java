package com.warfactory.medical.server;

import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;


public final class MedicalEffects {

    // 1.21 replaced UUID-keyed attribute modifiers with ResourceLocation-keyed ones; these ids keep the
    // same names the old modifiers carried, so the intent is unchanged.
    private static final ResourceLocation MAX_HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath("wfmedical", "trauma_max_health");
    private static final ResourceLocation MOVEMENT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath("wfmedical", "trauma_movement");
    private static final double EPSILON = 1.0E-4D;

    private MedicalEffects() {
    }

    public static void apply(ServerPlayer player, DerivedStats stats) {
        apply(player, stats, false);
    }

    public static void apply(ServerPlayer player, DerivedStats stats, boolean allowRaise) {
        if (player == null || stats == null) {
            return;
        }
        HealthState state = stats.state();

        float attrTarget = Math.max(stats.effectiveMaxHealth(), 1.0F);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(MAX_HEALTH_MODIFIER_ID);
            double without = maxHealth.getValue();
            double amount = attrTarget - without;
            if (Math.abs(amount) > EPSILON) {
                maxHealth.addTransientModifier(new AttributeModifier(MAX_HEALTH_MODIFIER_ID,
                        amount,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }

        double movementMultiplier = state == HealthState.UNCONSCIOUS ? 0.0D : stats.movementMultiplier();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(MOVEMENT_MODIFIER_ID);
            double amount = movementMultiplier - 1.0D;
            if (Math.abs(amount) > EPSILON) {
                speed.addTransientModifier(new AttributeModifier(MOVEMENT_MODIFIER_ID,
                        amount,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }

        boolean forceCrawl = state != HealthState.UNCONSCIOUS && state != HealthState.DEAD
                && stats.bothLegsDisabled();
        if (forceCrawl) {
            player.setForcedPose(Pose.SWIMMING);
        } else if (player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }

        if (state == HealthState.DEAD) {
            return;
        }
        float target = stats.effectiveCurrentHealth();
        if (state == HealthState.UNCONSCIOUS) {
            target = Math.max(target, 1.0F);
        }
        if (target > attrTarget) {
            target = attrTarget;
        }
        float current = player.getHealth();
        float clamped = allowRaise ? target : Math.min(current, target);
        if (state == HealthState.UNCONSCIOUS && current > 0.0F && clamped < 1.0F) {
            clamped = 1.0F;
        }
        if (clamped < 0.0F) {
            clamped = 0.0F;
        }
        if (Math.abs(clamped - current) > EPSILON && clamped > 0.0F) {
            player.setHealth(clamped);
        }
    }

    public static void applyToBody(LivingEntity body, DerivedStats stats) {
        if (body == null || stats == null) {
            return;
        }
        float attrTarget = Math.max(stats.effectiveMaxHealth(), 1.0F);
        AttributeInstance maxHealth = body.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(MAX_HEALTH_MODIFIER_ID);
            double without = maxHealth.getValue();
            double amount = attrTarget - without;
            if (Math.abs(amount) > EPSILON) {
                maxHealth.addPermanentModifier(new AttributeModifier(MAX_HEALTH_MODIFIER_ID,
                        amount,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }
        float target = Math.min(stats.effectiveCurrentHealth(), attrTarget);
        if (target < 1.0F) {
            target = 1.0F;
        }
        body.setHealth(target);
    }

    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(MAX_HEALTH_MODIFIER_ID);
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(MOVEMENT_MODIFIER_ID);
        }
        if (player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
    }
}
