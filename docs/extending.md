# Extending WFMedical

## Adding a new trauma type

Trauma types are data-driven. No Java code changes are required for a new type that fits within existing `TraumaCategory` values.

### 1. Add a `[[trauma]]` entry to `wfmedical_definitions.toml`

```toml
[[trauma]]
id = "nerve_damage"
category = "PUNCTURE"          # or any existing TraumaCategory
major = true
severityContribution = 0.6
painPerSeverity = 0.8           # high pain contribution
bleedingPerSeverity = 0.2
healSpeedPerTick = 0.0          # does not self-heal
canReopen = false
permanent = true                # requires treatment
movementModifier = 1.0
healthReductionPerSeverity = 2.5
maxSeverity = 1.0
mergeable = true
treatmentActions = ["HEAL_TRAUMA"]
```

### 2. If you need a new TraumaCategory

Edit `body/trauma/TraumaCategory.java` and add the constant:

```java
NERVE_DAMAGE(true)   // majorByDefault: true = major unless overridden
```

Then update every site that switches on `TraumaCategory`:

| File | Location | What to add |
|---|---|---|
| `TraumaGenerator.java` | `generate()` switch in `case FIRE ->`, etc. | A new `case` block mapping your category to `add(...)` calls |
| `DamageClassifier.java` | `classify()` method | Detection logic that returns the new `DamageCategory` (if also adding a damage category) |

### 3. If you need a new DamageCategory

Edit `damage/DamageCategory.java` and add the constant. Then update:

| File | Location | What to add |
|---|---|---|
| `DamageClassifier.java` | `classify()` | Detection rule (tag, key, or msgId substring) |
| `DamageCategory.java` | `hitCategory()` | AHF `HitCategory` (new constant there too; AHF `HitSampler.categoryBias`) |
| `TraumaGenerator.java` | `generate()` | Trauma set to generate |
| `DamageConfig.java` | `majorTraumaFraction()` | If you want a per-category instant-kill threshold |
| `DamageConfig.java` | `canInstakillOnImpact()` | If this is a DoT category that should never instant-kill |

---

## Adding a new treatment / medical item

### 1. Register the item

`treatment/TreatmentContent`: add a holder (`MedicalItem`); registered and listed in the creative tab by `TreatmentSystem`. Registry name = `item` key in `wfmedical_definitions.toml`.

### 2. Add its `[[treatment]]` table

`item = "wfmedical:<name>"` => `Treatments.TREATMENTS`; `MedicalItem.getTreatment` reads it per use. No table => item does nothing.

### 3. If you need a new TreatmentAction

Edit `body/trauma/TreatmentAction.java` and add the constant:

```java
SEAL_NERVE(false)   // false = localized (requires a limb target)
```

Then update every dispatch site:

| File | Location | What to add |
|---|---|---|
| `TreatmentService.java` | `applyTargeted()` switch block | A new `case SEAL_NERVE ->` branch with the actual mutation logic |
| `TreatmentService.java` | `priority()` switch | Scoring bonus for `pickTarget` selection |
| `MedicalDefinitions.java` | `parseAction()` | Resolved automatically via `TreatmentAction.valueOf` – no change needed |
| Trauma `treatmentActions` arrays | TOML / `MedicalDefinitions.defaultTraumas` | List `"SEAL_NERVE"` on the trauma types that respond to it |

---

## Adding a new substance (injectable)

### 1. Add a `[[substance]]` entry to `wfmedical_definitions.toml`

```toml
[[substance]]
id = "adrenaline_shot"
item = "wfmedical:adrenaline_syringe"
painSuppression = 0.3
doseLoad = 0.2
overdoseThreshold = 2.0        # hard to overdose
unconsciousTicks = 100
lethalThreshold = 0.0          # not lethal
antidote = false
reversalAmount = 0.0
useDurationTicks = 30
bloodRestoreMl = 0.0
clottingBoost = 0.0
stimulantStrength = 0.5        # moderate stimulant effect
effectTicks = 1200             # 1 minute
```

`SubstanceService.inject` handles the substance generically from `Substance` fields. No code changes are required unless the new substance needs behaviour that does not map to the existing fields.

### 2. Register the item

`substance/SubstanceContent`: add a holder; `InjectableItem` resolves `Substances.SUBSTANCES` by its item RL per use.

### 3. If you need new substance behaviour

Add fields to `Substance` (a Java record), update `SubstanceDefinitions.readSubstance` to read them, and add the handling logic in `SubstanceService.inject`. If the behaviour needs a new profile-level field (like `stimulant` or `clottingBoost`), add it to `MedicalProfile` (with NBT serialization in `save`/`load`), `MedicalSyncPacket` (encode/decode), and `Physiology.compute` (consume it in the derived-stats pass).

---

## Enum-dispatch sites summary

When adding new values to any of the key enums, this is the complete list of files that may need touching:

### Adding a `TraumaCategory`

- `body/trauma/TraumaCategory.java` – add constant
- `damage/TraumaGenerator.java` – `generate()` switch
- Any TOML `[[treatment]]` table – `categories` array

### Adding a `DamageCategory`

- `damage/DamageCategory.java` – add constant
- `damage/DamageClassifier.java` – `classify()` detection rules
- `damage/DamageCategory.java` – `hitCategory()`; AHF `HitCategory` + `HitSampler.categoryBias()`
- `damage/TraumaGenerator.java` – `generate()` / `fractureChance()`
- `damage/DamageConfig.java` – `majorTraumaFraction()` and `canInstakillOnImpact()` if needed

### Adding a `TreatmentAction`

- `body/trauma/TreatmentAction.java` – add constant
- `treatment/TreatmentService.java` – `applyTargeted()` switch and `priority()` switch
- TOML `[[trauma]]` `treatmentActions` arrays (or `MedicalDefinitions.defaultTraumas`)

### Adding a `LimbType`

This is a significant change affecting serialization contracts. Do not reorder existing constants – they are persisted by both ordinal (limb NBT) and name (profile state).

Files to update: `LimbType.java`, `PhysiologyParams.java` (`painShare()`), `LimbType.of`/`part()` + AHF `HitboxPart`/`HumanoidRig`, `MedicalSyncPacket.java` (array size assumptions), and any code that iterates `LimbType.VALUES`.

---

## Anatomy of a complete new item (example)

**Goal:** add `wfmedical:nerve_sealant` that treats `PUNCTURE` wounds reducing severity by 0.5.

1. `TreatmentContent`: `NERVE_SEALANT` holder (`MedicalItem`).
2. `en_us.json`: `"item.wfmedical.nerve_sealant": "Nerve Sealant"`.
3. `wfmedical_definitions.toml` + `TreatmentDefinitions.defaultTreatments`: `[[treatment]]` with `item = "wfmedical:nerve_sealant"`.
4. Item model JSON and texture in `assets/wfmedical/`.
