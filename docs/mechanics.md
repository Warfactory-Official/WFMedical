# Mechanics

## Trauma types and categories

Each wound is a `Trauma` object: a `TraumaType` definition plus a mutable `severity` float clamped to `[0, maxSeverity]`. Types are registered in `wfmedical_definitions.toml` (or the programmatic fallback in `MedicalDefinitions.loadDefaults`).

### TraumaCategory

Controls default major/minor status and is used to filter which items can treat which wounds.

| Category | Major by default | Notes |
|---|---|---|
| `BRUISE` | no | Minor, regenerates automatically |
| `LACERATION` | yes | Bleeds; can reopen after treatment |
| `FRACTURE` | yes | No bleed; leg fractures slow/block movement; arm fractures impair melee and aim |
| `BURN` | yes | Slight bleed; treated with ointment |
| `INTERNAL_BLEEDING` | yes | Heavy bleed; permanent (needs treatment to heal) |
| `PUNCTURE` | yes | Deep bleed; can reopen |
| `CRUSH_INJURY` | yes | Heavy health hit; mild bleed; data-driven movement modifier |
| `RADIATION_BURN` | yes | Permanent; treated with anti-rad |
| `CHEMICAL_BURN` | yes | Similar to burn; treated with ointment |

### TraumaType fields

| Field | Type | Meaning |
|---|---|---|
| `id` | string | Registry key used in NBT and TOML |
| `category` | `TraumaCategory` | Broad category |
| `major` | bool | If true, reduces effective max-health while active; if false, does not |
| `severityContribution` | float | Base severity inflicted when the trauma is first generated |
| `painPerSeverity` | float | Pain units contributed per unit severity |
| `bleedingPerSeverity` | float | Blood drained (ml/tick) per unit severity |
| `healSpeedPerTick` | float | Natural severity reduction per tick (0 = never heals on its own) |
| `canReopen` | bool | Wound can start bleeding again after a bandage (`treated` flag) is cleared on re-injury |
| `permanent` | bool | Never removed by natural healing; requires treatment |
| `movementModifier` | float | Speed multiplier applied when on a leg (1.0 = no effect; leg-fracture penalty handled separately via config) |
| `healthReductionPerSeverity` | float | Max-health points removed per unit severity (only for major trauma) |
| `maxSeverity` | float | Ceiling for merged/stacked severity |
| `mergeable` | bool | Compatible instances stack together instead of creating a second object |
| `treatmentActions` | string[] | `TreatmentAction` names this trauma responds to |

### Default trauma types

| id | Category | Major | Bleeding (ml/tick/sev) | Pain/sev | Health pts/sev | Permanent |
|---|---|---|---|---|---|---|
| `bruise` | BRUISE | no | 0.0 | 0.15 | 0.0 | no |
| `laceration_small` | LACERATION | no | 0.4 | 0.2 | 1.0 | no |
| `laceration_large` | LACERATION | yes | 1.2 | 0.6 | 4.0 | no |
| `fracture` | FRACTURE | yes | 0.0 | 0.5 | 3.0 | no |
| `burn` | BURN | yes | 0.1 | 0.55 | 3.0 | no |
| `internal_bleeding` | INTERNAL_BLEEDING | yes | 2.0 | 0.4 | 5.0 | yes |
| `puncture` | PUNCTURE | yes | 0.9 | 0.5 | 3.5 | no |
| `crush_injury` | CRUSH_INJURY | yes | 0.3 | 0.6 | 4.0 | no |
| `radiation_burn` | RADIATION_BURN | yes | 0.0 | 0.45 | 4.0 | yes |
| `chemical_burn` | CHEMICAL_BURN | yes | 0.2 | 0.5 | 3.5 | no |

### Trauma status flags

A `Trauma` has three boolean flags that modify its contribution:

- `treated` – reduces bleeding to 25% of base (bandage applied); does not close the wound.
- `sutured` – zeroes bleeding completely (wound closed); implies `treated`.
- `stabilized` – reduces pain to 50% of base (splint applied to a fracture); does not zero it.

