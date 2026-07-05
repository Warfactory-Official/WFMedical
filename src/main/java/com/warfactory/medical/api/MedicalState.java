package com.warfactory.medical.api;

import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.world.entity.player.Player;

/**
 * Read-only static facade over the cached {@link DerivedStats} of a player's medical capability.
 *
 * <p>Consumed by mixins (sprint/jump/movement) and item/effect logic on both the client and server.
 * Every accessor is null-safe and returns a benign default (unblocked / multiplier {@code 1.0} /
 * not unconscious) when the capability is missing, so it never NPEs for non-players or on a client
 * that has not yet received a sync.</p>
 */
public final class MedicalState {

    private MedicalState() {
    }

    private static DerivedStats stats(Player player) {
        if (player == null) {
            return null;
        }
        // On the logical client the capability profile is never populated (server syncs land in
        // ClientMedicalCache), so read the synced snapshot there. Client-authoritative movement like
        // LivingEntity#getJumpPower runs on the client, so the mixin needs the real synced values here.
        if (player.level().isClientSide()) {
            return ClientMedicalCache.stats();
        }
        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return null;
        }
        return data.getProfile().cached();
    }

    /** @return true when leg fracture / knockdown forbids sprinting. */
    public static boolean isSprintBlocked(Player player) {
        DerivedStats s = stats(player);
        return s != null && s.sprintBlocked();
    }

    /** @return multiplier applied to jump strength (1.0 = unaffected). */
    public static float jumpMultiplier(Player player) {
        DerivedStats s = stats(player);
        return s != null ? s.jumpMultiplier() : 1.0F;
    }

    /** @return multiplier applied to movement speed (1.0 = unaffected). */
    public static float movementMultiplier(Player player) {
        DerivedStats s = stats(player);
        return s != null ? s.movementMultiplier() : 1.0F;
    }

    /** @return true when the player is unconscious (bleed-out knockdown OR overdose blackout — the single merged state). */
    public static boolean isUnconscious(Player player) {
        DerivedStats s = stats(player);
        return s != null && s.state() == HealthState.UNCONSCIOUS;
    }
}
