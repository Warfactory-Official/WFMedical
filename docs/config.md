# Configuration Reference

## `MedicalConfig` — COMMON config (`wfmedical-common.toml`)

Registered via `MedicalConfig.register(ModLoadingContext)` during mod construction. All values are server-authoritative; the client uses the synced snapshot from `MedicalSyncPacket`.

### Section: `physiology`

| Key | Default | Description |
|---|---|---|
| `updateIntervalTicks` | `10` | Engine cadence in ticks. Lower = more responsive; higher = cheaper. Range: 1–40. |
| `maxHealthHearts` | `15` | Baseline max health in hearts (1 heart = 2 HP). Drives `maxHealthPoints = hearts * 2`. Range: 1–512. |
| `maxBloodMl` | `5000.0` | Total blood volume in millilitres. |
| `bloodLowFraction` | `0.60` | Blood fraction (remaining) at or below which blood-loss penalties begin. |
| `bloodCriticalFraction` | `0.35` | Blood fraction (remaining) at or below which state becomes CRITICAL. |
| `bloodDeathLossFraction` | `0.40` | Fraction of total blood **lost** that kills outright (exsanguination). |
| `bloodUnconsciousLossFraction` | `0.30` | Fraction lost at which blood-loss starts feeding the unconsciousness score. |
| `painShockThreshold` | `0.60` | Systemic pain (0..1) above which the pain-shock max-health penalty begins. |
| `painUnconsciousThreshold` | `0.70` | Systemic pain above which pain feeds the unconsciousness score. |
| `painUnconsciousWeight` | `1.00` | Weight of the pain factor in the unconsciousness score. 1.0 = severe pain alone can down the player; lower = pain only contributes to a combined KO. Range: 0–4. |
| `bloodMovementPenaltyLossFraction` | `0.25` | Fraction of blood **lost** above which walk/jump speed is penalised (ramping toward the pain speed floor at the death loss). |
| `bleedingSelfHealThreshold` | `0.30` | Severity at/below which an untreated bleeding wound naturally clots. |
| `bleedingSelfHealRate` | `0.0003` | Severity reduction per tick for a self-clotting wound. |
| `fractureSelfHealMinutes` | `20.0` | Real-time minutes for a full-severity untreated fracture to heal. 0 = never. |

### Section: `pain`

| Key | Default | Description |
|---|---|---|
| `painSaturationK` | `1.0` | Diminishing-returns constant: local pain = raw / (raw + k). Smaller k saturates faster. Range: 0.05–20. |
| `painShareHead` | `0.35` | Max systemic pain share (0..1) contributed by a fully-painful HEAD. |
| `painShareTorso` | `0.50` | Max systemic pain share contributed by a fully-painful TORSO. |
| `painShareArm` | `0.10` | Max systemic pain share per ARM (applied per arm, not total). |
| `painShareLeg` | `0.20` | Max systemic pain share per LEG. |
| `adrenalineEnabled` | `true` | If true, a purely pain-driven KO is held off for `adrenalinePainKoDelayTicks`. Blood-loss KOs are never delayed. |
| `adrenalinePainKoDelayTicks` | `120` | Ticks (6 s) a pain KO is delayed by adrenaline once pain reaches knockout level. If pain drops below before expiry, timer resets. |

### Section: `features`

| Key | Default | Description |
|---|---|---|
| `enableFractures` | `true` | Master toggle for fracture trauma generation. |
| `enableBleeding` | `true` | Master toggle for blood loss ticking. |
| `enablePain` | `true` | Master toggle for the pain system. |
| `enableBleedout` | `true` | If true, lethal conditions render the player UNCONSCIOUS (bleed-out) rather than immediately DEAD. |
| `effectImmuneInCreative` | `true` | Creative/spectator players ignore all medical penalties. |
| `enableInjectables` | `true` | Master toggle for the injectable substance system. |
| `enableAsphyxia` | `true` | If true, a heavy opioid overdose can trigger the conscious respiratory-depression asphyxia phase before passing out. |
| `enableDrowningAsphyxia` | `true` | If true, underwater drowning is handled as asphyxia instead of vanilla drowning damage. |

### Section: `balance`