### Per-limb cap

Each limb holds at most `maxTraumaPerLimb` (default 8) distinct `Trauma` objects. When the count would exceed the cap, the least-severe minor trauma (or least-severe overall if none is minor) is folded into the most-compatible or most-severe surviving wound via `Limb.enforceCap`.

---

## Pain

Pain is the most layered system. Two aggregates emerge from `Physiology.compute`:

- **perceivedPain** – the highest masked pain across all six limbs. Drives the vignette overlay, aim sway, and the HUD display.
- **systemicPain** – a weighted sum of masked pain, clamped to [0, 1]. Drives shock (max-health penalty) and unconsciousness.

### Computation pipeline (per-limb)

```
raw               = sum of (trauma.painPerSeverity * severity) on this limb
local             = raw / (raw + painSaturationK)          -- diminishing returns, 0..1
local            *= (1 - localNumbing)                     -- local anesthetic on this limb
masked            = local - analgesia                      -- systemic analgesia floor
                                                            (painkillers; also stimulant if stronger)
```

`perceivedPain = max(masked)` across all limbs.
`systemicPain = min(sum(painShare(lt) * masked), 1.0)` across all limbs.

### Pain shares (systemic weight per limb)

Arms carry a deliberately small share so an agonizing arm injury cannot, on its own, cause shock – it hurts but does not incapacitate.

| Limb | Config key | Default |
|---|---|---|
| HEAD | `painShareHead` | 0.35 |
| TORSO | `painShareTorso` | 0.50 |
| LEFT_ARM / RIGHT_ARM | `painShareArm` | 0.10 each |
| LEFT_LEG / RIGHT_LEG | `painShareLeg` | 0.20 each |

The shares sum to more than 1.0 by design (0.35 + 0.50 + 2×0.10 + 2×0.20 = 1.45). Systemic pain is clamped to 1.0 after summing, so it takes injuries across multiple body regions to reach full shock.

### Saturation curve

`painSaturationK` (default 1.0) controls diminishing returns on a single limb. With k=1.0, raw pain of 1.0 → local = 0.5; raw pain of 3.0 → local = 0.75. A smaller k saturates faster (one wound on the same limb dominates quickly); a larger k requires many or worsening wounds to saturate.

> **Under active revision:** The pain model is currently implemented as described. The planned change is to cap each limb's contribution to the systemic pain sum at its `painShare` – the current code already does this correctly – but the per-limb health reduction going into `effectiveMaxHealth` is still summed without a cap (see [architecture note](architecture.md#9-pure-physiology--physiologycompute)).

### LOCAL anesthetic vs GENERAL analgesia

- **GENERAL** (`REDUCE_PAIN` / `analgesia` / `painSuppression` on `MedicalProfile`): A body-wide subtractive mask applied identically to every limb's local pain. Small pains vanish entirely; severe ones are merely lessened. Decays over time (`PAIN_SUPPRESSION_DECAY_PER_TICK = 0.0008/tick`). From painkillers or opioids.
- **LOCAL** (`NUMB_LIMB` / `localNumbing` on `Limb`): Strongly reduces pain from one specific limb without touching others. Must be aimed at a limb (the server refuses it with no limb selected). Decays slower (`LOCAL_NUMB_DECAY_PER_TICK = 0.0005/tick`). From local anesthetic.

### Pain effects

- `systemicPain > painShockThreshold` (default 0.60): pain-shock penalty begins, removing up to `painMaxHealthPenalty` (10 pts) from effective max health at full systemic pain.
- `systemicPain > painUnconsciousThreshold` (default 0.70): pain score starts feeding the unconsciousness score (reaching 1.0 can knock the player out).
- Client effects: vignette overlay, aim sway (scale by `painSwayStrength`), red desaturation shader.

### Adrenaline-delayed pain KO

When `adrenalineEnabled` is true, a **purely pain-driven** knockout (one that blood loss alone would not cause) is held off for `adrenalinePainKoDelayTicks` (default 120 ticks = 6 seconds). During the grace period the player stays conscious despite the KO score reaching 1.0. If pain drops below knockout level before the timer expires, the timer resets and the adrenaline "recharges." Blood-loss-driven knockouts bypass this delay entirely.

