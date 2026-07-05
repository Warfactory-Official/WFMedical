package com.warfactory.medical.config;

import com.warfactory.medical.core.PhysiologyParams;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * COMMON-side {@link ForgeConfigSpec} holding the medical system's numeric tunables.
 *
 * <p>These are the "engine knobs" (update cadence, blood volume, thresholds, feature toggles). The
 * data-driven trauma/treatment definitions live separately in {@link MedicalDefinitions}. Everything
 * here is server-authoritative; the client reads a synced snapshot.</p>
 *
 * <p>Register with {@link #register(ModLoadingContext)} during mod construction, or register
 * {@link #SPEC} directly ({@code context.registerConfig(ModConfig.Type.COMMON, MedicalConfig.SPEC)}).</p>
 */
public final class MedicalConfig {

    /** The public spec; integrators may register this directly if they prefer. */
    public static final ForgeConfigSpec SPEC;

    // --- raw config values -------------------------------------------------
    private static final ForgeConfigSpec.IntValue UPDATE_INTERVAL_TICKS;
    private static final ForgeConfigSpec.IntValue MAX_HEALTH_HEARTS;
    private static final ForgeConfigSpec.DoubleValue MAX_BLOOD_ML;
    private static final ForgeConfigSpec.DoubleValue BLOOD_LOW_FRACTION;
    private static final ForgeConfigSpec.DoubleValue BLOOD_CRITICAL_FRACTION;
    private static final ForgeConfigSpec.DoubleValue PAIN_SHOCK_THRESHOLD;
    private static final ForgeConfigSpec.BooleanValue ENABLE_FRACTURES;
    private static final ForgeConfigSpec.BooleanValue ENABLE_BLEEDING;
    private static final ForgeConfigSpec.BooleanValue ENABLE_PAIN;
    private static final ForgeConfigSpec.BooleanValue ENABLE_KNOCKDOWN;
    private static final ForgeConfigSpec.IntValue KNOCKDOWN_BLEEDOUT_TICKS;
    private static final ForgeConfigSpec.BooleanValue EFFECT_IMMUNE_IN_CREATIVE;
    private static final ForgeConfigSpec.IntValue MAX_TRAUMA_PER_LIMB;
    private static final ForgeConfigSpec.DoubleValue LEG_FRACTURE_SPEED_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("physiology");
        UPDATE_INTERVAL_TICKS = b
                .comment("How often (in ticks) dirty players' physiology is recomputed. Lower = more responsive, higher = cheaper.")
                .defineInRange("updateIntervalTicks", 10, 1, 40);
        MAX_HEALTH_HEARTS = b
                .comment("Baseline maximum health in hearts (1 heart = 2 health points).")
                .defineInRange("maxHealthHearts", 15, 1, 512);
        MAX_BLOOD_ML = b
                .comment("Total blood volume in millilitres.")
                .defineInRange("maxBloodMl", 5000.0D, 100.0D, 100000.0D);
        BLOOD_LOW_FRACTION = b
                .comment("Blood fraction (0..1) below which blood-loss penalties begin.")
                .defineInRange("bloodLowFraction", 0.60D, 0.0D, 1.0D);
        BLOOD_CRITICAL_FRACTION = b
                .comment("Blood fraction (0..1) below which the player is critical.")
                .defineInRange("bloodCriticalFraction", 0.35D, 0.0D, 1.0D);
        PAIN_SHOCK_THRESHOLD = b
                .comment("Pain (0..1) above which pain-shock penalties begin.")
                .defineInRange("painShockThreshold", 0.60D, 0.0D, 1.0D);
        b.pop();

        b.push("features");
        ENABLE_FRACTURES = b.comment("Master toggle for fracture trauma.").define("enableFractures", true);
        ENABLE_BLEEDING = b.comment("Master toggle for bleeding / blood loss.").define("enableBleeding", true);
        ENABLE_PAIN = b.comment("Master toggle for the pain system.").define("enablePain", true);
        ENABLE_KNOCKDOWN = b.comment("If true, lethal conditions knock the player down instead of instant death.").define("enableKnockdown", true);
        EFFECT_IMMUNE_IN_CREATIVE = b.comment("Creative-mode players ignore medical penalties.").define("effectImmuneInCreative", true);
        b.pop();

        b.push("balance");
        KNOCKDOWN_BLEEDOUT_TICKS = b
                .comment("Ticks a player may remain knocked down before bleeding out and dying.")
                .defineInRange("knockdownBleedoutTicks", 2400, 20, 72000);
        MAX_TRAUMA_PER_LIMB = b
                .comment("Hard cap on distinct trauma objects per limb; excess compatible trauma is merged.")
                .defineInRange("maxTraumaPerLimb", 8, 1, 64);
        LEG_FRACTURE_SPEED_MULTIPLIER = b
                .comment("Movement speed multiplier applied per fractured leg (1.0 = no penalty).")
                .defineInRange("legFractureSpeedMultiplier", 0.40D, 0.0D, 1.0D);
        b.pop();

        SPEC = b.build();
    }

    private MedicalConfig() {
    }

    /** Registers {@link #SPEC} as this mod's COMMON config. */
    public static void register(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    // --- typed getters -----------------------------------------------------

    public static int updateIntervalTicks() {
        return UPDATE_INTERVAL_TICKS.get();
    }

    public static int maxHealthHearts() {
        return MAX_HEALTH_HEARTS.get();
    }

    /** Baseline max health in points (hearts * 2). */
    public static float maxHealthPoints() {
        return MAX_HEALTH_HEARTS.get() * 2.0F;
    }

    public static double maxBloodMl() {
        return MAX_BLOOD_ML.get();
    }

    public static double bloodLowFraction() {
        return BLOOD_LOW_FRACTION.get();
    }

    public static double bloodCriticalFraction() {
        return BLOOD_CRITICAL_FRACTION.get();
    }

    public static float painShockThreshold() {
        return PAIN_SHOCK_THRESHOLD.get().floatValue();
    }

    public static boolean enableFractures() {
        return ENABLE_FRACTURES.get();
    }

    public static boolean enableBleeding() {
        return ENABLE_BLEEDING.get();
    }

    public static boolean enablePain() {
        return ENABLE_PAIN.get();
    }

    public static boolean enableKnockdown() {
        return ENABLE_KNOCKDOWN.get();
    }

    public static int knockdownBleedoutTicks() {
        return KNOCKDOWN_BLEEDOUT_TICKS.get();
    }

    public static boolean effectImmuneInCreative() {
        return EFFECT_IMMUNE_IN_CREATIVE.get();
    }

    public static int maxTraumaPerLimb() {
        return MAX_TRAUMA_PER_LIMB.get();
    }

    public static float legFractureSpeedMultiplier() {
        return LEG_FRACTURE_SPEED_MULTIPLIER.get().floatValue();
    }

    /**
     * Builds an immutable {@link PhysiologyParams} snapshot from the current config values, for the
     * pure core to consume. Fields not exposed as tunables fall back to the core defaults.
     */
    public static PhysiologyParams toPhysiologyParams() {
        PhysiologyParams d = PhysiologyParams.defaults();
        return new PhysiologyParams(
                maxHealthPoints(),
                maxBloodMl(),
                bloodLowFraction(),
                bloodCriticalFraction(),
                d.bloodDeathMl(),
                painShockThreshold(),
                d.painMaxHealthPenalty(),
                legFractureSpeedMultiplier(),
                d.painSpeedFloor(),
                enableKnockdown(),
                knockdownBleedoutTicks()
        );
    }
}