| Key | Default | Description |
|---|---|---|
| `bleedoutTicks` | `2400` | Passed into `PhysiologyParams.bleedoutTicks` but **not read** in `Physiology.compute`; death from blood loss is governed by `bloodDeathLossFraction`, not a fixed countdown. Range: 20–72000. |
| `lethality.majorTraumaFractionDefault` | `1.0` | Fraction of `maxHealthPoints` a non-blocked hit must deal for an instant-kill (default/slashing/piercing/generic). |
| `lethality.majorTraumaFractionBallistic` | `0.9` | Instant-kill fraction for `BALLISTIC`. |
| `lethality.majorTraumaFractionExplosion` | `0.9` | Instant-kill fraction for `EXPLOSION`. |
| `lethality.majorTraumaFractionBlunt` | `1.1` | Instant-kill fraction for `BLUNT` (requires a hit heavier than full health). |
| `lethality.majorTraumaFractionUnarmed` | `3.0` | Instant-kill fraction for `UNARMED` — effectively disabled. |
| `lethality.majorTraumaFractionFall` | `1.5` | Instant-kill fraction for `FALL` — only catastrophic falls. |
| `finishDownedOnHit` | `true` | If true, any real damage to an already UNCONSCIOUS player kills them. |
| `maxTraumaPerLimb` | `8` | Hard cap on distinct trauma objects per limb; excess is merged. |
| `legFractureSpeedMultiplier` | `0.40` | Multiplicative speed penalty applied per fractured leg. |
| `unarmedMajorChance` | `0.15` | Probability (0..1) that a bare-handed punch to the torso/head produces internal bleeding. |
| `painSwayEnabled` | `true` | If true, high pain makes the client's aim drift (client-side). |
| `painSwayStrength` | `1.0` | Multiplier on aim-sway amplitude. |
| `brokenArmAimSway` | `0.90` | Forced aim-sway floor while aiming with a broken arm (0..1). |
| `brokenArmMeleeWeaknessLevel` | `1` | Weakness effect level while any arm is fractured (0 = disabled). |
| `drugDecayPerTick` | `0.00035` | Drug load decayed per tick (higher = shorter come-down). |
| `stimulantSpeedBonus` | `0.30` | Speed fraction added at full stimulant strength (0.30 = +30%). |
| `clottingBoostThresholdBonus` | `0.70` | Added to `bleedingSelfHealThreshold` at full clotting boost. |
| `clottingBoostRateMultiplier` | `10.0` | Self-clot rate multiplier at full boost: `rate *= (1 + boost * this)`. |
| `clottingAgentDurationTicks` | `2400` | Duration (ticks) of a clotting boost from a `BOOST_CLOTTING` item. |
| `overdoseLethalEnabled` | `true` | If true, drug load ≥ `overdoseLethalThreshold` drains health during overdose unconsciousness. |
| `overdoseLethalThreshold` | `1.6` | Drug load at/above which overdose is lethal (health drain active). |
| `overdoseLethalDrainPerTick` | `0.05` | HP drained per tick during a lethal overdose. |
| `asphyxiaThreshold` | `1.0` | Drug load at/above which asphyxia can trigger on injection. |
| `asphyxiaChance` | `0.35` | Probability of asphyxia triggering (vs. immediate KO) when load crosses the threshold. |
| `asphyxiaAirLossPerTick` | `12` | Air supply drained per tick while asphyxiating (vanilla max = 300). |
| `asphyxiaUnconsciousTicks` | `200` | Ticks (10 s) after asphyxia pass-out before death. Clearing the cause recovers. |
| `asphyxiaWeaknessAmplifier` | `1` | Weakness effect amplifier during the conscious asphyxia phase (0 = Weakness I, 1 = Weakness II). |
| `asphyxiaStruggleTicks` | `60` | Ticks (3 s) of the conscious struggle phase before passing out. |
| `asphyxiaMoveMultiplier` | `0.25` | Speed multiplier during the conscious asphyxia phase. |

### Section: `hitlocation`

| Key | Default | Description |
|---|---|---|
| `geometricHitLocation` | `true` | Master switch for OBB-based hit location. Off = legacy weighted sampler. |
| `poseAwareArms` | `true` | If true, an actively aiming victim has upper-frontal hits reassigned to the raised arm. |
| `headBandBottom` | `0.74` | Fraction of body height (from feet) at/above which a hit counts as HEAD. |
| `legBandTop` | `0.40` | Fraction of body height at/below which a hit counts as a LEG. |
| `armSideThreshold` | `0.80` | Normalized horizontal offset (|nx|) at/above which a torso-height hit redirects to an arm. |
| `meleeReach` | `3.0` | Aim-ray length (blocks) for reconstructing melee hit geometry. |
| `riggedLimbBoxes` | `true` | If true, hits are classified against the server-side pose replica (six OBBs). Off = banded-AABB. |
| `limbBoxPadding` | `0.02` | OBB inflation (blocks) to absorb pose-replica drift. |

### Section: `compat`

| Key | Default | Description |
|---|---|---|
| `openPersistenceCompat` | `true` | Enable Open Persistence integration (logout body carries medical profile). |
| `taczArmPose` | `true` | Use synced TACZ aiming progress for arm OBB poses instead of the generic bow-like approximation. |

### Section: `hitregistration`

| Key | Default | Description |
|---|---|---|
| `hitRegistrationMode` | `ENVELOPE` | `OFF` = vanilla tight box; `ENVELOPE` = inflated to model silhouette (registers arm/prone hits); `PRECISE` = ENVELOPE + rig gap-rejection. |
| `hitEnvelopeInflation` | `0.15` | Blocks the hit-scan box is widened for ENVELOPE/PRECISE modes. |

---

## `wfmedical_definitions.toml` — data-driven definitions