---

## Blood loss and bleeding

Each `Trauma` contributes `type.bleedingPerSeverity * severity` ml/tick to the limb's cached bleed aggregate (0 if sutured; ×0.25 if treated-but-not-sutured). The engine drains `totalBleeding * interval` ml from `bloodMl` every engine pass when `enableBleeding` is on.

`bloodMl` starts at `maxBloodMl` (default 5000 ml). Loss fraction = `1 - (bloodMl / maxBloodMl)`.

### Blood loss thresholds

| Fraction lost | Effect |
|---|---|
| ≥ `bloodMovementPenaltyLossFraction` (25%) | Walk/jump speed begins ramping down toward `painSpeedFloor` (0.30) |
| ≥ `bloodCriticalFraction` (35%) blood REMAINING | State becomes CRITICAL |
| ≥ `bloodUnconsciousLossFraction` (30% lost) | Blood score starts feeding the unconsciousness sum |
| ≥ `bloodDeathLossFraction` (40% lost) | `bloodDeath = true` → state = DEAD |

Blood penalizes speed only via `bloodMovementMultiplier`; it never affects aim or pain directly.

### Natural clotting

An untreated bleeding wound whose `severity <= bleedingSelfHealThreshold` (default 0.30) slowly closes on its own at `bleedingSelfHealRate` (default 0.0003/tick). Above the threshold it worsens at `MAJOR_WORSEN_PER_TICK = 0.00015/tick` until treated.

### Clotting boost

`BOOST_CLOTTING` items (e.g. hemostatic agent) grant a timed `clottingBoost` (0..1) for `clottingAgentDurationTicks` (default 2400 = 2 min). This raises:
- Self-clot threshold: `threshold + clottingBoost * clottingBoostThresholdBonus` (default: up to 1.0 at full boost – even severe bleeds close)
- Self-clot rate: `rate * (1 + clottingBoost * clottingBoostRateMultiplier)` (default: up to 11× normal speed)

Combat Stimulant I also applies `clottingBoost = 1.0` for `effectTicks`.

### Blood restoration

`RESTORE_BLOOD` items (blood bag) add `bloodRestoreMl` (default 1000 ml) directly to the pool. Some items have a secondary `bloodRestoreMl` field (e.g. the medkit restores 250 ml alongside treating trauma).

---

## Fractures

Fractures use `TraumaCategory.FRACTURE` and `Trauma.isFracture()`. The feature can be disabled globally with `enableFractures=false`.

### Leg fractures

- `Physiology.compute` applies `legFractureSpeedMultiplier` (default 0.40) **per fractured leg** (multiplicative). Two broken legs: 0.40 × 0.40 = 0.16 × base speed.
- A fracture on any leg sets `sprintBlocked = true` and `jumpMultiplier = 0.0`.
- The data-driven `movementModifier` on the `fracture` trauma type stays 1.0 specifically to avoid double-penalizing legs and to keep arm fractures from reducing walking speed.

### Arm fractures

- `Weakness` effect at level `brokenArmMeleeWeaknessLevel` (default 1 = Weakness I) re-applied with 60-tick duration every engine pass while any arm is fractured.
- Aim sway floor: `brokenArmAimSway` (default 0.9) – forced even when aiming down sights with a bow/crossbow/TACZ gun.

### Fracture self-healing

Untreated fractures self-heal over `fractureSelfHealMinutes` (default 20 real minutes). A full-severity fracture heals at `1 / (fractureSelfHealMinutes * 1200)` per tick; partial fractures heal proportionally faster. Setting `fractureSelfHealMinutes = 0` disables self-healing (fractures persist until treated).

Applying a splint (`STABILIZE_FRACTURE`) sets `stabilized = true`, halving the pain contribution and enabling the type's `healSpeedPerTick` (faster recovery than untreated self-heal).

---

## Asphyxia

