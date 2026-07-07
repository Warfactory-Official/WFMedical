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
    private static final ForgeConfigSpec.DoubleValue BLOOD_MOVEMENT_PENALTY_LOSS_FRACTION;
    private static final ForgeConfigSpec.DoubleValue BLEEDING_SELF_HEAL_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue BLEEDING_SELF_HEAL_RATE;
    private static final ForgeConfigSpec.DoubleValue FRACTURE_SELF_HEAL_MINUTES;
    private static final ForgeConfigSpec.DoubleValue UNARMED_MAJOR_CHANCE;
    private static final ForgeConfigSpec.BooleanValue PAIN_SWAY_ENABLED;
    private static final ForgeConfigSpec.DoubleValue PAIN_SWAY_STRENGTH;
    private static final ForgeConfigSpec.DoubleValue BROKEN_ARM_AIM_SWAY;
    private static final ForgeConfigSpec.IntValue BROKEN_ARM_MELEE_WEAKNESS_LEVEL;
    private static final ForgeConfigSpec.DoubleValue PAIN_SATURATION_K;
    private static final ForgeConfigSpec.DoubleValue PAIN_SHARE_HEAD;
    private static final ForgeConfigSpec.DoubleValue PAIN_SHARE_TORSO;
    private static final ForgeConfigSpec.DoubleValue PAIN_SHARE_ARM;
    private static final ForgeConfigSpec.DoubleValue PAIN_SHARE_LEG;
    private static final ForgeConfigSpec.BooleanValue ADRENALINE_ENABLED;
    private static final ForgeConfigSpec.IntValue ADRENALINE_PAIN_KO_DELAY_TICKS;

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
                .comment("Weight of the pain factor in the unconsciousness score. The score is the SUM of an "
                        + "independent blood-loss factor (reaches 1.0 at bloodUnconsciousLossFraction) and a pain "
                        + "factor (reaches 1.0 at painUnconsciousThreshold, then scaled by this weight); reaching "
                        + "1.0 total knocks the player out. 1.0 (default) = severe pain alone can down you; lower "
                        + "= pain only contributes toward a combined blood-loss + pain knockout.")
                .defineInRange("painUnconsciousWeight", 1.00D, 0.0D, 4.0D);
        BLOOD_MOVEMENT_PENALTY_LOSS_FRACTION = b
                .comment("Fraction of total blood LOST above which walk/jump speed is penalised (ramping to the "
                        + "pain-speed floor at the death loss). Non-leg injuries and general pain never affect "
                        + "speed; only leg injuries and blood loss past this threshold do. Default 0.25 = 25% lost.")
                .defineInRange("bloodMovementPenaltyLossFraction", 0.25D, 0.0D, 1.0D);
        BLEEDING_SELF_HEAL_THRESHOLD = b
                .comment("Severity (0..1) at or below which an UNTREATED bleeding wound slowly clots and closes on "
                        + "its own (natural hemostasis). A more severe bleed cannot be stopped by the body alone "
                        + "and keeps bleeding (worsening) until treated. Default 0.30.")
                .defineInRange("bleedingSelfHealThreshold", 0.30D, 0.0D, 1.0D);
        BLEEDING_SELF_HEAL_RATE = b
                .comment("Severity reduction PER TICK applied to a self-clotting wound (one at or below the "
                        + "self-heal threshold). Small = the wound takes a long while to close. Default 0.0003.")
                .defineInRange("bleedingSelfHealRate", 0.0003D, 0.0D, 1.0D);
        FRACTURE_SELF_HEAL_MINUTES = b
                .comment("Real-time minutes for a full-severity fracture to knit and heal ON ITS OWN while "
                        + "untreated (a partial fracture heals proportionally faster). Splinting/treating it is "
                        + "faster. 0 = fractures never self-heal (they worsen until treated). Default 20.")
                .defineInRange("fractureSelfHealMinutes", 20.0D, 0.0D, 600.0D);
        b.pop();

        b.push("pain");
        PAIN_SATURATION_K = b
                .comment("Per-limb pain diminishing-returns constant. A limb's LOCAL pain (0..1) = raw / (raw + "
                        + "k): a smaller k saturates a limb's pain faster, a larger k needs more/worse wounds on "
                        + "the SAME limb to approach that limb's share cap. Default 1.0.")
                .defineInRange("painSaturationK", 1.0D, 0.05D, 20.0D);
        PAIN_SHARE_HEAD = b
                .comment("Max SHARE of total pain (0..1) a fully-painful HEAD can contribute to the SYSTEMIC pain "
                        + "that drives shock / unconsciousness. Per-limb shares are caps and may sum ABOVE 1.0 "
                        + "(the total is clamped), so COMBINATIONS cause shock while no single limb can. Default 0.35.")
                .defineInRange("painShareHead", 0.35D, 0.0D, 1.0D);
        PAIN_SHARE_TORSO = b
                .comment("Max SHARE of total pain (0..1) a fully-painful TORSO can contribute. The torso carries "
                        + "most of the shock-inducing weight; at the default it sits just under the shock "
                        + "threshold, so torso trauma plus one more injury tips into shock. Default 0.50.")
                .defineInRange("painShareTorso", 0.50D, 0.0D, 1.0D);
        PAIN_SHARE_ARM = b
                .comment("Max SHARE of total pain (0..1) a fully-painful ARM can contribute (per arm). Deliberately "
                        + "small: an agonising arm still hurts (aim sway / screen effects) but cannot, by itself, "
                        + "put you into shock. Default 0.10.")
                .defineInRange("painShareArm", 0.10D, 0.0D, 1.0D);
        PAIN_SHARE_LEG = b
                .comment("Max SHARE of total pain (0..1) a fully-painful LEG can contribute (per leg). Femoral "
                        + "trauma is genuinely shock-grade, so legs weigh more than arms. Default 0.20.")
                .defineInRange("painShareLeg", 0.20D, 0.0D, 1.0D);
        ADRENALINE_ENABLED = b
                .comment("If true, a PURELY pain-driven knockout (one that blood loss alone would not cause) is "
                        + "held off for adrenalinePainKoDelayTicks, mimicking adrenaline: the player keeps their "
                        + "feet through the pain before finally collapsing. Blood-loss knockouts are never delayed.")
                .define("adrenalineEnabled", true);
        ADRENALINE_PAIN_KO_DELAY_TICKS = b
                .comment("Ticks a pain-driven knockout is delayed by adrenaline once pain reaches knockout level "
                        + "(20 ticks = 1 second). If pain drops below that level within the window, adrenaline "
                        + "recharges and the timer resets. Default 120 (6 seconds).")
                .defineInRange("adrenalinePainKoDelayTicks", 120, 0, 12000);
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
        UNARMED_MAJOR_CHANCE = b
                .comment("Chance (0..1) that a bare-handed PUNCH to the torso or head produces a minor internal "
                        + "bleed (major trauma) instead of just a bruise. Punches to any other limb are always "
                        + "just bruising. Default 0.15.")
                .defineInRange("unarmedMajorChance", 0.15D, 0.0D, 1.0D);
        PAIN_SWAY_ENABLED = b
                .comment("If true, high pain makes the local player's aim drift/tremble (harder to aim). Client-side.")
                .define("painSwayEnabled", true);
        PAIN_SWAY_STRENGTH = b
                .comment("Multiplier on the pain aim-sway amplitude (0 = off, 1 = default, higher = shakier).")
                .defineInRange("painSwayStrength", 1.0D, 0.0D, 5.0D);
        BROKEN_ARM_AIM_SWAY = b
                .comment("Aim-sway intensity (0..1) forced while aiming a bow / crossbow / TACZ gun with a broken "
                        + "arm: a broken arm cannot hold a weapon steady, and ADS does not brace it away. 1.0 = "
                        + "full sway. Default 0.9.")
                .defineInRange("brokenArmAimSway", 0.90D, 0.0D, 1.0D);
        BROKEN_ARM_MELEE_WEAKNESS_LEVEL = b
                .comment("Weakness effect level applied to a player with a broken arm, weakening their MELEE "
                        + "attacks. 1 = Weakness I, 2 = Weakness II, etc.; 0 = disabled. Default 1.")
                .defineInRange("brokenArmMeleeWeaknessLevel", 1, 0, 10);
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

    public static float painSaturationK() {
        return PAIN_SATURATION_K.get().floatValue();
    }

    public static float painShareHead() {
        return PAIN_SHARE_HEAD.get().floatValue();
    }

    public static float painShareTorso() {
        return PAIN_SHARE_TORSO.get().floatValue();
    }

    public static float painShareArm() {
        return PAIN_SHARE_ARM.get().floatValue();
    }

    public static float painShareLeg() {
        return PAIN_SHARE_LEG.get().floatValue();
    }

    /**
     * If true, a purely pain-driven knockout is delayed by the adrenaline grace timer.
     */
    public static boolean adrenalineEnabled() {
        return ADRENALINE_ENABLED.get();
    }

    /**
     * Ticks a pain-driven knockout is held off by adrenaline once pain reaches knockout level.
     */
    public static int adrenalinePainKoDelayTicks() {
        return ADRENALINE_PAIN_KO_DELAY_TICKS.get();
    }

    public static double bloodMovementPenaltyLossFraction() {
        return BLOOD_MOVEMENT_PENALTY_LOSS_FRACTION.get();
    }

    public static double bleedingSelfHealThreshold() {
        return BLEEDING_SELF_HEAL_THRESHOLD.get();
    }

    public static double bleedingSelfHealRate() {
        return BLEEDING_SELF_HEAL_RATE.get();
    }

    public static double fractureSelfHealMinutes() {
        return FRACTURE_SELF_HEAL_MINUTES.get();
    }

    public static double unarmedMajorChance() {
        return UNARMED_MAJOR_CHANCE.get();
    }

    public static boolean painSwayEnabled() {
        return PAIN_SWAY_ENABLED.get();
    }

    public static double painSwayStrength() {
        return PAIN_SWAY_STRENGTH.get();
    }

    public static double brokenArmAimSway() {
        return BROKEN_ARM_AIM_SWAY.get();
    }

    public static int brokenArmMeleeWeaknessLevel() {
        return BROKEN_ARM_MELEE_WEAKNESS_LEVEL.get();
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
                painUnconsciousWeight(),
                bloodMovementPenaltyLossFraction(),
                painShareHead(),
                painShareTorso(),
                painShareArm(),
                painShareLeg(),
                painSaturationK(),
                adrenalineEnabled()
        );
    }
}
