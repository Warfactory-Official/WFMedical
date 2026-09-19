package com.warfactory.medical.config;

import com.warfactory.medical.core.PhysiologyParams;
import com.warfactory.medical.core.damage.DamageCategory;
import com.warfactory.medical.core.damage.HitAuthority;
import com.warfactory.medical.core.damage.HitRegMode;
import com.warfactory.medical.core.damage.rig.RigTuning;
import com.warfactory.medical.core.limb.LimbType;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class MedicalConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue UPDATE_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue MAX_HEALTH_HEARTS;
    private static final ModConfigSpec.DoubleValue MAX_BLOOD_ML;
    private static final ModConfigSpec.DoubleValue BLOOD_LOW_FRACTION;
    private static final ModConfigSpec.DoubleValue BLOOD_CRITICAL_FRACTION;
    private static final ModConfigSpec.DoubleValue BLOOD_DEATH_LOSS_FRACTION;
    private static final ModConfigSpec.DoubleValue BLOOD_UNCONSCIOUS_LOSS_FRACTION;
    private static final ModConfigSpec.DoubleValue PAIN_SHOCK_THRESHOLD;
    private static final ModConfigSpec.DoubleValue PAIN_UNCONSCIOUS_THRESHOLD;
    private static final ModConfigSpec.DoubleValue PAIN_UNCONSCIOUS_WEIGHT;
    private static final ModConfigSpec.BooleanValue ENABLE_FRACTURES;
    private static final ModConfigSpec.BooleanValue ENABLE_BLEEDING;
    private static final ModConfigSpec.BooleanValue ENABLE_PAIN;
    private static final ModConfigSpec.BooleanValue ENABLE_BLEEDOUT;
    private static final ModConfigSpec.BooleanValue HEAD_DEPLETION_INSTAKILL;
    private static final ModConfigSpec.BooleanValue TORSO_DEPLETION_INSTAKILL;
    private static final ModConfigSpec.BooleanValue ENABLE_GIVE_UP;
    private static final ModConfigSpec.IntValue GIVE_UP_HOLD_TICKS;
    private static final ModConfigSpec.IntValue BLEEDOUT_TICKS;
    private static final ModConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_DEFAULT;
    private static final ModConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_BALLISTIC;
    private static final ModConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_EXPLOSION;
    private static final ModConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_BLUNT;
    private static final ModConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_UNARMED;
    private static final ModConfigSpec.DoubleValue MAJOR_TRAUMA_FRACTION_FALL;
    private static final ModConfigSpec.BooleanValue FINISH_DOWNED_ON_HIT;
    private static final ModConfigSpec.BooleanValue EFFECT_IMMUNE_IN_CREATIVE;
    private static final ModConfigSpec.BooleanValue MANAGE_NATURAL_REGEN;
    private static final ModConfigSpec.IntValue MAX_TRAUMA_PER_LIMB;
    private static final ModConfigSpec.DoubleValue LEG_FRACTURE_SPEED_MULTIPLIER;
    private static final ModConfigSpec.BooleanValue ENABLE_INJECTABLES;
    private static final ModConfigSpec.DoubleValue DRUG_DECAY_PER_TICK;
    private static final ModConfigSpec.BooleanValue OVERDOSE_LETHAL_ENABLED;
    private static final ModConfigSpec.DoubleValue OVERDOSE_LETHAL_THRESHOLD;
    private static final ModConfigSpec.DoubleValue OVERDOSE_LETHAL_DRAIN_PER_TICK;
    private static final ModConfigSpec.BooleanValue ASPHYXIA_ENABLED;
    private static final ModConfigSpec.DoubleValue ASPHYXIA_THRESHOLD;
    private static final ModConfigSpec.DoubleValue ASPHYXIA_CHANCE;
    private static final ModConfigSpec.IntValue ASPHYXIA_AIR_LOSS_PER_TICK;
    private static final ModConfigSpec.IntValue ASPHYXIA_UNCONSCIOUS_TICKS;
    private static final ModConfigSpec.IntValue ASPHYXIA_WEAKNESS_AMPLIFIER;
    private static final ModConfigSpec.BooleanValue DROWNING_ASPHYXIA_ENABLED;
    private static final ModConfigSpec.IntValue ASPHYXIA_STRUGGLE_TICKS;
    private static final ModConfigSpec.DoubleValue ASPHYXIA_MOVE_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue STIMULANT_SPEED_BONUS;
    private static final ModConfigSpec.DoubleValue CLOTTING_BOOST_THRESHOLD_BONUS;
    private static final ModConfigSpec.DoubleValue CLOTTING_BOOST_RATE_MULTIPLIER;
    private static final ModConfigSpec.IntValue CLOTTING_AGENT_DURATION_TICKS;
    private static final ModConfigSpec.IntValue DEATH_ATTRIBUTION_WINDOW_TICKS;
    private static final ModConfigSpec.DoubleValue TREAT_REACH_BLOCKS;
    private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> TREAT_SELF_ONLY_OVERRIDES;
    private static final ModConfigSpec.BooleanValue GEOMETRIC_HIT_LOCATION;
    private static final ModConfigSpec.BooleanValue POSE_AWARE_ARMS;
    private static final ModConfigSpec.DoubleValue HEAD_BAND_BOTTOM;
    private static final ModConfigSpec.DoubleValue LEG_BAND_TOP;
    private static final ModConfigSpec.DoubleValue ARM_SIDE_THRESHOLD;
    private static final ModConfigSpec.DoubleValue MELEE_REACH;
    private static final ModConfigSpec.BooleanValue RIGGED_LIMB_BOXES;
    private static final ModConfigSpec.DoubleValue LIMB_BOX_PADDING;
    private static final ModConfigSpec.BooleanValue HITBOX_DEBUG;
    private static final ModConfigSpec.BooleanValue LOG_HIT_DETECTION;
    private static final ModConfigSpec.BooleanValue LOG_MEDICAL_SYNC;
    private static final ModConfigSpec.IntValue SYNC_FULL_RESYNC_INTERVAL_TICKS;
    private static final ModConfigSpec.BooleanValue OPEN_PERSISTENCE_COMPAT;
    private static final ModConfigSpec.BooleanValue TACZ_ARM_POSE;
    private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> DAMAGE_SOURCE_CATEGORIES;
    private static final ModConfigSpec.EnumValue<HitRegMode> HITREG_MODE;
    private static final ModConfigSpec.DoubleValue HIT_GAP_REJECT_TOLERANCE;
    private static final ModConfigSpec.DoubleValue[] ENV_REACH_H;
    private static final ModConfigSpec.DoubleValue[] ENV_REACH_V;
    private static final ModConfigSpec.DoubleValue BLOOD_MOVEMENT_PENALTY_LOSS_FRACTION;
    private static final ModConfigSpec.DoubleValue BLEEDING_SELF_HEAL_THRESHOLD;
    private static final ModConfigSpec.DoubleValue BLEEDING_SELF_HEAL_RATE;
    private static final ModConfigSpec.DoubleValue FRACTURE_SELF_HEAL_MINUTES;
    private static final ModConfigSpec.DoubleValue BLOOD_REGEN_ML_PER_SECOND;
    private static final ModConfigSpec.DoubleValue UNARMED_MAJOR_CHANCE;
    private static final ModConfigSpec.DoubleValue FALL_FRACTURE_MIN_BLOCKS;
    private static final ModConfigSpec.BooleanValue PAIN_SWAY_ENABLED;
    private static final ModConfigSpec.DoubleValue PAIN_SWAY_STRENGTH;
    private static final ModConfigSpec.DoubleValue BROKEN_ARM_AIM_SWAY;
    private static final ModConfigSpec.IntValue BROKEN_ARM_MELEE_WEAKNESS_LEVEL;
    private static final ModConfigSpec.DoubleValue PAIN_SATURATION_K;
    private static final ModConfigSpec.DoubleValue PAIN_SHARE_HEAD;
    private static final ModConfigSpec.DoubleValue PAIN_SHARE_TORSO;
    private static final ModConfigSpec.DoubleValue PAIN_SHARE_ARM;
    private static final ModConfigSpec.DoubleValue PAIN_SHARE_LEG;
    private static final ModConfigSpec.DoubleValue HEALTH_SHARE_HEAD;
    private static final ModConfigSpec.DoubleValue HEALTH_SHARE_TORSO;
    private static final ModConfigSpec.DoubleValue HEALTH_SHARE_ARM;
    private static final ModConfigSpec.DoubleValue HEALTH_SHARE_LEG;
    private static final ModConfigSpec.DoubleValue TOURNIQUET_BLEED_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue BLEEDING_RATE_MULTIPLIER;
    private static final ModConfigSpec.IntValue RESUSCITATE_DURATION_TICKS;
    private static final ModConfigSpec.IntValue RESUSCITATE_GRACE_TICKS;
    private static final ModConfigSpec.DoubleValue RESUSCITATE_CHANCE_MIN;
    private static final ModConfigSpec.DoubleValue RESUSCITATE_CHANCE_MAX;
    private static final ModConfigSpec.DoubleValue RESUSCITATE_BLEED_REFERENCE;
    private static final ModConfigSpec.DoubleValue INTERNAL_BLEEDING_CHANCE;
    private static final ModConfigSpec.DoubleValue INTERNAL_BLEEDING_MIN_ENERGY;
    private static final ModConfigSpec.DoubleValue INTERNAL_BLEEDING_LIMB_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue INTERNAL_BLEEDING_EXPLOSION_MULTIPLIER;
    private static final ModConfigSpec.BooleanValue CARDIAC_OUTPUT_ENABLED;
    private static final ModConfigSpec.DoubleValue CARDIAC_VENOUS_RETURN_FLOOR;
    private static final ModConfigSpec.DoubleValue CARDIAC_OUTPUT_FLOOR;
    private static final ModConfigSpec.IntValue MAX_TRAUMAS_PER_HIT;
    private static final ModConfigSpec.BooleanValue HEART_RATE_ENABLED;
    private static final ModConfigSpec.DoubleValue HEART_RATE_RESTING;
    private static final ModConfigSpec.DoubleValue HEART_RATE_MAX;
    private static final ModConfigSpec.DoubleValue HEART_RATE_BLEED_INFLUENCE;
    private static final ModConfigSpec.DoubleValue HEART_RATE_COMPENSATION_RATIO;
    private static final ModConfigSpec.DoubleValue HEART_RATE_DECOMPENSATION_RATIO;
    private static final ModConfigSpec.DoubleValue HEART_RATE_PAIN_THRESHOLD;
    private static final ModConfigSpec.DoubleValue HEART_RATE_PAIN_GAIN;
    private static final ModConfigSpec.DoubleValue HEART_RATE_STIMULANT_BONUS;
    private static final ModConfigSpec.DoubleValue HEART_RATE_OPIOID_DROP;
    private static final ModConfigSpec.DoubleValue TOURNIQUET_LEG_SPEED_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue TOURNIQUET_ARM_SPEED_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue TOURNIQUET_ARM_SWAY;
    private static final ModConfigSpec.DoubleValue TOURNIQUET_RECOVERY_CHANCE;
    private static final ModConfigSpec.BooleanValue ADRENALINE_ENABLED;
    private static final ModConfigSpec.IntValue ADRENALINE_PAIN_KO_DELAY_TICKS;
    private static final ModConfigSpec.IntValue BLACKOUT_GRACE_TICKS;
    private static final ModConfigSpec.DoubleValue WAKE_CHANCE;
    private static final ModConfigSpec.DoubleValue WAKEUP_SCORE_THRESHOLD;
    private static final ModConfigSpec.DoubleValue WAKEUP_BLOOD_WEIGHT;
    private static final ModConfigSpec.DoubleValue WAKEUP_PAIN_WEIGHT;
    private static final ModConfigSpec.DoubleValue WAKEUP_DRUG_WEIGHT;
    private static final ModConfigSpec.DoubleValue WAKEUP_BLEED_WEIGHT;
    private static final ModConfigSpec.DoubleValue WAKEUP_BLEED_REFERENCE;
    private static final ModConfigSpec.EnumValue<HitAuthority> HIT_AUTHORITY;
    private static final ModConfigSpec.IntValue POSE_STREAM_MIN_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue POSE_STREAM_MAX_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue POSE_HINT_MAX_AGE_TICKS;
    private static final ModConfigSpec.DoubleValue POSE_HINT_MARGIN;
    private static final ModConfigSpec.BooleanValue ANIMATED_HITBOXES;
    private static final ModConfigSpec.DoubleValue POSE_STREAM_CHANGE_EPSILON;
    private static final ModConfigSpec.BooleanValue PENETRATION_ENABLED;
    private static final ModConfigSpec.DoubleValue PENETRATION_BUDGET;
    private static final ModConfigSpec.DoubleValue PENETRATION_ENERGY_FALLOFF;
    private static final ModConfigSpec.DoubleValue PEN_RESIST_HEAD;
    private static final ModConfigSpec.DoubleValue PEN_RESIST_TORSO;
    private static final ModConfigSpec.DoubleValue PEN_RESIST_ARM;
    private static final ModConfigSpec.DoubleValue PEN_RESIST_LEG;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

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
        BLOOD_REGEN_ML_PER_SECOND = b
                .comment("Millilitres of blood the body slowly regenerates PER SECOND on its own (natural "
                        + "haematopoiesis), climbing back toward maxBloodMl. Regen is PAUSED entirely while ANY "
                        + "wound is actively bleeding -- including one merely slowed by a tourniquet (which never "
                        + "fully stops the flow) -- so you must first stop the bleed (bandage / clot / suture) "
                        + "before the body starts rebuilding volume. Blood bags / medkits remain the fast way to "
                        + "restore volume. 0 = blood never regenerates on its own (only treatment restores it). "
                        + "Default 1.0 (topping up a full 5000 ml pool takes ~83 min).")
                .defineInRange("bloodRegenMlPerSecond", 1.0D, 0.0D, 1000.0D);
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
        BLEEDING_RATE_MULTIPLIER = b
                .comment("Global multiplier on ALL wound bleeding rates -- i.e. how fast blood (and thus life) "
                        + "drains from every wound at once. 1.0 = raw per-wound rates; 0.5 = bleed out half as fast. "
                        + "Default 0.50.")
                .defineInRange("bleedingRateMultiplier", 0.50D, 0.0D, 10.0D);
        RESUSCITATE_DURATION_TICKS = b
                .comment("How long one manual resuscitation attempt takes, in ticks (20 = 1s). It needs no item, so "
                        + "this is the whole cost of an attempt. Default 80 (4s).")
                .defineInRange("resuscitateDurationTicks", 80, 1, 12000);
        RESUSCITATE_GRACE_TICKS = b
                .comment("Ticks a just-revived casualty is held conscious even though their blood loss and pain would "
                        + "still put them down. Without this they fold again immediately, because they came up at the "
                        + "exact threshold that downed them. It never prevents bleeding out. Default 200 (10s).")
                .defineInRange("resuscitateGraceTicks", 200, 0, 12000);
        RESUSCITATE_CHANCE_MIN = b
                .comment("Chance one resuscitation attempt succeeds on a casualty who is at death's door (blood loss "
                        + "at bloodDeathLossFraction). Low: pumping a patient who has bled out rarely works. "
                        + "Default 0.10.")
                .defineInRange("resuscitateChanceMin", 0.10D, 0.0D, 1.0D);
        RESUSCITATE_CHANCE_MAX = b
                .comment("Chance one resuscitation attempt succeeds on a casualty who only just went down (blood loss "
                        + "at bloodUnconsciousLossFraction). Default 0.70.")
                .defineInRange("resuscitateChanceMax", 0.70D, 0.0D, 1.0D);
        RESUSCITATE_BLEED_REFERENCE = b
                .comment("Bleeding rate (ml/tick) at which resuscitation is hopeless: the success chance is scaled "
                        + "by (1 - bleeding/this). This is what makes stopping the haemorrhage the first job rather "
                        + "than an optional extra. Default 2.0.")
                .defineInRange("resuscitateBleedReference", 2.0D, 0.0D, 1000.0D);
        INTERNAL_BLEEDING_CHANCE = b
                .comment("Chance (0..1) that a penetrating hit to the TRUNK also causes INTERNAL BLEEDING. This is "
                        + "the one wound no bandage or tourniquet reaches (a suture kit stops it; a medkit clears "
                        + "it), so it should be the exception that makes a casualty a real emergency, not the "
                        + "default outcome of every bullet. Default 0.15.")
                .defineInRange("internalBleedingChance", 0.15D, 0.0D, 1.0D);
        INTERNAL_BLEEDING_MIN_ENERGY = b
                .comment("Minimum hit ENERGY (roughly the raw damage that got through armour) for internal bleeding "
                        + "to be possible at all: the penetration threshold. Below it a hit bruises and lacerates but "
                        + "does not reach anything vital. Default 4.0.")
                .defineInRange("internalBleedingMinEnergy", 4.0D, 0.0D, 100.0D);
        INTERNAL_BLEEDING_LIMB_MULTIPLIER = b
                .comment("Multiplier on the internal-bleeding chance when the hit lands on an ARM or LEG rather than "
                        + "the head/torso. There is far less to rupture in a limb. Default 0.20.")
                .defineInRange("internalBleedingLimbMultiplier", 0.20D, 0.0D, 1.0D);
        INTERNAL_BLEEDING_EXPLOSION_MULTIPLIER = b
                .comment("Multiplier on the internal-bleeding chance for EXPLOSION damage, which causes blast injury "
                        + "to organs without needing to penetrate. Default 1.5.")
                .defineInRange("internalBleedingExplosionMultiplier", 1.5D, 0.0D, 5.0D);
        CARDIAC_OUTPUT_ENABLED = b
                .comment("Scale every wound's bleeding by CIRCULATION, so blood loss decelerates as the patient "
                        + "empties: a wound can only bleed as fast as the heart pushes blood past it. This is what "
                        + "gives a downed casualty a long, workable window instead of a short fuse. Turn off for a "
                        + "flat rate that ignores how much blood is left.")
                .define("cardiacOutputEnabled", true);
        CARDIAC_VENOUS_RETURN_FLOOR = b
                .comment("Blood volume RATIO (remaining/max) at which venous return, and so cardiac output, reaches "
                        + "zero: the ventricle no longer fills enough to pump. Output ramps linearly from 1.0 at full "
                        + "volume down to 0 here (then the floor below applies). Higher = circulation collapses "
                        + "sooner, so bleeding slows earlier. Default 0.50.")
                .defineInRange("cardiacVenousReturnFloor", 0.50D, 0.0D, 0.95D);
        CARDIAC_OUTPUT_FLOOR = b
                .comment("Lower bound on the cardiac-output factor. Even with no effective circulation a wound still "
                        + "seeps under gravity, so bleeding never stops entirely from blood loss alone; this is what "
                        + "stops an untreated casualty from stabilising themselves by bleeding out. Default 0.05.")
                .defineInRange("cardiacOutputFloor", 0.05D, 0.0D, 1.0D);
        MAX_TRAUMAS_PER_HIT = b
                .comment("Hard cap on how many separate wounds one hit may open IN A SINGLE LIMB. Every damage "
                        + "path lists its primary wounds first and its rolled complications (internal bleeding, a "
                        + "fracture) last, so the cap drops the third thing piled on top rather than the wound "
                        + "itself. Keeping this low is what makes a casualty's injury list readable and treatable "
                        + "instead of a wall of entries. Note a round that PIERCES (penetrationEnabled) can still "
                        + "wound each limb its path crossed, which is a through-and-through rather than clutter. "
                        + "0 disables the cap. Default 3.")
                .defineInRange("maxTraumasPerHit", 3, 0, 16);
        HEART_RATE_ENABLED = b
                .comment("Model HEART RATE as a real vital. The body raises it to defend blood pressure as volume "
                        + "falls, and because circulation scales the bleed rate, a racing heart pushes blood out of "
                        + "wounds faster: bleeding becomes a spiral you have to interrupt rather than a flat drain. "
                        + "Turn off to pin the rate at resting, which is exactly how the model behaved before heart "
                        + "rate existed.")
                .define("heartRateEnabled", true);
        HEART_RATE_RESTING = b
                .comment("Resting heart rate in bpm. This is also the rate at which circulation counts as 1.0, so "
                        + "changing it rescales nothing else. Default 80.")
                .defineInRange("heartRateResting", 80.0D, 20.0D, 200.0D);
        HEART_RATE_MAX = b
                .comment("Ceiling on heart rate in bpm, however hard the body is compensating. Default 220.")
                .defineInRange("heartRateMax", 220.0D, 60.0D, 400.0D);
        HEART_RATE_BLEED_INFLUENCE = b
                .comment("How much heart rate scales the BLEED RATE. 1.0 is ACE3 "
                        + "exactly: fully linear in bpm, so a heart at 160 pushes twice the blood out of a wound "
                        + "as one at 80. 0.0 ignores rate entirely, leaving it a readout with no mechanical bite. "
                        + "Blood pressure and the rate the body settles at are unaffected either way, so this is a "
                        + "balance lever rather than a change to the physiology. The default "
                        + "is half, because ACE3 hands a patient to its cardiac-arrest timer at the volume where "
                        + "this mod simply kills them, so the undiluted coupling compresses the whole endgame "
                        + "into the last minute or so. At 0.5 a racing heart still roughly halves the window a "
                        + "medic has. Default 0.50.")
                .defineInRange("heartRateBleedInfluence", 0.50D, 0.0D, 2.0D);
        HEART_RATE_COMPENSATION_RATIO = b
                .comment("Blood RATIO (remaining/max) below which the body starts raising the heart rate to hold "
                        + "its blood pressure up. At the default blood settings this is the same volume at which a "
                        + "casualty goes down, so the tachycardia starts exactly when they collapse. Default 0.70.")
                .defineInRange("heartRateCompensationRatio", 0.70D, 0.0D, 1.0D);
        HEART_RATE_DECOMPENSATION_RATIO = b
                .comment("Blood RATIO below which compensation gives out and the rate falls away toward zero. At "
                        + "the default blood settings this is the volume at which a casualty bleeds out, so it is "
                        + "the tail of the curve; raise bloodDeathLossFraction past it to make the full bradycardic "
                        + "collapse something a patient can actually sit in. Default 0.60.")
                .defineInRange("heartRateDecompensationRatio", 0.60D, 0.0D, 1.0D);
        HEART_RATE_PAIN_THRESHOLD = b
                .comment("Perceived pain (0..1) above which pain alone drives the heart rate up. Default 0.20.")
                .defineInRange("heartRatePainThreshold", 0.20D, 0.0D, 1.0D);
        HEART_RATE_PAIN_GAIN = b
                .comment("Bpm added above resting by fully saturated pain. This is why painkillers are also a way "
                        + "to slow someone's bleeding, not only to keep them conscious. Default 50.")
                .defineInRange("heartRatePainGain", 50.0D, 0.0D, 200.0D);
        HEART_RATE_STIMULANT_BONUS = b
                .comment("Bpm added by a full stimulant dose. Default 40.")
                .defineInRange("heartRateStimulantBonus", 40.0D, 0.0D, 200.0D);
        HEART_RATE_OPIOID_DROP = b
                .comment("Bpm removed by full opioid pain suppression. This is what makes an over-medicated patient "
                        + "read as dangerously slow on the vitals readout before the overdose itself bites. "
                        + "Default 30.")
                .defineInRange("heartRateOpioidDrop", 30.0D, 0.0D, 200.0D);
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
        TOURNIQUET_RECOVERY_CHANCE = b
                .comment("Chance (0..1) that removing a tourniquet recovers it back into the remover's inventory. "
                        + "On success the tourniquet item is returned (dropped at their feet if there is no room); "
                        + "on failure it is lost. Default 0.60.")
                .defineInRange("tourniquetRecoveryChance", 0.60D, 0.0D, 1.0D);
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

        b.push("unconsciousness");
        BLACKOUT_GRACE_TICKS = b
                .comment("Short 'adrenaline-style' grace (ticks) the player stays conscious, resisting, before a "
                        + "DRUG blackout takes hold (20 ticks = 1 second); also ADDED on top of the asphyxia "
                        + "struggle window before passing out. Much shorter than the pain adrenaline delay. Once "
                        + "this grace is running the temporary blackout is SEALED – an antidote no longer PREVENTS "
                        + "it (it only strips the drug/buffs, prevents death, and speeds the wake-up roll). "
                        + "Surfacing still ends a drowning episode during the conscious struggle. 0 = black out "
                        + "immediately. Default 30 (1.5 seconds).")
                .defineInRange("blackoutGraceTicks", 30, 0, 12000);
        WAKE_CHANCE = b
                .comment("Probability (0..1) PER physiology recompute that an unconscious player WAKES UP, rolled "
                        + "only once their wakeup score has dropped to wakeupScoreThreshold or below. Higher = they "
                        + "come to sooner (at the default 10-tick update interval, 0.15 averages ~3-4 seconds once "
                        + "eligible). Default 0.15.")
                .defineInRange("wakeChance", 0.15D, 0.0D, 1.0D);
        WAKEUP_SCORE_THRESHOLD = b
                .comment("An unconscious player may only START rolling wakeChance once their WAKEUP SCORE is at or "
                        + "below this. The score is a weighted sum of how bad their blood loss, pain, drug load and "
                        + "active bleeding still are (each normalised 0..1, then multiplied by its weight below); "
                        + "low = stabilised. Injuries need not fully stop, just be low. A single fully-KO-grade "
                        + "factor (weight 1) sits at 1.0, so the default 0.5 requires the causes to be well eased. "
                        + "Default 0.5.")
                .defineInRange("wakeupScoreThreshold", 0.5D, 0.0D, 40.0D);
        WAKEUP_BLOOD_WEIGHT = b
                .comment("Weight of BLOOD LOSS in the wakeup score (blood lost / bloodUnconsciousLossFraction, "
                        + "clamped 0..1 -> 1.0 at the pass-out loss). Higher = heavy blood loss keeps the player "
                        + "down longer; this is what stops a still-bleeding-out player from waking. Default 1.0.")
                .defineInRange("wakeupBloodWeight", 1.0D, 0.0D, 20.0D);
        WAKEUP_PAIN_WEIGHT = b
                .comment("Weight of systemic PAIN in the wakeup score (0 below painShockThreshold, 1.0 at "
                        + "painUnconsciousThreshold). Default 1.0.")
                .defineInRange("wakeupPainWeight", 1.0D, 0.0D, 20.0D);
        WAKEUP_DRUG_WEIGHT = b
                .comment("Weight of DRUG LOAD in the wakeup score (load / overdoseLethalThreshold, clamped 0..1). "
                        + "Keeps an overdosed player under until the drug wears down. Default 1.0.")
                .defineInRange("wakeupDrugWeight", 1.0D, 0.0D, 20.0D);
        WAKEUP_BLEED_WEIGHT = b
                .comment("Weight of ACTIVE BLEEDING RATE in the wakeup score (rate / wakeupBleedReference, clamped "
                        + "0..1). Distinct from blood-loss: a player barely bled yet but haemorrhaging fast should "
                        + "not wake. Default 1.0.")
                .defineInRange("wakeupBleedWeight", 1.0D, 0.0D, 20.0D);
        WAKEUP_BLEED_REFERENCE = b
                .comment("Bleeding rate (ml/tick) treated as a 'full' (1.0) bleed contribution to the wakeup "
                        + "score; the actual aggregate bleeding is divided by this and clamped to 1. Lower = even "
                        + "light bleeding keeps the player down. Default 2.0.")
                .defineInRange("wakeupBleedReference", 2.0D, 0.01D, 1000.0D);
        b.pop();

        b.push("features");
        ENABLE_FRACTURES = b.comment("Master toggle for fracture trauma.").define("enableFractures", true);
        ENABLE_BLEEDING = b.comment("Master toggle for bleeding / blood loss.").define("enableBleeding", true);
        ENABLE_PAIN = b.comment("Master toggle for the pain system.").define("enablePain", true);
        ENABLE_BLEEDOUT = b.comment("If true, lethal conditions render the player unconscious (bleed-out) instead of instant death.").define("enableBleedout", true);
        HEAD_DEPLETION_INSTAKILL = b.comment("If true, fully depleting the HEAD's health kills the player OUTRIGHT (bypasses bleed-out/downing). Default false -> a destroyed head downs the player.").define("headDepletionInstakill", false);
        TORSO_DEPLETION_INSTAKILL = b.comment("If true, fully depleting the TORSO's health kills the player OUTRIGHT (bypasses bleed-out/downing). Default false -> a destroyed torso downs the player.").define("torsoDepletionInstakill", false);
        ENABLE_GIVE_UP = b.comment("If true, a downed (unconscious / bleeding-out) player may HOLD the 'Give Up' key to die instantly and reach the respawn screen.").define("enableGiveUp", true);
        GIVE_UP_HOLD_TICKS = b.comment("How long (ticks) a downed player must HOLD the 'Give Up' key before dying (20 ticks = 1 second). Default 40 (2 seconds).").defineInRange("giveUpHoldTicks", 40, 1, 1200);
        EFFECT_IMMUNE_IN_CREATIVE = b.comment("Creative-mode players ignore medical penalties.").define("effectImmuneInCreative", true);
        MANAGE_NATURAL_REGEN = b
                .comment("If true, WFMedical OWNS the player's health recovery: it drives health up to the medical",
                        "model's current-health value on its own, and clamps vanilla heals (natural regen, regen",
                        "potions, ...) so they can't push health past that value. This stops vanilla regen from",
                        "fighting the medical model, and means you can freely disable vanilla naturalRegeneration",
                        "WITHOUT stopping medical recovery. Set false to revert to vanilla driving the 'heal up'.")
                .define("manageNaturalRegen", true);
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
        FALL_FRACTURE_MIN_BLOCKS = b
                .comment("Minimum fall height (in blocks) at which a fall can start breaking bones. Below this a "
                        + "fall is pure soft blunt-force trauma (bruising that self-heals, never bleeds, no crush) "
                        + "-- it costs health but cannot fracture. At/above it a bad landing has a rising chance to "
                        + "fracture a limb (legs far more than arms); a fall NEVER causes a crush injury regardless "
                        + "of height. Default 5.0.")
                .defineInRange("fallFractureMinBlocks", 5.0D, 0.0D, 256.0D);
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
                        + "effect window. Come-off time scales with each drug's dose load: default 0.0000277778 "
                        + "clears a morphine dose (0.5 load) in ~15 min and a combat-stimulant dose (1.4 load) in ~42 min.")
                .defineInRange("drugDecayPerTick", 0.0000277778D, 0.0D, 1.0D);
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
        DEATH_ATTRIBUTION_WINDOW_TICKS = b
                .comment("How long (ticks) after being damaged by a player that a subsequent bleed-out / engine "
                        + "death is still credited to that player (20 ticks = 1 second). Vanilla forgets an "
                        + "attacker after 100 ticks, so this must be long enough to cover a full bleed-out. "
                        + "Default 6000 (5 minutes).")
                .defineInRange("deathAttributionWindowTicks", 6000, 0, 432000);
        TREAT_REACH_BLOCKS = b
                .comment("Reach (in blocks) for treating ANOTHER player: how close you must be to aim at them and "
                        + "open the treatment wheel / examination sheet, and the distance past which an open "
                        + "menu auto-closes and an in-progress treatment can no longer be applied. Used by the "
                        + "client target-pick, the server-side validation, and the menu auto-close so they all "
                        + "agree. Default 3.0.")
                .defineInRange("treatReachBlocks", 3.0D, 1.0D, 16.0D);
        TREAT_SELF_ONLY_OVERRIDES = b
                .comment("Per-item override for whether a medical item is SELF-ONLY (can only be used on yourself, "
                        + "never applied to another player). Each entry is \"<itemId>=true|false\" -- e.g. "
                        + "\"wfmedical:painkillers=false\" to let a medic dose a teammate, or \"wfmedical:medkit=true\" "
                        + "to forbid treating others with a medkit. Items not listed use their built-in default: "
                        + "only ORAL medication (painkillers) is self-only by default; everything else (bandages, "
                        + "blood bags, syringes, ...) can be applied to others.")
                .defineList("treatSelfOnlyOverrides", java.util.List.of(),
                        o -> o instanceof String s && s.indexOf('=') > 0);
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
        LOG_HIT_DETECTION = b
                .comment("DEBUG/TEST tool. If true, every TACZ bullet's own broad-phase collision test is compared",
                        "against the rig's per-limb OBBs for nearby players and logged: whether TACZ itself registered",
                        "a hit, and whether the rig would have classified a limb for that same ray. A 'HITBOX GAP'",
                        "warning is logged whenever a shot would land on a rig limb box but TACZ's own collision test",
                        "missed it entirely (so no damage/hurt event ever fired). Off (the default) has ZERO runtime cost.")
                .define("logHitDetection", false);
        LOG_MEDICAL_SYNC = b
                .comment("DEBUG/TEST tool. If true, the whole medical state sync pipeline is traced to the log:",
                        "server-side every full sync and delta sent (with the per-limb health%/bleed/pain/wound",
                        "counts and the delta's changed-limb mask), and client-side every full/delta APPLIED to the",
                        "cache (including a WARN when a delta arrives with no baseline to apply onto -- the classic",
                        "'UI shows no damage / snaps back to healthy' desync). Use this to pin down sync/cache issues.",
                        "Off (the default) has ZERO runtime cost.")
                .define("logMedicalSync", false);
        SYNC_FULL_RESYNC_INTERVAL_TICKS = b
                .comment("Safety net for the incremental (delta) medical sync. At most every this many game ticks an",
                        "active player is re-baselined with a full authoritative sync instead of a delta, so any",
                        "client-side delta drift (a dropped/late/mis-based delta leaving the character sheet stuck on a",
                        "stale or falsely-healthy limb) self-corrects within this window. It is invisible while the",
                        "client is already in sync (the full carries the same data the deltas already produced) and only",
                        "costs one small extra packet per interval per injured player. Set to 0 to disable and rely on",
                        "deltas alone. Default 40 (~2s at 20 TPS).")
                .defineInRange("syncFullResyncIntervalTicks", 40, 0, 12000);
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
        DAMAGE_SOURCE_CATEGORIES = b
                .comment("Tie specific damage sources to a WFMedical hurt category so modded damage makes the right",
                        "trauma. Each entry is \"<key>=<CATEGORY>\", where <key> is matched against the damage type's",
                        "registry id first (e.g. gtceu:electric) and then its msgId (e.g. mod.something).",
                        "Categories: BALLISTIC, SLASHING, BLUNT, UNARMED, PIERCING, FIRE (burns), EXPLOSION,",
                        "CHEMICAL (chemical burns), RADIATION, FALL, GENERIC. This wins over the built-in guesser.",
                        "Defaults cover GregTech CEu (verified ids from GTDamageTypes): electrical shock and hot",
                        "metal/steam are burns; turbine blades cut; chemical and radiation map through.")
                .defineList("damageSourceCategories", java.util.List.of(
                        "gtceu:electric=FIRE",
                        "gtceu:heat=FIRE",
                        "gtceu:turbine=SLASHING",
                        "gtceu:chemical=CHEMICAL",
                        "gtceu:radiation=RADIATION"
                ), o -> o instanceof String s && s.indexOf('=') > 0);
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
                .defineEnum("hitRegistrationMode", HitRegMode.PRECISE);
        HIT_GAP_REJECT_TOLERANCE = b
                .comment("PRECISE only: forgiveness (blocks) for the gap-rejection test. A shot is thrown out as a",
                        "'gap' (whiff between the limb boxes  no damage, no hitmarker) only when its path clears",
                        "EVERY limb box by more than this margin. It exists because the classifier assigns the nearest",
                        "limb to any envelope hit, so a grazing shot, or one skimming a limb whose server-side pose",
                        "drifted slightly from what the shooter saw, would otherwise be silently dropped. Higher =",
                        "more forgiving (fewer whiffs, but narrow gaps between an arm and the torso start to count);",
                        "0 = strict, reject unless the ray passes exactly through a limb box. Default 0.2.")
                .defineInRange("hitGapRejectTolerance", 0.2D, 0.0D, 2.0D);
        b.comment("Per-STANCE broad-phase envelope: blocks the hit-scan box is widened for each pose so arm /",
                        "prone hits register. Horizontal = X/Z per side, Vertical = Y top+bottom. Size each to just",
                        "contain the model in that stance -- the vanilla box already shrinks while crouching/swimming,",
                        "and the fine-phase per-limb test rejects any surplus, so over-sizing only costs a few extra",
                        "fine tests while under-sizing drops hits. Tune live with '/wfmedical hitbox envelope ...' and",
                        "bake the dialled-in numbers back here.")
                .push("envelopeReach");
        double[][] envDefaults = {{0.4D, 0.2D}, {0.5D, 0.1D}, {1.0D, 0.3D}, {1.0D, 0.3D}};
        ModConfigSpec.DoubleValue[] envH = new ModConfigSpec.DoubleValue[RigTuning.RigPose.VALUES.length];
        ModConfigSpec.DoubleValue[] envV = new ModConfigSpec.DoubleValue[RigTuning.RigPose.VALUES.length];
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
        ANIMATED_HITBOXES = b
                .comment("Animation-aware hitboxes (PlayerAnimator compat). When ON, a player's limb hitboxes track",
                        "played animations for HIT REGISTRATION -- not just the client visual. PlayerAnimator applies",
                        "animations client-side only, so the animated pose reaches the server via the SAME validated",
                        "pose-stream path as CLIENT_HINT: the victim streams its own posed rig, the server still runs",
                        "the ray test ITSELF and validates the pose (an attacker can never pick the limb). This turns",
                        "on pose streaming even under SERVER authority, so it carries the same client-trust caveat as",
                        "CLIENT_HINT. OFF (default): animations are a client-side visual only; hits use the server's",
                        "vanilla-pose rebuild. PlayerAnimator itself is NOT required on the server.")
                .define("animatedHitboxes", false);
        POSE_STREAM_CHANGE_EPSILON = b
                .comment("Client-pose streaming sensitivity: the victim resends its pose once any limb-box scalar",
                        "(position or orientation) drifts more than this many blocks from the last sent pose. Lower =",
                        "small rotations reach the server sooner (finer tracking) for a little more bandwidth; higher =",
                        "coarser, small movements ignored. Default 0.0002.")
                .defineInRange("poseStreamChangeEpsilon", 2.0e-4D, 0.0D, 1.0D);
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

    public static int updateIntervalTicks() {
        return UPDATE_INTERVAL_TICKS.get();
    }

    public static int maxHealthHearts() {
        return MAX_HEALTH_HEARTS.get();
    }

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

    public static float healthShare(LimbType lt) {
        if (lt == LimbType.HEAD) {
            return healthShareHead();
        }
        if (lt == LimbType.TORSO) {
            return healthShareTorso();
        }
        return lt.isLeg() ? healthShareLeg() : healthShareArm();
    }

    public static float tourniquetBleedMultiplier() {
        return TOURNIQUET_BLEED_MULTIPLIER.get().floatValue();
    }

    public static double bleedingRateMultiplier() {
        return BLEEDING_RATE_MULTIPLIER.get();
    }

    public static int resuscitateDurationTicks() {
        return RESUSCITATE_DURATION_TICKS.get();
    }

    public static int resuscitateGraceTicks() {
        return RESUSCITATE_GRACE_TICKS.get();
    }

    public static double resuscitateChanceMin() {
        return RESUSCITATE_CHANCE_MIN.get();
    }

    public static double resuscitateChanceMax() {
        return RESUSCITATE_CHANCE_MAX.get();
    }

    public static double resuscitateBleedReference() {
        return RESUSCITATE_BLEED_REFERENCE.get();
    }

    public static double internalBleedingChance() {
        return INTERNAL_BLEEDING_CHANCE.get();
    }

    public static double internalBleedingMinEnergy() {
        return INTERNAL_BLEEDING_MIN_ENERGY.get();
    }

    public static double internalBleedingLimbMultiplier() {
        return INTERNAL_BLEEDING_LIMB_MULTIPLIER.get();
    }

    public static double internalBleedingExplosionMultiplier() {
        return INTERNAL_BLEEDING_EXPLOSION_MULTIPLIER.get();
    }

    public static boolean cardiacOutputEnabled() {
        return CARDIAC_OUTPUT_ENABLED.get();
    }

    public static double cardiacVenousReturnFloor() {
        return CARDIAC_VENOUS_RETURN_FLOOR.get();
    }

    public static double cardiacOutputFloor() {
        return CARDIAC_OUTPUT_FLOOR.get();
    }

    public static int maxTraumasPerHit() {
        return MAX_TRAUMAS_PER_HIT.get();
    }

    public static boolean heartRateEnabled() {
        return HEART_RATE_ENABLED.get();
    }

    public static double heartRateResting() {
        return HEART_RATE_RESTING.get();
    }

    public static double heartRateMax() {
        return HEART_RATE_MAX.get();
    }

    public static double heartRateBleedInfluence() {
        return HEART_RATE_BLEED_INFLUENCE.get();
    }

    public static double heartRateCompensationRatio() {
        return HEART_RATE_COMPENSATION_RATIO.get();
    }

    public static double heartRateDecompensationRatio() {
        return HEART_RATE_DECOMPENSATION_RATIO.get();
    }

    public static float heartRatePainThreshold() {
        return HEART_RATE_PAIN_THRESHOLD.get().floatValue();
    }

    public static float heartRatePainGain() {
        return HEART_RATE_PAIN_GAIN.get().floatValue();
    }

    public static float heartRateStimulantBonus() {
        return HEART_RATE_STIMULANT_BONUS.get().floatValue();
    }

    public static float heartRateOpioidDrop() {
        return HEART_RATE_OPIOID_DROP.get().floatValue();
    }

    public static float tourniquetLegSpeedMultiplier() {
        return TOURNIQUET_LEG_SPEED_MULTIPLIER.get().floatValue();
    }

    public static float tourniquetArmSpeedMultiplier() {
        return TOURNIQUET_ARM_SPEED_MULTIPLIER.get().floatValue();
    }

    public static double tourniquetArmSway() {
        return TOURNIQUET_ARM_SWAY.get();
    }

    public static double tourniquetRecoveryChance() {
        return TOURNIQUET_RECOVERY_CHANCE.get();
    }

    public static boolean adrenalineEnabled() {
        return ADRENALINE_ENABLED.get();
    }

    public static int adrenalinePainKoDelayTicks() {
        return ADRENALINE_PAIN_KO_DELAY_TICKS.get();
    }

    public static int blackoutGraceTicks() {
        return BLACKOUT_GRACE_TICKS.get();
    }

    public static double wakeChance() {
        return WAKE_CHANCE.get();
    }

    public static double wakeupScoreThreshold() {
        return WAKEUP_SCORE_THRESHOLD.get();
    }

    public static double wakeupBloodWeight() {
        return WAKEUP_BLOOD_WEIGHT.get();
    }

    public static double wakeupPainWeight() {
        return WAKEUP_PAIN_WEIGHT.get();
    }

    public static double wakeupDrugWeight() {
        return WAKEUP_DRUG_WEIGHT.get();
    }

    public static double wakeupBleedWeight() {
        return WAKEUP_BLEED_WEIGHT.get();
    }

    public static double wakeupBleedReference() {
        return WAKEUP_BLEED_REFERENCE.get();
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

    public static double bloodRegenMlPerSecond() {
        return BLOOD_REGEN_ML_PER_SECOND.get();
    }

    public static double unarmedMajorChance() {
        return UNARMED_MAJOR_CHANCE.get();
    }

    public static double fallFractureMinBlocks() {
        return FALL_FRACTURE_MIN_BLOCKS.get();
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

    public static boolean headDepletionInstakill() {
        return HEAD_DEPLETION_INSTAKILL.get();
    }

    public static boolean torsoDepletionInstakill() {
        return TORSO_DEPLETION_INSTAKILL.get();
    }

    public static boolean enableGiveUp() {
        return ENABLE_GIVE_UP.get();
    }

    public static int giveUpHoldTicks() {
        return GIVE_UP_HOLD_TICKS.get();
    }

    public static int bleedoutTicks() {
        return BLEEDOUT_TICKS.get();
    }

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

    public static boolean canInstakillOnImpact(DamageCategory cat) {
        return cat != DamageCategory.FIRE && cat != DamageCategory.CHEMICAL && cat != DamageCategory.RADIATION;
    }

    public static boolean finishDownedOnHit() {
        return FINISH_DOWNED_ON_HIT.get();
    }

    public static boolean effectImmuneInCreative() {
        return EFFECT_IMMUNE_IN_CREATIVE.get();
    }

    public static boolean manageNaturalRegen() {
        return MANAGE_NATURAL_REGEN.get();
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

    public static int deathAttributionWindowTicks() {
        return DEATH_ATTRIBUTION_WINDOW_TICKS.get();
    }

    public static double treatReachBlocks() {
        return TREAT_REACH_BLOCKS.get();
    }

    public static double treatReachSqr() {
        double r = TREAT_REACH_BLOCKS.get();
        return r * r;
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

    public static double armSideThreshold() {
        return ARM_SIDE_THRESHOLD.get();
    }

    public static double meleeReach() {
        return MELEE_REACH.get();
    }

    public static boolean riggedLimbBoxes() {
        return RIGGED_LIMB_BOXES.get();
    }

    public static double limbBoxPadding() {
        return LIMB_BOX_PADDING.get();
    }

    public static boolean hitboxDebug() {
        return HITBOX_DEBUG.get();
    }

    public static boolean logHitDetection() {
        return LOG_HIT_DETECTION.get();
    }

    public static boolean logMedicalSync() {
        return LOG_MEDICAL_SYNC.get();
    }

    public static int syncFullResyncIntervalTicks() {
        return SYNC_FULL_RESYNC_INTERVAL_TICKS.get();
    }

    public static boolean openPersistenceCompat() {
        return OPEN_PERSISTENCE_COMPAT.get();
    }

    public static boolean taczArmPose() {
        return TACZ_ARM_POSE.get();
    }

    private static java.util.List<? extends String> cachedDamageRaw;
    private static java.util.Map<String, DamageCategory> cachedDamageMap = java.util.Collections.emptyMap();

    /**
     * Look up a configured hurt category for a damage source key (its registry id or msgId), or null if
     * no mapping is configured. Backs the data-driven {@code damageSourceCategories} override.
     */
    public static DamageCategory damageSourceCategory(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return damageSourceCategoryMap().get(key);
    }

    private static java.util.Map<String, DamageCategory> damageSourceCategoryMap() {
        java.util.List<? extends String> raw;
        try {
            raw = DAMAGE_SOURCE_CATEGORIES.get();
        } catch (IllegalStateException notLoaded) {
            return java.util.Collections.emptyMap();
        }
        if (raw != cachedDamageRaw) {
            java.util.Map<String, DamageCategory> parsed = new java.util.HashMap<>();
            for (String entry : raw) {
                if (entry == null) {
                    continue;
                }
                int eq = entry.indexOf('=');
                if (eq <= 0 || eq >= entry.length() - 1) {
                    continue;
                }
                String k = entry.substring(0, eq).trim();
                String v = entry.substring(eq + 1).trim();
                if (k.isEmpty() || v.isEmpty()) {
                    continue;
                }
                try {
                    parsed.put(k, DamageCategory.valueOf(v.toUpperCase(java.util.Locale.ROOT)));
                } catch (IllegalArgumentException ignoredBadCategory) {
                    // skip entries with an unknown category name
                }
            }
            cachedDamageMap = parsed;
            cachedDamageRaw = raw;
        }
        return cachedDamageMap;
    }

    private static java.util.List<? extends String> cachedSelfOnlyRaw;
    private static java.util.Map<String, Boolean> cachedSelfOnlyMap = java.util.Collections.emptyMap();

    /**
     * Per-item self-only override, or null if the item is not listed (use its built-in default). Backs the
     * data-driven {@code treatSelfOnlyOverrides} config.
     */
    public static Boolean treatSelfOnlyOverride(net.minecraft.resources.ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return treatSelfOnlyMap().get(id.toString());
    }

    private static java.util.Map<String, Boolean> treatSelfOnlyMap() {
        java.util.List<? extends String> raw;
        try {
            raw = TREAT_SELF_ONLY_OVERRIDES.get();
        } catch (IllegalStateException notLoaded) {
            return java.util.Collections.emptyMap();
        }
        if (raw != cachedSelfOnlyRaw) {
            java.util.Map<String, Boolean> parsed = new java.util.HashMap<>();
            for (String entry : raw) {
                if (entry == null) {
                    continue;
                }
                int eq = entry.indexOf('=');
                if (eq <= 0 || eq >= entry.length() - 1) {
                    continue;
                }
                String k = entry.substring(0, eq).trim();
                String v = entry.substring(eq + 1).trim();
                if (!k.isEmpty() && !v.isEmpty()) {
                    parsed.put(k, Boolean.parseBoolean(v));
                }
            }
            cachedSelfOnlyMap = parsed;
            cachedSelfOnlyRaw = raw;
        }
        return cachedSelfOnlyMap;
    }

    public static HitRegMode hitRegistrationMode() {
        return HITREG_MODE.get();
    }

    public static double hitGapRejectTolerance() {
        return HIT_GAP_REJECT_TOLERANCE.get();
    }

    public static double hitEnvelopeReachHorizontal(RigTuning.RigPose pose) {
        return ENV_REACH_H[pose.ordinal()].get();
    }

    public static double hitEnvelopeReachVertical(RigTuning.RigPose pose) {
        return ENV_REACH_V[pose.ordinal()].get();
    }

    public static double[] envelopeReachSnapshot() {
        double[] a = new double[RigTuning.RigPose.VALUES.length * 2];
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            a[pose.ordinal() * 2] = ENV_REACH_H[pose.ordinal()].get();
            a[pose.ordinal() * 2 + 1] = ENV_REACH_V[pose.ordinal()].get();
        }
        return a;
    }

    public static HitAuthority hitAuthority() {
        return HIT_AUTHORITY.get();
    }

    public static int poseStreamMinIntervalTicks() {
        return POSE_STREAM_MIN_INTERVAL_TICKS.get();
    }

    public static int poseStreamMaxIntervalTicks() {
        return POSE_STREAM_MAX_INTERVAL_TICKS.get();
    }

    public static int poseHintMaxAgeTicks() {
        return POSE_HINT_MAX_AGE_TICKS.get();
    }

    public static double poseHintMargin() {
        return POSE_HINT_MARGIN.get();
    }

    public static boolean animatedHitboxes() {
        return ANIMATED_HITBOXES.get();
    }

    public static double poseStreamChangeEpsilon() {
        return POSE_STREAM_CHANGE_EPSILON.get();
    }

    public static boolean useClientPose() {
        return hitAuthority() == HitAuthority.CLIENT_HINT || animatedHitboxes();
    }

    public static boolean penetrationEnabled() {
        return PENETRATION_ENABLED.get();
    }

    public static double penetrationBudget() {
        return PENETRATION_BUDGET.get();
    }

    public static double penetrationEnergyFalloff() {
        return PENETRATION_ENERGY_FALLOFF.get();
    }

    public static double penetrationResistance(LimbType lt) {
        if (lt == LimbType.HEAD) {
            return PEN_RESIST_HEAD.get();
        }
        if (lt == LimbType.TORSO) {
            return PEN_RESIST_TORSO.get();
        }
        return lt.isLeg() ? PEN_RESIST_LEG.get() : PEN_RESIST_ARM.get();
    }

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
                tourniquetArmSpeedMultiplier(),
                headDepletionInstakill(),
                torsoDepletionInstakill(),
                bleedingRateMultiplier(),
                cardiacOutputEnabled(),
                cardiacVenousReturnFloor(),
                cardiacOutputFloor(),
                heartRateEnabled(),
                heartRateResting(),
                heartRateMax(),
                heartRateBleedInfluence(),
                heartRateCompensationRatio(),
                heartRateDecompensationRatio(),
                heartRatePainThreshold(),
                heartRatePainGain(),
                heartRateStimulantBonus(),
                heartRateOpioidDrop()
        );
    }
}
