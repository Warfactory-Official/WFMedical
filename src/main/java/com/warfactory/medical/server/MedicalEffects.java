package com.warfactory.medical.server;

import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import net.minecraft.server.level.ServerPlayer;
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
     * Push the derived snapshot onto the vanilla body: transient MAX_HEALTH / MOVEMENT_SPEED modifiers and
     * a health clamp. Keeps an unconscious player alive (death interception lives in the event handler).
     *
     * <p>Convenience overload that never raises current health (normal ticking behaviour).</p>
     */
    public static void apply(ServerPlayer player, DerivedStats stats) {
        apply(player, stats, false);
    }

    /**
     * Push the derived snapshot onto the vanilla body: transient MAX_HEALTH / MOVEMENT_SPEED modifiers and
     * a health reconciliation. Keeps an unconscious player alive (death interception lives in the event
     * handler).
     *
     * <p>When {@code allowRaise} is {@code false} current health is only clamped DOWNWARD toward the
     * derived target (normal ticking never heals the player). When {@code allowRaise} is {@code true} the
     * health is set EXACTLY to the derived {@code effectiveCurrentHealth} (clamped to {@code [0,
     * effectiveMaxHealth]}); this is used on join / respawn / dimension change so a pristine player spawns
     * at full derived health instead of being stuck at the old vanilla 20 while max is lifted to 30.</p>
     *
     * @param allowRaise when true, current health may be raised to the derived target (join/respawn only)
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
    }
}