Asphyxia is a unified suffocation condition with two distinct causes, sharing the same lifecycle.

### Causes

- **Drowning:** player is underwater with air supply ≤ 0. Requires `drowningAsphyxiaEnabled = true`. Vanilla `DROWN` damage is canceled; the asphyxia system replaces it entirely.
- **Drug respiratory depression:** heavy opioid overdose. Requires `asphyxiaEnabled = true`. Triggered probabilistically (`asphyxiaChance`, default 0.35) when `drugLoad >= asphyxiaThreshold` (default 1.0) on injection, provided the player is still conscious and not already asphyxiating. A severe overdose (`drugLoad >= overdoseLethalThreshold`) skips asphyxia and blacks out immediately.

### Phases

| Phase | Flag | Duration | Effects |
|---|---|---|---|
| Conscious struggle | `asphyxiating = true` | `asphyxiaStruggleTicks` (default 60 = 3s) | Speed × `asphyxiaMoveMultiplier` (default 0.25); sprint/jump blocked; Weakness at `asphyxiaWeaknessAmplifier` (default 1 = Weakness II); air drains at `asphyxiaAirLossPerTick` (12/tick) |
| Unconscious | `asphyxiaUnconscious = true` | `asphyxiaUnconsciousTicks` (default 200 = 10s) | State forced to `UNCONSCIOUS`; death deadline ticking |
| Fatal | – | – | `killByAsphyxia` drops health to 0 |

**Recovery:** clearing the cause at any phase recovers cleanly. For drowning: reach the surface. For drug: naloxone injection or waiting for `drugLoad` to decay below `asphyxiaThreshold`. Recovery is immediate – air restores naturally from that point.

The heavy movement, blur shader, and vignette are driven by the `asphyxiating` and `UNCONSCIOUS` state fields in `DerivedStats`, which are synced in `MedicalSyncPacket`.

---

## Drugs

Substances are injectable items defined in `wfmedical_definitions.toml`. The system can be disabled globally with `enableInjectables = false`.

### Opioids (morphine, combat stimulant)

On injection (`SubstanceService.inject`):

1. `painSuppression` on the profile is raised to `max(current, substance.painSuppression)`.
2. `drugLoad += doseLoad`. The load decays at `drugDecayPerTick` (default 0.00035/tick ≈ one morphine dose in ~24 minutes).
3. Timed effects (stimulant / clotting boost) are activated for `effectTicks` ticks.

### Drug load and overdose

When `drugLoad >= overdoseThreshold`:
- If asphyxia conditions are met and the roll succeeds: conscious asphyxia phase begins.
- Otherwise: `overdoseUntilTick = now + unconsciousTicks`; `overdoseUnconscious = true`. This forces the state to `UNCONSCIOUS` (1-HP pin, locked movement, downed pose).

### Lethal overdose (severe)

When `drugLoad >= overdoseLethalThreshold` (and `overdoseLethalEnabled = true`):
- The player remains unconscious even after `overdoseUntilTick` elapses (the timer never clears them).
- Health drains at `overdoseLethalDrainPerTick` (default 0.05 HP/tick) while unconscious.
- When health would cross the 1-HP UNCONSCIOUS pin floor → `enactEngineDeath` fires (state = `DEAD`, health = 0). This makes an untreated severe overdose genuinely fatal.

The beneficial window (stimulant speed, clotting, analgesia) ends at `stimulantEndTick`, but `drugLoad` continues decaying much more slowly – the "crash" / come-down risk window.

### Naloxone (antidote)

On injection: `drugLoad -= reversalAmount` (default 3.0 – enough to drop from severe overdose to zero); overdose unconsciousness and asphyxia end immediately; `painSuppression` drops to 0 (all masked pain returns at once). This is the only counter-play to an active overdose.

### Default substances

| id | doseLoad | overdoseThreshold | lethalThreshold | painSuppression | Notes |
|---|---|---|---|---|---|
| `morphine` | 0.5 | 1.0 | 1.6 | 0.95 | 2 doses → overdose; ≥3.2 load → lethal |
| `naloxone` | – | – | – | – | antidote; reversalAmount = 3.0 |
| `combat_stimulant_i` | 1.4 | 1.6 | 2.6 | – (via stimulant) | stimulantStrength = 0.97, clottingBoost = 1.0, effectTicks = 3600 (3 min) |

