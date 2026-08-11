package com.warfactory.medical.api;

import com.warfactory.medical.attachment.IMedicalData;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.client.ClientDownedTracker;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class MedicalState {

    private MedicalState() {
    }

    private static DerivedStats stats(Player player) {
        if (player == null) {
            return null;
        }
        if (player.level().isClientSide()) {
            return ClientMedicalCache.stats();
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

    public static boolean isDowned(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            // DistExecutor is gone; the dist check keeps the client-only tracker off the server's
            // class-loading path exactly as unsafeCallWhenOn did.
            return FMLEnvironment.dist == Dist.CLIENT && ClientDownedTracker.isDowned(player.getId());
        }
        IMedicalData data = MedicalAttachments.get(player);
        return data != null && data.getProfile().isDowned();
    }
}
