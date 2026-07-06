package com.warfactory.medical.config;

import com.warfactory.medical.core.PhysiologyParams;
import com.warfactory.medical.core.damage.HitRegMode;
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

    /**
     * The public spec; integrators may register this directly if they prefer.
     */
    public static final ForgeConfigSpec SPEC;

    // --- raw config values -------------------------------------------------
    private static final ForgeConfigSpec.IntValue UPDATE_INTERVAL_TICKS;
    private static final ForgeConfigSpec.IntValue MAX_HEALTH_HEARTS;
    private static final ForgeConfigSpec.DoubleValue MAX_BLOOD_ML;
    private static final ForgeConfigSpec.DoubleValue BLOOD_LOW_FRACTION;
    private static final ForgeConfigSpec.DoubleValue BLOOD_CRITICAL_FRACTION;
    private static final ForgeConfigSpec.DoubleValue BLOOD_DEATH_LOSS_FRACTION;
    private static final ForgeConfigSpec.DoubleValue BLOOD_UNCONSCIOUS_LOSS_FRACTION;
    private static final ForgeConfigSpec.DoubleValue PAIN_SHOCK_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue PAIN_UNCONSCIOUS_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue PAIN_UNCONSCIOUS_WEIGHT;
    private static final ForgeConfigSpec.BooleanValue ENABLE_FRACTURES;
    private static final ForgeConfigSpec.BooleanValue ENABLE_BLEEDING;
    private static final ForgeConfigSpec.BooleanValue ENABLE_PAIN;
    private static final ForgeConfigSpec.BooleanValue ENABLE_BLEEDOUT;
    private static final ForgeConfigSpec.IntValue BLEEDOUT_TICKS;
    private static final ForgeConfigSpec.BooleanValue LETHAL_BLOWS_ENABLED;
    private static final ForgeConfigSpec.DoubleValue LETHAL_BLOW_HEALTH_FRACTION;
    private static final ForgeConfigSpec.BooleanValue FINISH_DOWNED_ON_HIT;
    private static final ForgeConfigSpec.BooleanValue EFFECT_IMMUNE_IN_CREATIVE;
    private static final ForgeConfigSpec.IntValue MAX_TRAUMA_PER_LIMB;
    private static final ForgeConfigSpec.DoubleValue LEG_FRACTURE_SPEED_MULTIPLIER;
    private static final ForgeConfigSpec.BooleanValue ENABLE_INJECTABLES;
    private static final ForgeConfigSpec.DoubleValue DRUG_DECAY_PER_TICK;
    private static final ForgeConfigSpec.BooleanValue OVERDOSE_LETHAL_ENABLED;
    private static final ForgeConfigSpec.DoubleValue OVERDOSE_LETHAL_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue OVERDOSE_LETHAL_DRAIN_PER_TICK;
    private static final ForgeConfigSpec.BooleanValue ASPHYXIA_ENABLED;
    private static final ForgeConfigSpec.DoubleValue ASPHYXIA_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue ASPHYXIA_CHANCE;
    private static final ForgeConfigSpec.IntValue ASPHYXIA_AIR_LOSS_PER_TICK;
    private static final ForgeConfigSpec.IntValue ASPHYXIA_UNCONSCIOUS_TICKS;
    private static final ForgeConfigSpec.IntValue ASPHYXIA_WEAKNESS_AMPLIFIER;
    private static final ForgeConfigSpec.BooleanValue GEOMETRIC_HIT_LOCATION;
    private static final ForgeConfigSpec.BooleanValue POSE_AWARE_ARMS;
    private static final ForgeConfigSpec.DoubleValue HEAD_BAND_BOTTOM;
    private static final ForgeConfigSpec.DoubleValue LEG_BAND_TOP;
    private static final ForgeConfigSpec.DoubleValue ARM_SIDE_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue MELEE_REACH;
    private static final ForgeConfigSpec.BooleanValue RIGGED_LIMB_BOXES;
    private static final ForgeConfigSpec.DoubleValue LIMB_BOX_PADDING;
    private static final ForgeConfigSpec.BooleanValue OPEN_PERSISTENCE_COMPAT;
    private static final ForgeConfigSpec.BooleanValue TACZ_ARM_POSE;
    private static final ForgeConfigSpec.EnumValue<HitRegMode> HITREG_MODE;
    private static final ForgeConfigSpec.DoubleValue HIT_ENVELOPE_INFLATION;

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
        BLOOD_DEATH_LOSS_FRACTION = b
                .comment("Fraction of total blood volume that, once LOST, kills the player outright (bleeding out "
                        + "totally). Default 0.40 = losing more than 40% of your blood is instantly fatal.")
                .defineInRange("bloodDeathLossFraction", 0.40D, 0.05D, 1.0D);
        BLOOD_UNCONSCIOUS_LOSS_FRACTION = b
                .comment("Fraction of total blood volume LOST at which blood loss starts contributing to the "
                        + "unconsciousness score. Between this and bloodDeathLossFraction (default 30%-40% loss) "
                        + "blood loss ramps that score from 0 to 1; combined with pain it can knock the player out.")
                .defineInRange("bloodUnconsciousLossFraction", 0.30D, 0.0D, 1.0D);
        PAIN_SHOCK_THRESHOLD = b
                .comment("Pain (0..1) above which pain-shock penalties begin.")
                .defineInRange("painShockThreshold", 0.60D, 0.0D, 1.0D);
        PAIN_UNCONSCIOUS_THRESHOLD = b
                .comment("Perceived pain (0..1) above which pain begins contributing to the unconsciousness score. "
                        + "Severe pain past this point pushes the player toward passing out.")
                .defineInRange("painUnconsciousThreshold", 0.70D, 0.0D, 1.0D);
        PAIN_UNCONSCIOUS_WEIGHT = b
                .comment("How much FULLY-saturated pain (1.0) contributes to the unconsciousness score. 1.0 means "
                        + "max pain alone can knock you out; 0.5 (default) means pain can only tip you over when "
                        + "combined with blood loss. The score is the sum of the blood-loss and pain contributions; "
                        + "reaching 1.0 on any recalculation instantly renders the player unconscious.")
                .defineInRange("painUnconsciousWeight", 0.50D, 0.0D, 4.0D);
        b.pop();

        b.push("features");
        ENABLE_FRACTURES = b.comment("Master toggle for fracture trauma.").define("enableFractures", true);
        ENABLE_BLEEDING = b.comment("Master toggle for bleeding / blood loss.").define("enableBleeding", true);
        ENABLE_PAIN = b.comment("Master toggle for the pain system.").define("enablePain", true);
        ENABLE_BLEEDOUT = b.comment("If true, lethal conditions render the player unconscious (bleed-out) instead of instant death.").define("enableBleedout", true);
        EFFECT_IMMUNE_IN_CREATIVE = b.comment("Creative-mode players ignore medical penalties.").define("effectImmuneInCreative", true);
        ENABLE_INJECTABLES = b.comment("Master toggle for the injectable/opioid substance system (morphine, naloxone, ...).").define("enableInjectables", true);
        ASPHYXIA_ENABLED = b
                .comment("If true, a heavy opioid overdose can trigger ASPHYXIA (respiratory depression) before "
                        + "unconsciousness: air drains rapidly (sped-up drowning) with weakness, no sprint and "
                        + "blurred vision, ending in consciousness loss when the air runs out.")
                .define("enableAsphyxia", true);
        b.pop();

        b.push("balance");
        BLEEDOUT_TICKS = b
                .comment("Ticks a player may remain unconscious from bleeding out before dying.")
                .defineInRange("bleedoutTicks", 2400, 20, 72000);
        LETHAL_BLOWS_ENABLED = b
                .comment("If true, a single blow big enough to deplete the player's current health kills them "
                        + "outright (kill on impact) instead of dropping them into a survivable unconsciousness. "
                        + "Unconsciousness then only happens from GRADUAL depletion (bleeding/accumulated trauma), "
                        + "an overdose, or an admin override -- it is no longer a mandatory step before every death.")
                .define("lethalBlowsEnabled", true);
        LETHAL_BLOW_HEALTH_FRACTION = b
                .comment("A blow is 'lethal on impact' when its damage is at least this fraction of the player's "
                        + "current (derived) health. 1.0 = the hit must fully deplete remaining health; lower = "
                        + "even near-fatal hits execute. Only consulted when lethalBlowsEnabled is true.")
                .defineInRange("lethalBlowHealthFraction", 1.0D, 0.1D, 10.0D);
        FINISH_DOWNED_ON_HIT = b
                .comment("If true, any real damage taken while already unconscious/downed finishes the player "
                        + "(they can be killed while helpless). If false, a downed player is immune to further "
                        + "combat damage and can only die from the bleed-out timer.")
                .define("finishDownedOnHit", true);
        MAX_TRAUMA_PER_LIMB = b
                .comment("Hard cap on distinct trauma objects per limb; excess compatible trauma is merged.")
                .defineInRange("maxTraumaPerLimb", 8, 1, 64);
        LEG_FRACTURE_SPEED_MULTIPLIER = b
                .comment("Movement speed multiplier applied per fractured leg (1.0 = no penalty).")
                .defineInRange("legFractureSpeedMultiplier", 0.40D, 0.0D, 1.0D);
        DRUG_DECAY_PER_TICK = b
                .comment("How much injectable drug load decays per tick (higher = shorter dosing window before it clears).")
                .defineInRange("drugDecayPerTick", 0.0005D, 0.0D, 1.0D);
        OVERDOSE_LETHAL_ENABLED = b
                .comment("If true, a severe overdose (drug load >= overdoseLethalThreshold) drains health during the overdose unconsciousness.")
                .define("overdoseLethalEnabled", true);
        OVERDOSE_LETHAL_THRESHOLD = b
                .comment("Drug load at/above which an overdose unconsciousness also causes a slow respiratory-depression health drain.")
                .defineInRange("overdoseLethalThreshold", 1.6D, 0.0D, 100.0D);
        OVERDOSE_LETHAL_DRAIN_PER_TICK = b
                .comment("Health points drained per tick during a severe (lethal-threshold) overdose unconsciousness. "
                        + "Tuned so a single severe dose-stack (drug load ~2.0) drains a full-health player before "
                        + "the load decays back below the lethal threshold, yet leaves time for naloxone to reverse it.")
                .defineInRange("overdoseLethalDrainPerTick", 0.05D, 0.0D, 20.0D);
        ASPHYXIA_THRESHOLD = b
                .comment("Drug load at/above which an overdose can trigger asphyxia. Keep it at or below the "
                        + "substance overdose threshold (default 1.0 = morphine's) so the overdose that crosses "
                        + "it is eligible; raising it means only heavier overshoots asphyxiate. A severe overdose "
                        + "(>= overdoseLethalThreshold) always skips asphyxia and blacks out immediately.")
                .defineInRange("asphyxiaThreshold", 1.0D, 0.0D, 100.0D);
        ASPHYXIA_CHANCE = b
                .comment("Probability (0..1) that crossing the asphyxia threshold on an injection triggers asphyxia "
                        + "instead of an immediate overdose unconsciousness.")
                .defineInRange("asphyxiaChance", 0.35D, 0.0D, 1.0D);
        ASPHYXIA_AIR_LOSS_PER_TICK = b
                .comment("Air supply units drained per tick while asphyxiating (vanilla max air is 300). Higher = a "
                        + "faster suffocation; net drain also has to overcome vanilla's on-land air regen.")
                .defineInRange("asphyxiaAirLossPerTick", 12, 1, 300);
        ASPHYXIA_UNCONSCIOUS_TICKS = b
                .comment("Ticks the player stays unconscious after passing out from asphyxia.")
                .defineInRange("asphyxiaUnconsciousTicks", 200, 1, 72000);
        ASPHYXIA_WEAKNESS_AMPLIFIER = b
                .comment("Amplifier of the Weakness effect applied while asphyxiating (0 = Weakness I, 1 = Weakness II, ...).")
                .defineInRange("asphyxiaWeaknessAmplifier", 1, 0, 9);
        b.pop();

        b.push("hitlocation");
        GEOMETRIC_HIT_LOCATION = b
                .comment("Master switch. If true, incoming hits are mapped to a limb by projecting the reconstructed "
                        + "hit position onto the victim's body geometry (deterministic). Off -> legacy weighted "
                        + "random sampler everywhere.")
                .define("geometricHitLocation", true);
        POSE_AWARE_ARMS = b
                .comment("If true, a victim actively aiming/using a weapon reassigns frontal-upper hits to the "
                        + "raised arm instead of the torso.")
                .define("poseAwareArms", true);
        HEAD_BAND_BOTTOM = b
                .comment("Fraction (0..1) of body height, measured from the feet, at/above which a hit counts as the head.")
                .defineInRange("headBandBottom", 0.74D, 0.0D, 1.0D);
        LEG_BAND_TOP = b
                .comment("Fraction (0..1) of body height, measured from the feet, at/below which a hit counts as a leg.")
                .defineInRange("legBandTop", 0.40D, 0.0D, 1.0D);
        ARM_SIDE_THRESHOLD = b
                .comment("Normalized horizontal offset (|nx|, 0..1 of the box half-width) at/above which a "
                        + "torso-height hit is redirected to an arm.")
                .defineInRange("armSideThreshold", 0.80D, 0.0D, 1.0D);
        MELEE_REACH = b
                .comment("Melee aim-ray length in blocks used when reconstructing the geometric hit location for melee attacks.")
                .defineInRange("meleeReach", 3.0D, 0.0D, 8.0D);
        RIGGED_LIMB_BOXES = b
                .comment("If true, hits on players are classified against a server-side replica of the humanoid pose "
                        + "(six oriented limb boxes posed as the renderer would pose them), so aiming/crouch/swing "
                        + "arm positions are exact. Off -> fall back to the banded-AABB hit location.")
                .define("riggedLimbBoxes", true);
        LIMB_BOX_PADDING = b
                .comment("Amount (in blocks) each rigged limb box is inflated to absorb pose-replica drift versus "
                        + "vanilla/mod animations.")
                .defineInRange("limbBoxPadding", 0.02D, 0.0D, 0.5D);
        b.pop();

        b.push("compat");
        OPEN_PERSISTENCE_COMPAT = b
                .comment("If true and Open Persistence is installed, a player's persistent logout body carries their "
                        + "medical profile: it inherits their trauma on logout, accrues new trauma when hit while they "
                        + "are offline, and the (possibly worse) state is restored to the player on login. The body "
                        + "keeps vanilla health while offline -- there is no live physiology/bleed-out tick on it.")
                .define("openPersistenceCompat", true);
        TACZ_ARM_POSE = b
                .comment("If true and TACZ is installed, a player holding a TACZ gun poses the rig's arms with the "
                        + "baked TACZ third-person hold/ADS pose (driven by the gun's SYNCED aiming progress) instead "
                        + "of the generic raised-forward approximation, so arm hits while aiming a gun land correctly.")
                .define("taczArmPose", true);
        b.pop();

        b.push("hitregistration");
        HITREG_MODE = b
                .comment("How incoming attacks are registered against players / persistent bodies:",
                        "  OFF      - vanilla: the ray clips the tight collision box; arms (which render outside",
                        "             it) and prone bodies never register.",
                        "  ENVELOPE - the hit-scan box is widened to the model silhouette so arm / prone hits",
                        "             register (forgiving: a shot through the gap between an arm and the torso",
                        "             still counts). Near-zero cost; collision/physics are unaffected.",
                        "  PRECISE  - ENVELOPE registration, then a shot that actually threaded a gap between the",
                        "             rigged limb boxes is rejected (whiffs). A centre-mass hit is a cheap tight-box",
                        "             fast-path, so only grazing arm-margin shots ever build the rig.")
                .defineEnum("hitRegistrationMode", HitRegMode.ENVELOPE);
        HIT_ENVELOPE_INFLATION = b
                .comment("Blocks the hit-scan box is widened (X/Z) to reach the arms for ENVELOPE / PRECISE "
                        + "registration. ~0.15-0.2 covers the vanilla arm overhang.")
                .defineInRange("hitEnvelopeInflation", 0.15D, 0.0D, 1.0D);
        b.pop();

        SPEC = b.build();
    }

    private MedicalConfig() {
    }

    /**
     * Registers {@link #SPEC} as this mod's COMMON config.
     */
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

    /**
     * Baseline max health in points (hearts * 2).
     */
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

    public static double bloodDeathLossFraction() {
        return BLOOD_DEATH_LOSS_FRACTION.get();
    }

    public static double bloodUnconsciousLossFraction() {
        return BLOOD_UNCONSCIOUS_LOSS_FRACTION.get();
    }

    public static float painShockThreshold() {
        return PAIN_SHOCK_THRESHOLD.get().floatValue();
    }

    public static float painUnconsciousThreshold() {
        return PAIN_UNCONSCIOUS_THRESHOLD.get().floatValue();
    }

    public static float painUnconsciousWeight() {
        return PAIN_UNCONSCIOUS_WEIGHT.get().floatValue();
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

    public static boolean enableBleedout() {
        return ENABLE_BLEEDOUT.get();
    }

    public static int bleedoutTicks() {
        return BLEEDOUT_TICKS.get();
    }

    /**
     * If true, a single blow that would deplete the player's current health kills on impact instead of
     * dropping them into a survivable unconsciousness.
     */
    public static boolean lethalBlowsEnabled() {
        return LETHAL_BLOWS_ENABLED.get();
    }

    /**
     * Fraction of current health a single blow must deal to count as a lethal (kill-on-impact) blow.
     */
    public static double lethalBlowHealthFraction() {
        return LETHAL_BLOW_HEALTH_FRACTION.get();
    }

    /**
     * If true, any real damage taken while already downed finishes the player.
     */
    public static boolean finishDownedOnHit() {
        return FINISH_DOWNED_ON_HIT.get();
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
     * Master toggle for the injectable/opioid substance system.
     */
    public static boolean enableInjectables() {
        return ENABLE_INJECTABLES.get();
    }

    /**
     * How much injectable drug load decays per tick.
     */
    public static double drugDecayPerTick() {
        return DRUG_DECAY_PER_TICK.get();
    }

    /**
     * If true, a severe overdose drains health during the overdose unconsciousness.
     */
    public static boolean overdoseLethalEnabled() {
        return OVERDOSE_LETHAL_ENABLED.get();
    }

    /**
     * Drug load at/above which an overdose unconsciousness also causes a respiratory-depression health drain.
     */
    public static double overdoseLethalThreshold() {
        return OVERDOSE_LETHAL_THRESHOLD.get();
    }

    /**
     * Health points drained per tick during a severe overdose unconsciousness.
     */
    public static double overdoseLethalDrainPerTick() {
        return OVERDOSE_LETHAL_DRAIN_PER_TICK.get();
    }

    /**
     * Master toggle for the overdose asphyxia (respiratory-depression) phase.
     */
    public static boolean asphyxiaEnabled() {
        return ASPHYXIA_ENABLED.get();
    }

    /**
     * Drug load at/above which a heavy overdose can trigger asphyxia.
     */
    public static double asphyxiaThreshold() {
        return ASPHYXIA_THRESHOLD.get();
    }

    /**
     * Probability (0..1) that a qualifying overdose injection triggers asphyxia instead of instant unconsciousness.
     */
    public static double asphyxiaChance() {
        return ASPHYXIA_CHANCE.get();
    }

    /**
     * Air supply units drained per tick while asphyxiating (sped-up drowning).
     */
    public static int asphyxiaAirLossPerTick() {
        return ASPHYXIA_AIR_LOSS_PER_TICK.get();
    }

    /**
     * Ticks the player stays unconscious after passing out from asphyxia.
     */
    public static int asphyxiaUnconsciousTicks() {
        return ASPHYXIA_UNCONSCIOUS_TICKS.get();
    }

    /**
     * Amplifier of the Weakness effect applied while asphyxiating (0 = Weakness I).
     */
    public static int asphyxiaWeaknessAmplifier() {
        return ASPHYXIA_WEAKNESS_AMPLIFIER.get();
    }

    /**
     * Master switch for the geometric (deterministic) hit-location system. Off -> legacy weighted sampler.
     */
    public static boolean geometricHitLocation() {
        return GEOMETRIC_HIT_LOCATION.get();
    }

    /**
     * If true, an actively aiming victim has frontal-upper hits reassigned to the raised arm.
     */
    public static boolean poseAwareArms() {
        return POSE_AWARE_ARMS.get();
    }

    /**
     * Fraction (0..1) of body height at/above which a hit is classified as the head.
     */
    public static double headBandBottom() {
        return HEAD_BAND_BOTTOM.get();
    }

    /**
     * Fraction (0..1) of body height at/below which a hit is classified as a leg.
     */
    public static double legBandTop() {
        return LEG_BAND_TOP.get();
    }

    /**
     * Normalized horizontal offset (|nx|, 0..1) at/above which a torso-height hit is redirected to an arm.
     */
    public static double armSideThreshold() {
        return ARM_SIDE_THRESHOLD.get();
    }

    /**
     * Melee aim-ray length in blocks used when reconstructing the geometric hit location.
     */
    public static double meleeReach() {
        return MELEE_REACH.get();
    }

    /**
     * If true, player hits are classified against the server-side rigged limb boxes (Tier 2); otherwise
     * the banded-AABB hit location is used.
     */
    public static boolean riggedLimbBoxes() {
        return RIGGED_LIMB_BOXES.get();
    }

    /**
     * Amount (in blocks) each rigged limb box is inflated to absorb pose-replica drift.
     */
    public static double limbBoxPadding() {
        return LIMB_BOX_PADDING.get();
    }

    /**
     * If true (and Open Persistence is present), persistent logout bodies carry/accrue the owner's medical
     * profile and round-trip it player&harr;body on logout/login.
     */
    public static boolean openPersistenceCompat() {
        return OPEN_PERSISTENCE_COMPAT.get();
    }

    /**
     * If true (and TACZ is present), a held TACZ gun poses the rig's arms with the baked TACZ third-person
     * pose driven by synced aiming progress, instead of the generic raised-forward approximation.
     */
    public static boolean taczArmPose() {
        return TACZ_ARM_POSE.get();
    }

    /**
     * How incoming attacks are registered against players / bodies (OFF = vanilla tight box, ENVELOPE = model
     * silhouette, PRECISE = envelope + rig gap-rejection).
     */
    public static HitRegMode hitRegistrationMode() {
        return HITREG_MODE.get();
    }

    /**
     * Blocks the hit-scan box is widened (X/Z) to reach the arms for ENVELOPE / PRECISE registration.
     */
    public static double hitEnvelopeInflation() {
        return HIT_ENVELOPE_INFLATION.get();
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
                enableBleedout(),
                bleedoutTicks(),
                bloodDeathLossFraction(),
                bloodUnconsciousLossFraction(),
                painUnconsciousThreshold(),
                painUnconsciousWeight()
        );
    }
}
