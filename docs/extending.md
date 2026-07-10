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

Edit `core/trauma/TraumaCategory.java` and add the constant:

```java
NERVE_DAMAGE(true)   // majorByDefault: true = major unless overridden
```

Then update every site that switches on `TraumaCategory`:

| File | Location | What to add |
|---|---|---|
| `TraumaGenerator.java` | `generate()` switch in `case FIRE ->`, etc. | A new `case` block mapping your category to `add(...)` calls |
| `DamageClassifier.java` | `classify()` method | Detection logic that returns the new `DamageCategory` (if also adding a damage category) |

### 3. If you need a new DamageCategory

Edit `core/damage/DamageCategory.java` and add the constant. Then update:

| File | Location | What to add |
|---|---|---|
| `DamageClassifier.java` | `classify()` | Detection rule (tag, key, or msgId substring) |
| `ArmorEvaluation.java` | `categoryEffectiveness()` | Armor effectiveness factor |
| `HitLocation.java` | `categoryBias()` | Per-limb hit-probability bias |
| `TraumaGenerator.java` | `generate()` | Trauma set to generate |
| `MedicalConfig.java` | `majorTraumaFraction()` | If you want a per-category instant-kill threshold |
| `MedicalConfig.java` | `canInstakillOnImpact()` | If this is a DoT category that should never instant-kill |

---

## Adding a new treatment / medical item

### 1. Register the item

In `ModItems.java`, register a `MedicalItem` (for timed use) or `InjectableItem` (for substances). The item's registry name is the key used in `wfmedical_definitions.toml`.

### 2. Embed the treatment in the item registration

**Important:** The `[[treatment]]` tables in `wfmedical_definitions.toml` are parsed by `MedicalDefinitions.load` into a local `Map<String, Treatment>` but this map is currently not stored globally – it is not wired up to override the runtime behavior of existing items. The actual treatment an item applies comes from the `Treatment` object embedded in the `MedicalItem` at registration time in `ModItems.java`.

Substances are different: `SubstanceService` does look up the live config-loaded definition from `SubstanceRegistry.active()` at inject time, so TOML changes to substances take effect at runtime. Treatment item behavior does not have this hookup yet.

To add a new treatment item, add a registration in `ModItems.java`:

```java
public static final RegistryObject<Item> NERVE_SEALANT = medical("nerve_sealant",
    new Treatment(TreatmentAction.HEAL_TRAUMA,
        cats(TraumaCategory.PUNCTURE), 0.5F, 0.0D, 80, false));
```

The `[[treatment]]` entry in TOML documents the intent but is not currently applied at runtime for treatment items.

### 3. If you need a new TreatmentAction

Edit `core/treatment/TreatmentAction.java` and add the constant:

```java
SEAL_NERVE(false)   // false = localized (requires a limb target)
```

Then update every dispatch site:

| File | Location | What to add |
|---|---|---|
| `TreatmentService.java` | `applyTargeted()` switch block | A new `case SEAL_NERVE ->` branch with the actual mutation logic |
| `TreatmentService.java` | `priority()` switch | Scoring bonus for `pickTarget` selection |
| `MedicalDefinitions.java` | `parseAction()` | Resolved automatically via `TreatmentAction.valueOf` – no change needed |
| Trauma `treatmentActions` arrays | TOML / `loadDefaults` | List `"SEAL_NERVE"` on the trauma types that respond to it |

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

Register an `InjectableItem` in `ModItems.java`. The `InjectableItem` constructor takes the substance id; `SubstanceRegistry.active().get(substance.itemId())` resolves the live config-loaded definition at inject time, so TOML changes take effect without restarting.

### 3. If you need new substance behaviour

Add fields to `Substance` (a Java record), update `MedicalDefinitions.readSubstance` to read them, and add the handling logic in `SubstanceService.inject`. If the behaviour needs a new profile-level field (like `stimulant` or `clottingBoost`), add it to `MedicalProfile` (with NBT serialization in `save`/`load`), `MedicalSyncPacket` (encode/decode), and `Physiology.compute` (consume it in the derived-stats pass).

---

## Enum-dispatch sites summary

When adding new values to any of the key enums, this is the complete list of files that may need touching:

### Adding a `TraumaCategory`

- `core/trauma/TraumaCategory.java` – add constant
- `core/damage/TraumaGenerator.java` – `generate()` switch
- Any TOML `[[treatment]]` table – `categories` array

### Adding a `DamageCategory`

- `core/damage/DamageCategory.java` – add constant
- `core/damage/DamageClassifier.java` – `classify()` detection rules
- `core/damage/ArmorEvaluation.java` – `categoryEffectiveness()`
- `core/damage/HitLocation.java` – `categoryBias()`
- `core/damage/TraumaGenerator.java` – `generate()` / `fractureChance()`
- `config/MedicalConfig.java` – `majorTraumaFraction()` and `canInstakillOnImpact()` if needed

### Adding a `TreatmentAction`

- `core/treatment/TreatmentAction.java` – add constant
- `server/TreatmentService.java` – `applyTargeted()` switch and `priority()` switch
- TOML `[[trauma]]` `treatmentActions` arrays (or `loadDefaults` in `MedicalDefinitions`)

### Adding a `LimbType`

This is a significant change affecting serialization contracts. Do not reorder existing constants – they are persisted by both ordinal (limb NBT) and name (profile state).

Files to update: `LimbType.java`, `PhysiologyParams.java` (`painShare()`), `HumanoidRig.java` (OBB part definition), `MedicalSyncPacket.java` (array size assumptions), `HitLocation.java` (`categoryBias()`), and any code that iterates `LimbType.VALUES`.

---

## Anatomy of a complete new item (example)

**Goal:** add `wfmedical:nerve_sealant` that treats `PUNCTURE` wounds reducing severity by 0.5.

1. `ModItems.java`: register `NERVE_SEALANT = new MedicalItem(...)`.
2. `ModCreativeTab.java`: add to the creative tab output list.
3. `en_us.json`: add `"item.wfmedical.nerve_sealant": "Nerve Sealant"`.
4. `wfmedical_definitions.toml`: add a `[[treatment]]` table for documentation purposes (not yet applied at runtime for treatment items – the embedded `Treatment` in step 1 is what runs).
5. Provide item model JSON and texture in `assets/wfmedical/`.

The treatment pipeline (`MedicalItem.finishUsingItem` → `TreatmentService.applyTargeted`) uses the `Treatment` embedded in the item. For injectables, `InjectableItem` routes through `SubstanceService.inject`, which does look up the live TOML-loaded `Substance` from `SubstanceRegistry.active()`.