This file is copied into the Forge config directory on first launch and parsed by `MedicalDefinitions`. Editing the config-directory copy customizes injuries and medical items without code changes. The file is parsed by NightConfig's TOML parser. Unknown keys are silently ignored; missing keys fall back to coded defaults.

If the file cannot be parsed at all, `MedicalDefinitions.loadDefaults` regenerates the same definitions programmatically as a fallback.

### `[[trauma]]` tables

One table per injury type. Fields:

| Key | Type | Default | Description |
|---|---|---|---|
| `id` | string | — | Unique id (also the NBT key). |
| `category` | string | `"BRUISE"` | `TraumaCategory` name. |
| `major` | bool | category default | If true, reduces effective max-health while active. |
| `severityContribution` | float | `1.0` | Base severity when created. |
| `painPerSeverity` | float | `0.0` | Pain units per unit severity. |
| `bleedingPerSeverity` | float | `0.0` | ml/tick of blood lost per unit severity. |
| `healSpeedPerTick` | float | `0.0` | Natural severity decay per tick (0 = never). |
| `canReopen` | bool | `false` | May start bleeding again after treatment flag is cleared. |
| `permanent` | bool | `false` | Never heals naturally; requires treatment. |
| `movementModifier` | float | `1.0` | Leg-specific speed multiplier (1.0 = no effect). |
| `healthReductionPerSeverity` | float | `0.0` | Max-health points removed per unit severity (only meaningful when `major = true`). |
| `maxSeverity` | float | `1.0` | Upper bound for merged/accumulated severity. |
| `mergeable` | bool | `true` | Compatible same-type instances on the same limb stack. |
| `treatmentActions` | string[] | `[]` | `TreatmentAction` names this trauma responds to. |

### `[[treatment]]` tables

One table per medical item. Fields:

| Key | Type | Default | Description |
|---|---|---|---|
| `item` | string | — | Item registry name (e.g. `"wfmedical:bandage"`). |
| `action` | string | — | `TreatmentAction` name. |
| `categories` | string[] | `[]` | `TraumaCategory` names this item may act on. Empty = any category. |
| `magnitude` | float | `0.0` | Generic strength parameter (bleeding reduction fraction, heal amount, etc.). |
| `bloodRestoreMl` | float | `0.0` | ml of blood restored as a secondary effect. |
| `useDurationTicks` | int | `20` | Item use/channel duration in ticks. |
| `removesTrauma` | bool | `false` | If true, a successful application removes the trauma entirely. |

### `[[substance]]` tables

One table per injectable substance. Fields:

| Key | Type | Default | Description |
|---|---|---|---|
| `id` | string | — | Unique substance id. |
| `item` | string | — | Item registry name. |
| `painSuppression` | float | `0.0` | Systemic analgesia mask (0..1) applied on injection. |
| `doseLoad` | float | `0.0` | Drug load added per injection. |
| `overdoseThreshold` | float | `1.0` | Drug load at which overdose unconsciousness triggers. |
| `unconsciousTicks` | int | `200` | Overdose unconsciousness duration in ticks. |
| `lethalThreshold` | float | `0.0` | Drug load at/above which overdose also drains health (≤ 0 = disabled). |
| `antidote` | bool | `false` | If true, reverses an overdose rather than causing one. |
| `reversalAmount` | float | `0.0` | Drug load removed by a single antidote injection. |
| `useDurationTicks` | int | `40` | Injection channel duration in ticks. |
| `bloodRestoreMl` | float | `0.0` | Optional secondary blood restore in ml. |
| `clottingBoost` | float | `0.0` | Timed clotting-boost strength (0..1) granted. |
| `stimulantStrength` | float | `0.0` | Timed stimulant strength (0..1): analgesia floor + speed boost + jump clear. |
| `effectTicks` | int | `0` | Duration of clotting/stimulant effects in ticks. |

### TreatmentAction values

| Value | Global | Description |
|---|---|---|
| `REDUCE_BLEEDING` | no | Sets `treated = true` on target trauma (reduces bleeding to 25%). |
| `SUTURE_WOUND` | no | Sets `sutured = true` (zeroes bleeding). |
| `STABILIZE_FRACTURE` | no | Sets `stabilized = true` on a fracture (halves pain contribution). |
| `RESTORE_BLOOD` | yes | Adds `bloodRestoreMl` to the blood pool. No trauma target. |
| `REDUCE_PAIN` | yes | Sets profile `painSuppression` to `max(current, magnitude)`. |
| `NUMB_LIMB` | no | Sets `localNumbing` to `max(current, magnitude)` on the selected limb. Requires a limb selection. |
| `BOOST_CLOTTING` | yes | Activates a timed clotting boost on the profile. |
| `HEAL_TRAUMA` | no | Reduces severity by `magnitude`; removes trauma if `removesTrauma=true` or severity ≤ 0. |
| `TREAT_BURN` | no | Same as `HEAL_TRAUMA`; conventionally filtered to BURN/CHEMICAL_BURN categories. |
| `TREAT_RADIATION` | no | Same as `HEAL_TRAUMA`; conventionally filtered to RADIATION_BURN. |
