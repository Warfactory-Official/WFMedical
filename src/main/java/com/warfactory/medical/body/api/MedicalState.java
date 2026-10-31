package com.warfactory.medical.body.api;

import com.warfactory.medical.body.BodyHooks;
import com.warfactory.medical.body.attachment.IMedicalData;
import com.warfactory.medical.body.attachment.MedicalAttachments;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.DerivedStats;
import com.warfactory.medical.body.HealthState;
import net.minecraft.world.entity.player.Player;

/** Client: stats = local cache => other players read neutral (false, 1.0, 0); {@link #isDowned} tracked per player. */
public final class MedicalState {

    private static final float SWAY_START = 0.15F;

    private MedicalState() {
    }

    private static DerivedStats stats(Player player) {
        if (player == null) {
            return null;
        }
        if (player.level().isClientSide()) {
            return player.isLocalPlayer() ? BodyHooks.CLIENT.get().localStats() : null;
        }
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return null;
        }
        return data.getProfile().cached();
    }

    public static boolean isSprintBlocked(Player player) {
        DerivedStats s = stats(player);
        return s != null && s.sprintBlocked();
    }

    public static float jumpMultiplier(Player player) {
        DerivedStats s = stats(player);
        return s != null ? s.jumpMultiplier() : 1.0F;
    }

    public static float movementMultiplier(Player player) {
        DerivedStats s = stats(player);
        return s != null ? s.movementMultiplier() : 1.0F;
    }

    public static boolean isUnconscious(Player player) {
        DerivedStats s = stats(player);
        return s != null && s.state() == HealthState.UNCONSCIOUS;
    }

    public static boolean isBothArmsDisabled(Player player) {
        DerivedStats s = stats(player);
        return s != null && s.bothArmsDisabled();
    }

    public static boolean isBothLegsDisabled(Player player) {
        DerivedStats s = stats(player);
        return s != null && s.bothLegsDisabled();
    }

    public static boolean isHandsDisabled(Player player) {
        DerivedStats s = stats(player);
        return s != null && (s.state() == HealthState.UNCONSCIOUS || s.bothArmsDisabled());
    }

    public static boolean hasArmFracture(Player player) {
        DerivedStats s = stats(player);
        return s != null && s.anyArmFracture();
    }

    public static boolean hasArmTourniquet(Player player) {
        DerivedStats s = stats(player);
        return s != null && s.anyArmTourniquet();
    }

    /**
     * Pain sway in [0, 1]: pain ramp from {@value #SWAY_START} to {@code painUnconsciousThreshold}, floored at
     * {@code tourniquetArmSway} with an arm tourniquet. 0 when disabled or unconscious. Excludes ADS steadiness and
     * the {@code brokenArmAimSway} aiming floor.
     */
    public static float painSwayIntensity(Player player) {
        DerivedStats s = stats(player);
        if (s == null || !MedicalConfig.painSwayEnabled() || s.state() == HealthState.UNCONSCIOUS) {
            return 0.0F;
        }
        float intensity = painRamp(s.totalPain());
        return s.anyArmTourniquet() ? Math.max(intensity, (float) MedicalConfig.tourniquetArmSway()) : intensity;
    }

    private static float painRamp(float pain) {
        if (pain <= SWAY_START) {
            return 0.0F;
        }
        float span = MedicalConfig.painUnconsciousThreshold() - SWAY_START;
        return span <= 0.0F ? 1.0F : Math.min((pain - SWAY_START) / span, 1.0F);
    }

    /** Stress in [0, 1]; 0 for a non-local client player. */
    public static float stress(Player player) {
        DerivedStats s = stats(player);
        return s == null ? 0.0F : s.stress();
    }

    public static boolean isDowned(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return BodyHooks.CLIENT.get().isDowned(player.getId());
        }
        IMedicalData data = MedicalAttachments.get(player);
        return data != null && data.getProfile().isDowned();
    }
}
