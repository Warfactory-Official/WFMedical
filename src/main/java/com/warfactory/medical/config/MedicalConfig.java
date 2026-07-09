package com.warfactory.medical.config;

import com.warfactory.medical.core.PhysiologyParams;
import com.warfactory.medical.core.damage.DamageCategory;
import com.warfactory.medical.core.damage.HitAuthority;
import com.warfactory.medical.core.damage.HitRegMode;
import com.warfactory.medical.core.damage.rig.RigTuning;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * COMMON-side {@link ForgeConfigSpec} holding the medical system's numeric tunables (engine knobs:
 * update cadence, blood volume, thresholds, feature toggles). Data-driven trauma/treatment definitions
 * live in {@link MedicalDefinitions}. Everything here is server-authoritative.
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
    private static final ForgeConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_DEFAULT;
    private static final ForgeConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_BALLISTIC;
    private static final ForgeConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_EXPLOSION;
    private static final ForgeConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_BLUNT;
    private static final ForgeConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_UNARMED;
    private static final ForgeConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_FALL;
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
    private static final ForgeConfigSpec.BooleanValue DROWNING_ASPHYXIA_ENABLED;
    private static final ForgeConfigSpec.IntValue ASPHYXIA_STRUGGLE_TICKS;
    private static final ForgeConfigSpec.DoubleValue ASPHYXIA_MOVE_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue STIMULANT_SPEED_BONUS;
    private static final ForgeConfigSpec.DoubleValue CLOTTING_BOOST_THRESHOLD_BONUS;
    private static final ForgeConfigSpec.DoubleValue CLOTTING_BOOST_RATE_MULTIPLIER;
    private static final ForgeConfigSpec.IntValue CLOTTING_AGENT_DURATION_TICKS;
    private static final ForgeConfigSpec.BooleanValue GEOMETRIC_HIT_LOCATION;
    private static final ForgeConfigSpec.BooleanValue POSE_AWARE_ARMS;
    private static final ForgeConfigSpec.DoubleValue HEAD_BAND_BOTTOM;
    private static final ForgeConfigSpec.DoubleValue LEG_BAND_TOP;
    private static final ForgeConfigSpec.DoubleValue ARM_SIDE_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue MELEE_REACH;
    private static final ForgeConfigSpec.BooleanValue RIGGED_LIMB_BOXES;
    private static final ForgeConfigSpec.DoubleValue LIMB_BOX_PADDING;
    private static final ForgeConfigSpec.BooleanValue HITBOX_DEBUG;
    private static final ForgeConfigSpec.BooleanValue OPEN_PERSISTENCE_COMPAT;
    private static final ForgeConfigSpec.BooleanValue TACZ_ARM_POSE;
    private static final ForgeConfigSpec.EnumValue<HitRegMode> HITREG_MODE;
    /**
     * Per-stance broad-phase envelope reach, indexed by {@link RigTuning.RigPose#ordinal()}: horizontal (X/Z)
     * and vertical (Y).
     */
    private static final ForgeConfigSpec.DoubleValue[] ENV_REACH_H;
    private static final ForgeConfigSpec.DoubleValue[] ENV_REACH_V;
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
    private static final ForgeConfigSpec.DoubleValue HEALTH_SHARE_HEAD;
    private static final ForgeConfigSpec.DoubleValue HEALTH_SHARE_TORSO;
    private static final ForgeConfigSpec.DoubleValue HEALTH_SHARE_ARM;
    private static final ForgeConfigSpec.DoubleValue HEALTH_SHARE_LEG;
    private static final ForgeConfigSpec.DoubleValue TOURNIQUET_BLEED_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue TOURNIQUET_LEG_SPEED_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue TOURNIQUET_ARM_SPEED_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue TOURNIQUET_ARM_SWAY;
    private static final ForgeConfigSpec.BooleanValue ADRENALINE_ENABLED;
    private static final ForgeConfigSpec.IntValue ADRENALINE_PAIN_KO_DELAY_TICKS;
    private static final ForgeConfigSpec.EnumValue<HitAuthority> HIT_AUTHORITY;
    private static final ForgeConfigSpec.IntValue POSE_STREAM_MIN_INTERVAL_TICKS;
    private static final ForgeConfigSpec.IntValue POSE_STREAM_MAX_INTERVAL_TICKS;
    private static final ForgeConfigSpec.IntValue POSE_HINT_MAX_AGE_TICKS;
    private static final ForgeConfigSpec.DoubleValue POSE_HINT_MARGIN;
    private static final ForgeConfigSpec.BooleanValue PENETRATION_ENABLED;
    private static final ForgeConfigSpec.DoubleValue PENETRATION_BUDGET;
    private static final ForgeConfigSpec.DoubleValue PENETRATION_ENERGY_FALLOFF;
    private static final ForgeConfigSpec.DoubleValue PEN_RESIST_HEAD;
    private static final ForgeConfigSpec.DoubleValue PEN_RESIST_TORSO;
    private static final ForgeConfigSpec.DoubleValue PEN_RESIST_ARM;
    private static final ForgeConfigSpec.DoubleValue PEN_RESIST_LEG;

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
        HEALTH_SHARE_HEAD = b
                .comment("Max SHARE of the FULL health bar a fully-destroyed HEAD can remove from the life pool. "
                        + "Per-limb CAPS so one limb can never drain the whole pool; a limb that reaches its cap is "
                        + "'drained' (disabled + fractured) and further damage overflows into bleeding. Default 0.35.")
                .defineInRange("healthShareHead", 0.35D, 0.0D, 1.0D);
        HEALTH_SHARE_TORSO = b
                .comment("Max SHARE of the FULL health bar a fully-destroyed TORSO can remove. The torso carries "
                        + "most of the life pool. Default 0.55.")
                .defineInRange("healthShareTorso", 0.55D, 0.0D, 1.0D);
        HEALTH_SHARE_ARM = b
                .comment("Max SHARE of the FULL health bar a fully-destroyed ARM can remove (per arm). Small: an arm "
                        + "cannot cost your life directly, but a drained arm is disabled and overflows into bleeding. "
                        + "Default 0.12.")
                .defineInRange("healthShareArm", 0.12D, 0.0D, 1.0D);
        HEALTH_SHARE_LEG = b
                .comment("Max SHARE of the FULL health bar a fully-destroyed LEG can remove (per leg). A drained leg "
                        + "is disabled; both legs drained forces a crawl. Default 0.18.")
                .defineInRange("healthShareLeg", 0.18D, 0.0D, 1.0D);
        TOURNIQUET_BLEED_MULTIPLIER = b
                .comment("Multiplier applied to a limb's bleeding while a TOURNIQUET is on it (arms/legs only). "
                        + "Lower = a tourniquet slows blood loss more; it never fully stops it and does NOT treat "
                        + "the underlying wound (remove it and full bleeding returns). Default 0.20.")
                .defineInRange("tourniquetBleedMultiplier", 0.20D, 0.0D, 1.0D);
        TOURNIQUET_LEG_SPEED_MULTIPLIER = b
                .comment("Movement multiplier applied PER LEG wearing a tourniquet (0.85 = 15% slower per leg) "
                        + "so leaving them on permanently is discouraged. Default 0.85.")
                .defineInRange("tourniquetLegSpeedMultiplier", 0.85D, 0.0D, 1.0D);
        TOURNIQUET_ARM_SPEED_MULTIPLIER = b
                .comment("Movement multiplier applied PER ARM wearing a tourniquet (minor). Default 0.95.")
                .defineInRange("tourniquetArmSpeedMultiplier", 0.95D, 0.0D, 1.0D);
        TOURNIQUET_ARM_SWAY = b
                .comment("Weapon-sway intensity floor (0..1, like pain sway) while ANY arm wears a tourniquet. "
                        + "Default 0.30.")
                .defineInRange("tourniquetArmSway", 0.30D, 0.0D, 1.0D);
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
                .comment("If true, a heavy opioid overdose can trigger ASPHYXIA (respiratory depression): heavy "
                        + "movement constraint, weakness, no sprint/jump and a blur + heavy vignette, ending in a "
                        + "FATAL unconsciousness unless the drug is reversed (naloxone) or decays in time.")
                .define("enableAsphyxia", true);
        DROWNING_ASPHYXIA_ENABLED = b
                .comment("If true, going underwater with no air is handled as ASPHYXIA (drowning) instead of "
                        + "vanilla drowning damage: once your breath runs out you struggle, then pass out and "
                        + "DROWN TO DEATH unless you reach the surface in time. Vanilla drowning damage is suppressed.")
                .define("enableDrowningAsphyxia", true);
        b.pop();

        b.push("balance");
        BLEEDOUT_TICKS = b
                .comment("Ticks a player may remain unconscious from bleeding out before dying.")
                .defineInRange("bleedoutTicks", 2400, 20, 72000);
        b.comment("MAJOR TRAUMA (instant death): a hit kills outright -- before it could become a survivable",
                        "unconsciousness -- when its damage reaches this fraction of the player's FULL healthy health",
                        "bar (maxHealthHearts) AND the medical armor model did not BLOCK it. Per damage category so a",
                        "sniper, a blast and a fall tune apart; categories without their own entry use",
                        "majorTraumaFractionDefault. Fire/chemical/radiation never instant-kill on impact (they are",
                        "damage-over-time). Set an entry high (e.g. 100) to effectively disable instant death for it.")
                .push("lethality");
        MAJOR_TRAUMA_FRACTION_DEFAULT = b
                .comment("Default fraction for categories without a specific entry (melee slashing, piercing, generic).")
                .defineInRange("majorTraumaFractionDefault", 1.0D, 0.1D, 100.0D);
        MAJOR_TRAUMA_FRACTION_BALLISTIC = b
                .comment("Firearms/bullets -- a full healthy bar's worth of bullet in one hit kills outright.")
                .defineInRange("majorTraumaFractionBallistic", 0.9D, 0.1D, 100.0D);
        MAJOR_TRAUMA_FRACTION_EXPLOSION = b
                .comment("Blasts.")
                .defineInRange("majorTraumaFractionExplosion", 0.9D, 0.1D, 100.0D);
        MAJOR_TRAUMA_FRACTION_BLUNT = b
                .comment("Heavy impact/crushing (falling blocks, anvils, wall slams).")
                .defineInRange("majorTraumaFractionBlunt", 1.1D, 0.1D, 100.0D);
        MAJOR_TRAUMA_FRACTION_UNARMED = b
                .comment("Bare-handed strikes -- set high so punches essentially never one-shot.")
                .defineInRange("majorTraumaFractionUnarmed", 3.0D, 0.1D, 100.0D);
        MAJOR_TRAUMA_FRACTION_FALL = b
                .comment("Falls -- 1.0 means a fall dealing a full health bar (~33 blocks) instant-kills; "
                        + "shorter falls crush/fracture legs and can bleed you out instead.")
                .defineInRange("majorTraumaFractionFall", 1.0D, 0.1D, 100.0D);
        b.pop();
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
                .comment("How much injectable drug load decays per tick (higher = shorter dosing window before it "
                        + "clears). Lower values make the drug 'stat' come-down outlast a stimulant's beneficial "
                        + "effect window. Default 0.00035.")
                .defineInRange("drugDecayPerTick", 0.00035D, 0.0D, 1.0D);
        STIMULANT_SPEED_BONUS = b
                .comment("Movement-speed bonus fraction added at FULL stimulant strength (0.30 = +30% speed). A "
                        + "combat stimulant also overrides injury slowdown and clears the jump penalty while active.")
                .defineInRange("stimulantSpeedBonus", 0.30D, 0.0D, 5.0D);
        CLOTTING_BOOST_THRESHOLD_BONUS = b
                .comment("How much a FULL clotting boost raises the wound severity that can self-clot without a "
                        + "bandage, ADDED to bleedingSelfHealThreshold (0.70 -> a full boost lets even a severe "
                        + "bleed close on its own). Default 0.70.")
                .defineInRange("clottingBoostThresholdBonus", 0.70D, 0.0D, 1.0D);
        CLOTTING_BOOST_RATE_MULTIPLIER = b
                .comment("How much FASTER a boosted wound self-clots: the self-heal rate is multiplied by "
                        + "(1 + clottingBoost * this). 10.0 -> up to 11x the normal clot speed at full boost. Default 10.0.")
                .defineInRange("clottingBoostRateMultiplier", 10.0D, 0.0D, 100.0D);
        CLOTTING_AGENT_DURATION_TICKS = b
                .comment("How long (ticks) the clotting boost from a hemostatic BOOST_CLOTTING item lasts "
                        + "(20 ticks = 1 second). Default 2400 (2 minutes).")
                .defineInRange("clottingAgentDurationTicks", 2400, 1, 72000);
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
                .comment("Ticks after passing out from asphyxia before it turns FATAL. If the cause is not cleared "
                        + "in this window (surface / reverse the drug), the player dies. Default 200 (10 seconds).")
                .defineInRange("asphyxiaUnconsciousTicks", 200, 1, 72000);
        ASPHYXIA_WEAKNESS_AMPLIFIER = b
                .comment("Amplifier of the Weakness effect applied while asphyxiating (0 = Weakness I, 1 = Weakness II, ...).")
                .defineInRange("asphyxiaWeaknessAmplifier", 1, 0, 9);
        ASPHYXIA_STRUGGLE_TICKS = b
                .comment("Ticks a player consciously struggles for air (heavily slowed, blurred) once asphyxia "
                        + "begins, before passing out. Clearing the cause in this window recovers cleanly. "
                        + "Default 60 (3 seconds).")
                .defineInRange("asphyxiaStruggleTicks", 60, 1, 12000);
        ASPHYXIA_MOVE_MULTIPLIER = b
                .comment("Movement-speed multiplier while consciously asphyxiating (heavy constraint). Sprint and "
                        + "jump are also blocked. Default 0.25 = a quarter speed.")
                .defineInRange("asphyxiaMoveMultiplier", 0.25D, 0.0D, 1.0D);
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
        HITBOX_DEBUG = b
                .comment("DEBUG/TEST tool. If true, the rigged limb boxes become live-tunable: use",
                        "'/wfmedical hitbox set|add <limb> <field> <value>' to nudge each box's position/size (in",
                        "model units, 1/16 block) while watching the hitbox overlay (K key), then bake the dialled-in",
                        "numbers back into HumanoidRig.BASE with '/wfmedical hitbox export'. Off (the default) has ZERO",
                        "runtime cost -- the boxes are built straight from their fixed base spec, no tuning applied.",
                        "The '/wfmedical hitbox debug on|off' command flips this live for a session without a reload.")
                .define("hitboxDebug", false);
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
                        "  ENVELOPE - the hit-scan box is widened by a fixed margin (hitEnvelopeReach*) so arm /",
                        "             prone hits register (forgiving: a shot through the gap between an arm and the",
                        "             torso still counts). Near-zero cost; collision/physics are unaffected.",
                        "  PRECISE  - ENVELOPE registration, then a shot that actually threaded a gap between the",
                        "             rigged limb boxes is rejected (whiffs). A centre-mass hit is a cheap tight-box",
                        "             fast-path, so only grazing arm-margin shots ever build the rig.")
                .defineEnum("hitRegistrationMode", HitRegMode.ENVELOPE);
        b.comment("Per-STANCE broad-phase envelope: blocks the hit-scan box is widened for each pose so arm /",
                        "prone hits register. Horizontal = X/Z per side, Vertical = Y top+bottom. Size each to just",
                        "contain the model in that stance -- the vanilla box already shrinks while crouching/swimming,",
                        "and the fine-phase per-limb test rejects any surplus, so over-sizing only costs a few extra",
                        "fine tests while under-sizing drops hits. Tune live with '/wfmedical hitbox envelope ...' and",
                        "bake the dialled-in numbers back here.")
                .push("envelopeReach");
        // Defaults per RigPose (ordinal order STANDING, CROUCHING, PRONE, DOWNED): {horizontal, vertical}.
        // Upright/crouch only need the arm overhang; the body-horizontal prone/downed silhouettes reach far.
        double[][] envDefaults = {{0.4D, 0.2D}, {0.5D, 0.1D}, {1.0D, 0.3D}, {1.0D, 0.3D}};
        ForgeConfigSpec.DoubleValue[] envH = new ForgeConfigSpec.DoubleValue[RigTuning.RigPose.VALUES.length];
        ForgeConfigSpec.DoubleValue[] envV = new ForgeConfigSpec.DoubleValue[RigTuning.RigPose.VALUES.length];
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            int i = pose.ordinal();
            String n = pose.lower();
            envH[i] = b.comment("Horizontal (X/Z) envelope reach for the " + n + " stance.")
                    .defineInRange(n + "Horizontal", envDefaults[i][0], 0.0D, 4.0D);
            envV[i] = b.comment("Vertical (Y) envelope reach for the " + n + " stance.")
                    .defineInRange(n + "Vertical", envDefaults[i][1], 0.0D, 4.0D);
        }
        ENV_REACH_H = envH;
        ENV_REACH_V = envV;
        b.pop();
        b.pop();

        b.comment("Who computes the posed limb rig used to classify a player hit. Purely a performance /",
                        "authority trade -- the medical outcome is identical either way.",
                        "  SERVER      - the server rebuilds the victim's rig itself (authoritative, deterministic).",
                        "                Backed by a per-tick cache so repeated hits in one tick cost one rebuild.",
                        "  CLIENT_HINT - the victim's client streams its own posed rig; the server still runs the ray",
                        "                test ITSELF (an attacker can never pick the limb) but skips the costly rebuild,",
                        "                validating the supplied pose against a cheap bound and falling back to a server",
                        "                rebuild whenever the hint is absent, stale, or implausible. Trades a little",
                        "                per-player bandwidth for server CPU on very large servers.")
                .push("authority");
        HIT_AUTHORITY = b
                .comment("SERVER (default, authoritative) or CLIENT_HINT (victim streams its pose; server validates).")
                .defineEnum("hitAuthority", HitAuthority.SERVER);
        POSE_STREAM_MIN_INTERVAL_TICKS = b
                .comment("CLIENT_HINT only: the victim's client will not send its pose more often than this many "
                        + "ticks, even while moving (rate limit). Default 2.")
                .defineInRange("poseStreamMinIntervalTicks", 2, 1, 40);
        POSE_STREAM_MAX_INTERVAL_TICKS = b
                .comment("CLIENT_HINT only: the victim's client resends its pose at least this often even when it "
                        + "has not changed (heartbeat), so the server's copy never goes stale under poseHintMaxAge"
                        + "Ticks. Keep below poseHintMaxAgeTicks. Default 10.")
                .defineInRange("poseStreamMaxIntervalTicks", 10, 1, 200);
        POSE_HINT_MAX_AGE_TICKS = b
                .comment("CLIENT_HINT only: the server treats a streamed pose older than this (in ticks) as stale "
                        + "and rebuilds the rig itself for that hit. Default 30 (1.5s).")
                .defineInRange("poseHintMaxAgeTicks", 30, 1, 200);
        POSE_HINT_MARGIN = b
                .comment("CLIENT_HINT only: slack (blocks) added to the victim's bounding box when validating a "
                        + "streamed pose. A supplied limb box whose centre falls outside the box+margin (or whose "
                        + "size is implausible) is rejected and the server rebuilds instead. Guards against a client "
                        + "shrinking/displacing its own hitboxes. Default 0.6.")
                .defineInRange("poseHintMargin", 0.6D, 0.0D, 4.0D);
        b.pop();

        b.comment("PENETRATION (through-and-through): when on, a traced shot can wound EVERY rigged limb box it",
                        "passes through (e.g. a raised arm AND the torso behind it), not just the nearest. The nearest",
                        "limb is still the PRIMARY hit (full damage, can be lethal, identical to penetration-off); each",
                        "further limb the shot exits into takes a declining share of trauma and never instant-kills.",
                        "Only ray-like sources (bullets, arrows, melee aim) penetrate; explosions / positional hits do",
                        "not. Off (default) is byte-identical to the single-limb behaviour.")
                .push("penetration");
        PENETRATION_ENABLED = b
                .comment("Master toggle for through-and-through multi-limb wounding.")
                .define("penetrationEnabled", false);
        PENETRATION_BUDGET = b
                .comment("How much limb resistance one shot can punch through. As the ray crosses each limb it spends "
                        + "that limb's penetrationResist*; once the budget is used up the shot stops and no deeper "
                        + "limb is wounded. The first (nearest) limb is always hit regardless of budget. Default 1.0.")
                .defineInRange("penetrationBudget", 1.0D, 0.0D, 10.0D);
        PENETRATION_ENERGY_FALLOFF = b
                .comment("Trauma-energy multiplier applied per limb already pierced: limb N gets energy * "
                        + "falloff^N. 0.5 -> the second limb takes half, the third a quarter, etc. Default 0.5.")
                .defineInRange("penetrationEnergyFalloff", 0.5D, 0.0D, 1.0D);
        PEN_RESIST_HEAD = b.comment("Penetration resistance of the HEAD (budget spent passing through it).")
                .defineInRange("penetrationResistHead", 0.5D, 0.0D, 10.0D);
        PEN_RESIST_TORSO = b.comment("Penetration resistance of the TORSO (dense -> stops a shot soonest).")
                .defineInRange("penetrationResistTorso", 0.8D, 0.0D, 10.0D);
        PEN_RESIST_ARM = b.comment("Penetration resistance of an ARM (thin -> a shot passes through readily).")
                .defineInRange("penetrationResistArm", 0.25D, 0.0D, 10.0D);
        PEN_RESIST_LEG = b.comment("Penetration resistance of a LEG.")
                .defineInRange("penetrationResistLeg", 0.4D, 0.0D, 10.0D);
        b.pop();

        SPEC = b.build();
    }

    private MedicalConfig() {
    }

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

    public static float healthShareHead() {
        return HEALTH_SHARE_HEAD.get().floatValue();
    }

    public static float healthShareTorso() {
        return HEALTH_SHARE_TORSO.get().floatValue();
    }

    public static float healthShareArm() {
        return HEALTH_SHARE_ARM.get().floatValue();
    }

    public static float healthShareLeg() {
        return HEALTH_SHARE_LEG.get().floatValue();
    }

    /**
     * Max share of the FULL health bar a fully-destroyed limb of this type can remove (per-limb cap). Mirrors
     * {@link PhysiologyParams#healthShare}; used at damage time to detect a "drained" limb.
     */
    public static float healthShare(LimbType lt) {
        if (lt == LimbType.HEAD) {
            return healthShareHead();
        }
        if (lt == LimbType.TORSO) {
            return healthShareTorso();
        }
        return lt.isLeg() ? healthShareLeg() : healthShareArm();
    }

    /**
     * Multiplier applied to a limb's bleeding while a tourniquet is on it (slows blood loss, never treats).
     */
    public static float tourniquetBleedMultiplier() {
        return TOURNIQUET_BLEED_MULTIPLIER.get().floatValue();
    }

    public static float tourniquetLegSpeedMultiplier() {
        return TOURNIQUET_LEG_SPEED_MULTIPLIER.get().floatValue();
    }

    public static float tourniquetArmSpeedMultiplier() {
        return TOURNIQUET_ARM_SPEED_MULTIPLIER.get().floatValue();
    }

    /**
     * Weapon-sway intensity floor (0..1) while any arm wears a tourniquet (read client-side).
     */
    public static double tourniquetArmSway() {
        return TOURNIQUET_ARM_SWAY.get();
    }

    /**
     * If true, a purely pain-driven knockout is delayed by the adrenaline grace timer.
     */
    public static boolean adrenalineEnabled() {
        return ADRENALINE_ENABLED.get();
    }

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
     * Fraction of the player's FULL healthy health bar a single (non-blocked) hit of the given category must
     * deal to be a MAJOR TRAUMA that kills on impact. Per-category; unlisted categories use the default.
     */
    public static double majorTraumaFraction(DamageCategory cat) {
        if (cat == null) {
            return MAJOR_TRAUMA_FRACTION_DEFAULT.get();
        }
        return switch (cat) {
            case BALLISTIC -> MAJOR_TRAUMA_FRACTION_BALLISTIC.get();
            case EXPLOSION -> MAJOR_TRAUMA_FRACTION_EXPLOSION.get();
            case BLUNT -> MAJOR_TRAUMA_FRACTION_BLUNT.get();
            case UNARMED -> MAJOR_TRAUMA_FRACTION_UNARMED.get();
            case FALL -> MAJOR_TRAUMA_FRACTION_FALL.get();
            default -> MAJOR_TRAUMA_FRACTION_DEFAULT.get();
        };
    }

    /**
     * Whether a damage category can ever cause instant death on impact. Fire/chemical/radiation are
     * damage-over-time conditions and never instant-kill from a single hit.
     */
    public static boolean canInstakillOnImpact(DamageCategory cat) {
        return cat != DamageCategory.FIRE && cat != DamageCategory.CHEMICAL && cat != DamageCategory.RADIATION;
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

    public static boolean enableInjectables() {
        return ENABLE_INJECTABLES.get();
    }

    public static double drugDecayPerTick() {
        return DRUG_DECAY_PER_TICK.get();
    }

    public static float stimulantSpeedBonus() {
        return STIMULANT_SPEED_BONUS.get().floatValue();
    }

    public static double clottingBoostThresholdBonus() {
        return CLOTTING_BOOST_THRESHOLD_BONUS.get();
    }

    public static double clottingBoostRateMultiplier() {
        return CLOTTING_BOOST_RATE_MULTIPLIER.get();
    }

    public static int clottingAgentDurationTicks() {
        return CLOTTING_AGENT_DURATION_TICKS.get();
    }

    public static boolean overdoseLethalEnabled() {
        return OVERDOSE_LETHAL_ENABLED.get();
    }

    public static double overdoseLethalThreshold() {
        return OVERDOSE_LETHAL_THRESHOLD.get();
    }

    public static double overdoseLethalDrainPerTick() {
        return OVERDOSE_LETHAL_DRAIN_PER_TICK.get();
    }

    public static boolean asphyxiaEnabled() {
        return ASPHYXIA_ENABLED.get();
    }

    public static double asphyxiaThreshold() {
        return ASPHYXIA_THRESHOLD.get();
    }

    public static double asphyxiaChance() {
        return ASPHYXIA_CHANCE.get();
    }

    public static int asphyxiaAirLossPerTick() {
        return ASPHYXIA_AIR_LOSS_PER_TICK.get();
    }

    public static int asphyxiaUnconsciousTicks() {
        return ASPHYXIA_UNCONSCIOUS_TICKS.get();
    }

    /**
     * Amplifier of the Weakness effect applied while asphyxiating (0 = Weakness I).
     */
    public static int asphyxiaWeaknessAmplifier() {
        return ASPHYXIA_WEAKNESS_AMPLIFIER.get();
    }

    public static boolean drowningAsphyxiaEnabled() {
        return DROWNING_ASPHYXIA_ENABLED.get();
    }

    public static int asphyxiaStruggleTicks() {
        return ASPHYXIA_STRUGGLE_TICKS.get();
    }

    public static float asphyxiaMoveMultiplier() {
        return ASPHYXIA_MOVE_MULTIPLIER.get().floatValue();
    }

    /**
     * Master switch for the geometric hit-location system; off = legacy weighted sampler.
     */
    public static boolean geometricHitLocation() {
        return GEOMETRIC_HIT_LOCATION.get();
    }

    public static boolean poseAwareArms() {
        return POSE_AWARE_ARMS.get();
    }

    public static double headBandBottom() {
        return HEAD_BAND_BOTTOM.get();
    }

    public static double legBandTop() {
        return LEG_BAND_TOP.get();
    }

    /**
     * Normalized horizontal offset (|nx|) at/above which a torso-height hit is redirected to an arm.
     */
    public static double armSideThreshold() {
        return ARM_SIDE_THRESHOLD.get();
    }

    public static double meleeReach() {
        return MELEE_REACH.get();
    }

    /**
     * If true, player hits use server-side rigged limb boxes (Tier 2); otherwise banded-AABB.
     */
    public static boolean riggedLimbBoxes() {
        return RIGGED_LIMB_BOXES.get();
    }

    /**
     * Inflation (blocks) applied to each rigged limb box to absorb pose-replica drift.
     */
    public static double limbBoxPadding() {
        return LIMB_BOX_PADDING.get();
    }

    /**
     * If true, the rigged limb boxes are live-tunable via {@code /wfmedical hitbox} (debug/test only). Mirrored
     * into {@link com.warfactory.medical.core.damage.rig.RigTuning#ACTIVE} at config load; off = zero cost.
     */
    public static boolean hitboxDebug() {
        return HITBOX_DEBUG.get();
    }

    public static boolean openPersistenceCompat() {
        return OPEN_PERSISTENCE_COMPAT.get();
    }

    public static boolean taczArmPose() {
        return TACZ_ARM_POSE.get();
    }

    /**
     * OFF = vanilla box, ENVELOPE = model silhouette, PRECISE = envelope + rig gap-rejection.
     */
    public static HitRegMode hitRegistrationMode() {
        return HITREG_MODE.get();
    }

    /**
     * Horizontal (X/Z) envelope reach the hit-scan box is widened by for the given stance.
     */
    public static double hitEnvelopeReachHorizontal(RigTuning.RigPose pose) {
        return ENV_REACH_H[pose.ordinal()].get();
    }

    /**
     * Vertical (Y) envelope reach the hit-scan box is widened by for the given stance.
     */
    public static double hitEnvelopeReachVertical(RigTuning.RigPose pose) {
        return ENV_REACH_V[pose.ordinal()].get();
    }

    /**
     * Snapshot of the per-stance envelope reach as a flat {@code [pose*2 + axis]} array (axis 0 = horizontal,
     * 1 = vertical), for seeding {@link RigTuning#seedEnvelope} at config load/reload.
     */
    public static double[] envelopeReachSnapshot() {
        double[] a = new double[RigTuning.RigPose.VALUES.length * 2];
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            a[pose.ordinal() * 2] = ENV_REACH_H[pose.ordinal()].get();
            a[pose.ordinal() * 2 + 1] = ENV_REACH_V[pose.ordinal()].get();
        }
        return a;
    }

    /**
     * SERVER (server rebuilds the rig) or CLIENT_HINT (victim streams its pose; server validates + ray-tests).
     */
    public static HitAuthority hitAuthority() {
        return HIT_AUTHORITY.get();
    }

    /**
     * CLIENT_HINT: minimum ticks between pose sends from a victim's client (rate limit).
     */
    public static int poseStreamMinIntervalTicks() {
        return POSE_STREAM_MIN_INTERVAL_TICKS.get();
    }

    /**
     * CLIENT_HINT: heartbeat interval (ticks) at which the victim resends its pose even when unchanged.
     */
    public static int poseStreamMaxIntervalTicks() {
        return POSE_STREAM_MAX_INTERVAL_TICKS.get();
    }

    /**
     * CLIENT_HINT: age (ticks) past which a streamed pose is stale and the server rebuilds instead.
     */
    public static int poseHintMaxAgeTicks() {
        return POSE_HINT_MAX_AGE_TICKS.get();
    }

    /**
     * CLIENT_HINT: bounding-box slack (blocks) when validating a streamed pose.
     */
    public static double poseHintMargin() {
        return POSE_HINT_MARGIN.get();
    }

    /**
     * Master toggle for through-and-through multi-limb wounding (R1 penetration passthrough).
     */
    public static boolean penetrationEnabled() {
        return PENETRATION_ENABLED.get();
    }

    /**
     * How much total limb resistance one shot can punch through before it stops.
     */
    public static double penetrationBudget() {
        return PENETRATION_BUDGET.get();
    }

    /**
     * Trauma-energy multiplier applied per already-pierced limb (limb N gets energy * falloff^N).
     */
    public static double penetrationEnergyFalloff() {
        return PENETRATION_ENERGY_FALLOFF.get();
    }

    /**
     * Penetration resistance of a limb: budget spent as the ray passes through it. Torso is densest.
     */
    public static double penetrationResistance(LimbType lt) {
        if (lt == LimbType.HEAD) {
            return PEN_RESIST_HEAD.get();
        }
        if (lt == LimbType.TORSO) {
            return PEN_RESIST_TORSO.get();
        }
        return lt.isLeg() ? PEN_RESIST_LEG.get() : PEN_RESIST_ARM.get();
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
                adrenalineEnabled(),
                asphyxiaMoveMultiplier(),
                stimulantSpeedBonus(),
                healthShareHead(),
                healthShareTorso(),
                healthShareArm(),
                healthShareLeg(),
                tourniquetBleedMultiplier(),
                tourniquetLegSpeedMultiplier(),
                tourniquetArmSpeedMultiplier()
        );
    }
}