### Combat Stimulant I

While the stimulant is active (`stimulant > 0`):
- Analgesia floor = `max(analgesia, stimulant)` – acts as near-total pain immunity.
- Speed = `max(injured_speed, 1.0 + stimulantSpeedBonus * stimulant)` – overrides injury slowdown and boosts above normal (default +30%).
- Jump penalty cleared (even with broken legs).
- Clotting boost = 1.0 for the same `effectTicks` window.

One dose adds `doseLoad = 1.4`, just below the overdose threshold of 1.6. A second dose stacks to 2.8, past the lethal threshold of 2.6, making re-dosing during the come-down dangerous.

---

## Death vs unconsciousness

### Instant death (major trauma)

Any single hit that:
1. Was **not** `ArmorEvaluation.Outcome.BLOCKED`
2. Belongs to a category that `canInstakillOnImpact` (not FIRE/CHEMICAL/RADIATION)
3. Has raw damage `>= maxHealthPoints * majorTraumaFraction(category)`

…kills outright. The profile is immediately set to `DEAD`, the vanilla damage is raised to exceed current health, and the vanilla death pipeline runs. Unconsciousness is not an intermediate step.

This applies equally to live players and Open Persistence logout bodies (which receive the same `resolveHit` call and are destroyed on a major-trauma hit).

### Per-category thresholds

| DamageCategory | `majorTraumaFraction` default | Notes |
|---|---|---|
| `BALLISTIC` | 0.9 | A full-health bar's worth of bullet in one hit |
| `EXPLOSION` | 0.9 | Blast equivalent |
| `BLUNT` | 1.1 | Slightly above full bar – requires a very heavy impact |
| `UNARMED` | 3.0 | Effectively impossible to one-punch; punches only bruise |
| `FALL` | 1.5 | Only a catastrophic fall; lesser falls break legs |
| DEFAULT (slashing, piercing, generic) | 1.0 | Exactly a full-health bar's worth in one blow |
| FIRE / CHEMICAL / RADIATION | never | Damage-over-time; never instant-kill on impact |

### Gradual depletion → survivable unconsciousness

When `enableBleedout = true` (default), a lethal condition derived purely from accumulated damage (KO score reaching 1.0, blood loss passing `bloodDeathLossFraction`) triggers `UNCONSCIOUS` rather than instant death. The `MedicalEffects` 1-HP pin keeps the player alive. The client overlay's `deathProgress` ramps 0 → 1 as blood loss approaches the death fraction, signaling how close death is. When blood loss reaches `bloodDeathLossFraction` (40%), `bloodDeath = true` → state = `DEAD` → engine drops health to 0.

When `enableBleedout = false`, the same conditions map directly to `DEAD`.

A downed player can be killed instantly by any subsequent real damage if `finishDownedOnHit = true` (default), without going through the blood-loss progression.

---

## Persistent bodies (Open Persistence integration)

When `openPersistenceCompat = true` and Open Persistence is installed:

**On logout:** The player's full `MedicalProfile` (serialized via NBT) is copied onto the logout body entity. The body's max-health is set to the player's derived max (default 30 HP) via a permanent `AttributeModifier`, and current health is set to the player's derived current health. The body does not run live physiology or bleed-out ticks while the player is offline.

**While offline:** Hits on the body go through the same `resolveHit` pipeline as live players. Major-trauma hits destroy the body (`profile.enterDeadState`, vanilla damage raised to lethal). Survivable hits accumulate trauma on the carried profile. Bleeding is not simulated offline.

**On login:** The body's profile is copied back onto the player (possibly worse than at logout), and `MedicalEngine.resync` is called immediately to apply the current injuries to the vanilla body.

This is the anti-combat-log mechanic: a player cannot safely log out mid-fight, as their body carries their wounds and can be killed.
