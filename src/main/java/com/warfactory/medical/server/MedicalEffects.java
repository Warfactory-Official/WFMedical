package com.warfactory.medical.server;

import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Reconciles the vanilla entity body with the derived medical state WITHOUT potion effects.
 *
 * <p>Applies two stable transient attribute modifiers (max-health and movement-speed) so vanilla hearts
 * and walk speed track {@link DerivedStats}, and clamps current health downward. Sprint blocking and jump
 * scaling are handled by the mixin reading {@code MedicalState}, which reads the same cached
 * {@link DerivedStats} stored on the capability by the engine.</p>
 */
public final class MedicalEffects {

    /**
     * Fixed UUIDs so our modifiers never stack across ticks and are cleanly removable.
     */
    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("b6d4c2a0-1e3f-4a7b-9c11-2f6e8a4d1c30");
    private static final UUID MOVEMENT_MODIFIER_ID = UUID.fromString("a1f0e9d8-7c6b-4a53-8e42-0b9c7d6e5f14");
    private static final String MAX_HEALTH_MODIFIER_NAME = "wfmedical:trauma_max_health";
    private static final String MOVEMENT_MODIFIER_NAME = "wfmedical:trauma_movement";
    private static final double EPSILON = 1.0E-4D;

    private MedicalEffects() {
    }

    /**
     * Convenience overload that never raises current health (normal ticking behaviour).
     */
    public static void apply(ServerPlayer player, DerivedStats stats) {
        apply(player, stats, false);
    }

    /**
     * Push the derived snapshot onto the vanilla body: transient MAX_HEALTH / MOVEMENT_SPEED modifiers and
     * a health reconciliation. {@code allowRaise=true} sets health exactly to the derived target (used on
     * join/respawn so a pristine player spawns at full derived health, not stuck at vanilla 20).
     */
    public static void apply(ServerPlayer player, DerivedStats stats, boolean allowRaise) {
        if (player == null || stats == null) {
            return;
        }
        HealthState state = stats.state();

        // --- Max health: make total MAX_HEALTH equal effectiveMaxHealth (min 1 so the entity stays valid).
        float attrTarget = Math.max(stats.effectiveMaxHealth(), 1.0F);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(MAX_HEALTH_MODIFIER_ID);
            double without = maxHealth.getValue();
            double amount = attrTarget - without;
            if (Math.abs(amount) > EPSILON) {
                maxHealth.addTransientModifier(new AttributeModifier(
                        MAX_HEALTH_MODIFIER_ID,
                        MAX_HEALTH_MODIFIER_NAME,
                        amount,
                        AttributeModifier.Operation.ADDITION));
            }
        }

        // --- Movement: MULTIPLY_TOTAL from movementMultiplier; forced to zero while unconscious.
        double movementMultiplier = state == HealthState.UNCONSCIOUS ? 0.0D : stats.movementMultiplier();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(MOVEMENT_MODIFIER_ID);
            double amount = movementMultiplier - 1.0D;
            if (Math.abs(amount) > EPSILON) {
                speed.addTransientModifier(new AttributeModifier(
                        MOVEMENT_MODIFIER_ID,
                        MOVEMENT_MODIFIER_NAME,
                        amount,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }

        // --- Both legs drained: force the crawl (swimming) pose while conscious, and clear OUR pose once the
        // condition lifts (only touch a SWIMMING forced pose so we never clobber another mod's forced pose).
        boolean forceCrawl = state != HealthState.UNCONSCIOUS && state != HealthState.DEAD
                && stats.bothLegsDisabled();
        if (forceCrawl) {
            player.setForcedPose(Pose.SWIMMING);
        } else if (player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }

        // --- Health clamp: never exceed the derived current health; keep the UNCONSCIOUS player alive.
        // The >= 1 pin below applies to BOTH unconscious causes (bleed-out AND non-lethal overdose) so they
        // stay alive at ~1 HP. A LETHAL cause (bleed-out timer expiry OR lethal overdose drain) must NOT be
        // held alive here: the engine transitions such a player to DEAD + setHealth(0) explicitly, and the
        // early-return for DEAD below stops the pin from running, letting vanilla death proceed.
        if (state == HealthState.DEAD) {
            return; // let the death event handler resolve lethality
        }
        float target = stats.effectiveCurrentHealth();
        if (state == HealthState.UNCONSCIOUS) {
            target = Math.max(target, 1.0F);
        }
        if (target > attrTarget) {
            target = attrTarget;
        }
        float current = player.getHealth();
        // Normal ticking only clamps downward (never heals); join/respawn sets health exactly to the
        // derived target so a pristine player spawns full instead of stuck at the old vanilla value.
        float clamped = allowRaise ? target : Math.min(current, target);
        // The >=1 UNCONSCIOUS pin must NOT resurrect a player who has already died: if current health has hit
        // 0 (a finishing blow, the expired bleed-out timer, a lethal overdose), raising it back to 1 makes the
        // server ignore the respawn request (PERFORM_RESPAWN needs health <= 0) and leaves them stuck in the
        // dying animation with a disabled respawn button. Only pin an ALREADY-ALIVE unconscious player.
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

    /**
     * Stamp an Open-Persistence logout body with the player's derived max-health pool and current health.
     * Uses a PERMANENT modifier (body does not tick physiology, so this is set once at logout and must
     * survive a restart).
     */
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
                maxHealth.addPermanentModifier(new AttributeModifier(
                        MAX_HEALTH_MODIFIER_ID,
                        MAX_HEALTH_MODIFIER_NAME,
                        amount,
                        AttributeModifier.Operation.ADDITION));
            }
        }
        // Set current health to the derived value the player had (a wounded logout leaves a wounded body),
        // never above the new pool and never below 1 so the freshly-created body isn't instantly dead.
        float target = Math.min(stats.effectiveCurrentHealth(), attrTarget);
        if (target < 1.0F) {
            target = 1.0F;
        }
        body.setHealth(target);
    }

    /**
     * Remove our transient modifiers (on logout, respawn reset, or creative immunity).
     */
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
