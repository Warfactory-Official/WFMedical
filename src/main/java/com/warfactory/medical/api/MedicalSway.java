package com.warfactory.medical.api;

import com.warfactory.medical.config.MedicalConfig;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Client pain-sway yield. Any suppressor true => WFMedical writes no pain sway for that player; the suppressing
 * mod applies its own from {@link MedicalState#painSwayIntensity}, {@link MedicalState#hasArmFracture},
 * {@link MedicalState#hasArmTourniquet} and {@link #amplitudeDegrees}.
 */
public final class MedicalSway {

    private static final float MAX_SWAY_DEG = 2.5F;

    @SuppressWarnings("unchecked")
    private static volatile Predicate<Player>[] suppressors = new Predicate[0];

    private MedicalSway() {
    }

    public static synchronized void suppress(Predicate<Player> suppressor) {
        Predicate<Player>[] next = Arrays.copyOf(suppressors, suppressors.length + 1);
        next[suppressors.length] = suppressor;
        suppressors = next;
    }

    public static boolean suppressed(Player player) {
        for (Predicate<Player> p : suppressors) {
            if (p.test(player)) {
                return true;
            }
        }
        return false;
    }

    /** Peak sway angle at intensity 1. */
    public static float amplitudeDegrees() {
        return MAX_SWAY_DEG * (float) MedicalConfig.painSwayStrength();
    }
}
