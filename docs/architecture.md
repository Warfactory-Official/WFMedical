# Architecture

## Package layout

```
com.warfactory.medical
├── api/                    MedicalState — thin read-only API for other mods / mixins
├── capability/             Forge capability: IMedicalData, MedicalData, MedicalCapabilities, MedicalProvider
├── client/
│   ├── overlay/            HUD overlays (health bar, pain vignette, unconscious, action progress)
│   ├── screen/             CharacterSheetUI, RadialMenuUI
│   ├── effect/             Post-process shaders (blood desaturation, passout blur)
│   └── PainSwayHandler     Client-side aim sway from pain
├── compat/
│   ├── OpenPersistenceCompat  Logout body integration
│   └── TaczCompat             TACZ firearm detection
├── config/
│   ├── MedicalConfig       ForgeConfigSpec (COMMON) — numeric tunables and feature toggles
│   └── MedicalDefinitions  TOML loader for trauma/treatment/substance tables
├── core/
│   ├── damage/             DamageClassifier, DamageCategory, ArmorEvaluation, HitLocation, HitGeometry,
│   │                       TraumaGenerator, HitRegMode
│   │   └── rig/            HumanoidRig, Obb — server-side pose replica for geometric hit location
│   ├── limb/               Limb, LimbType
│   ├── substance/          Substance record, SubstanceRegistry
│   ├── trauma/             Trauma, TraumaType, TraumaCategory, TraumaRegistry
│   ├── treatment/          Treatment record, TreatmentAction enum
│   ├── DerivedStats        Immutable computed snapshot (record)
│   ├── HealthState         HEALTHY / CRITICAL / UNCONSCIOUS / DEAD
│   ├── MedicalProfile      Mutable per-player state: limbs, blood pool, drug state, transient flags
│   ├── Physiology          Pure, deterministic DerivedStats computation from MedicalProfile
│   └── PhysiologyParams    Immutable config-snapshot record consumed by Physiology
├── event/                  MedicalEventHandler — Forge bus wiring
├── item/                   ModItems, MedicalItem, InjectableItem
├── mixin/                  Vanilla intercepts (sprint/jump block, camera tilt, hit registration)
├── network/                MedicalSyncPacket, ClientMedicalCache, MedicalNetworking, action packets
└── server/
    ├── MedicalActionService  Timed-treatment tick driver
    ├── MedicalEffects        Vanilla attribute reconciliation (MAX_HEALTH, MOVEMENT_SPEED)
    ├── MedicalEngine         Scheduled physiology engine + lifecycle hooks
    ├── SubstanceService      Injectable substance application
    ├── TreatmentService      Treatment application and trauma targeting
    └── command/              /wfmedical admin commands
```

## End-to-end hit trace

### 1. Event interception — `MedicalEventHandler.onLivingHurt`

`LivingHurtEvent` fires for every incoming hit on a server-side `ServerPlayer` (creative/spectator immune players and bypass-invulnerability sources are let through unchanged).

### 2. Damage classification — `DamageClassifier.classify`

Inspects the `DamageSource` via damage-type tags, well-known `DamageTypes` keys, and the source `msgId` string. TACZ firearm damage is detected first. Returns a `DamageCategory` enum value (`BALLISTIC`, `SLASHING`, `BLUNT`, `UNARMED`, `PIERCING`, `FIRE`, `EXPLOSION`, `CHEMICAL`, `RADIATION`, `FALL`, `GENERIC`).

### 3. Hit location — `HitLocation.pick` / `HumanoidRig`

**Geometric path (default, `geometricHitLocation=true`):** `HitGeometry.classifyHit` reconstructs a world-space impact position from the attacker/projectile direction, then projects it onto six oriented bounding boxes (OBBs) built by `HumanoidRig.compute`. The rig is a server-side replica of `HumanoidModel.setupAnim` — it reads `walkAnimation`, arm pose, crouch, swim/elytra state, and (when `taczArmPose` is on) the synced TACZ aiming progress to match what the client renderer draws. OBBs are padded by `limbBoxPadding` to absorb pose-replica drift.

**Weighted fallback:** When geometric reconstruction fails (no traceable direction) or is disabled, `HitLocation.pickWeighted` samples from the six limb hit weights, biased by category (ballistic/piercing skew head+torso, fall skews legs, explosion spreads toward extremities).

**Hit registration:** `HitRegMode` controls the pre-physics collision box used for registering whether a hit counts at all: `OFF` = vanilla tight box; `ENVELOPE` = box inflated by `hitEnvelopeInflation` (default 0.15 blocks) so arm and prone hits register; `PRECISE` = envelope with a rig-gap rejection pass that cancels shots that threaded the gap between limb OBBs.

### 4. Armor evaluation — `ArmorEvaluation.evaluate`

Reads `Attributes.ARMOR` / `Attributes.ARMOR_TOUGHNESS` plus the durability of the armor piece covering the struck limb. Computes a probabilistic mitigation score and rolls for three outcomes:

| Outcome | Effect |
|---|---|
| `BLOCKED` | Trauma generated is bruise-only; a small residual vanilla damage (15%, capped at 1 pt) is left so armor feedback reads correctly |
| `PARTIAL` | Bruise + small laceration |
| `FULL` | Full trauma set for the category |

Effectiveness varies: armor is rated `0.45×` effective against `BALLISTIC`, `0.6×` against `PIERCING`, `1.0×` against `SLASHING`, `1.2×` against `BLUNT`. Elemental damage (`FIRE`, `CHEMICAL`, `RADIATION`, `FALL`) always `FULL` (not stopped by physical armor).

### 5. Instant-death check

Before generating trauma, the event handler tests whether this is a **major-trauma** (instant-kill) hit:

```
outcome != BLOCKED
&& canInstakillOnImpact(category)          // not FIRE/CHEMICAL/RADIATION
&& amount >= maxHealthPoints * majorTraumaFraction(category)
```

`majorTraumaFraction` is per-category and config-tunable (see [config.md](config.md)). If true, the player is immediately marked `DEAD` and the vanilla damage is raised above current health so `die()` fires.

### 6. Trauma generation — `TraumaGenerator.generate`

Maps `(DamageCategory, ArmorOutcome, LimbType, energy)` to a list of `Trauma` objects. Types are resolved from `TraumaRegistry` by canonical id (falling back to the first type of the matching `TraumaCategory`). Severity scales with `energy × 0.1`, clamped to `[0.1, 1.5]`. Fracture generation is probabilistic (`BALLISTIC` 35% base, `EXPLOSION` 50%, `FALL` on legs 60%).

### 7. Profile mutation — `Limb.tryMerge`

Generated traumas are merged into the target `Limb` on the `MedicalProfile`. Compatible same-type wounds on the same limb are folded together; the per-limb cap (`maxTraumaPerLimb`) is enforced by dropping the least-severe minor trauma and merging its severity into the most-severe compatible wound. The limb and profile are marked dirty.

### 8. Engine tick — `MedicalEngine.onServerTick`

Runs every server tick via `TickEvent.ServerTickEvent`, but only does real work every `updateIntervalTicks` (default 10) ticks. Per-player work:

1. Skip creative/spectator-immune and health-≤-0 players.
2. **Fast path:** skip players where `isActive` returns false (no dirty flag, no bleeding, no pain, no drugs, full blood).
3. Advance active timed treatment (`MedicalActionService.tick`).
4. Drain blood from cached `totalBleeding` aggregate × `interval` (if `enableBleeding`).
5. `advanceTrauma`: minor trauma regenerates, untreated major trauma worsens (except self-clotting bleeds and self-knitting fractures), treated major heals at `healSpeedPerTick`.
6. `advanceSubstances`: stimulant/clotting boost timers, overdose-unconsciousness timer, drug-load decay, severe-overdose health drain.
7. Recompute: calls `MedicalProfile.recompute(params)` only when dirty — this rebuilds only dirty limb caches then calls `Physiology.compute`.
8. Adrenaline grace timer for pain-driven KOs.
9. Death enactment: if `state == DEAD` and vanilla health > 0 → `setHealth(0)`.
10. `MedicalEffects.apply`: reconcile vanilla `MAX_HEALTH` / `MOVEMENT_SPEED` attribute modifiers.
11. Revision bump + sync if dirty.
12. Downed-state edge broadcast (`DownedStatePacket` to trackers on enter/exit change only).

Breathing / asphyxia runs separately on **every** player tick (`TickEvent.PlayerTickEvent`) via `MedicalEngine.tickBreathing` — it needs to respond every tick, not on the throttled cadence.

### 9. Pure physiology — `Physiology.compute`

Allocation-free, deterministic, no side effects. Reads per-limb cached aggregates and profile-level state, returns a `DerivedStats` record. Main calculations:

- **Pain:** Per-limb raw pain → saturation curve → local anesthetic → systemic analgesia mask → `perceivedPain` (max across limbs) and `systemicPain` (weighted sum capped at 1.0).
- **Blood loss penalty:** ramps max-health down from `bloodLowFraction` to `bloodDeathMl`.
- **Pain-shock penalty:** adds up to `painMaxHealthPenalty` (10 pts) to the health modifier when `systemicPain > painShockThreshold`.
- **Health modifier:** `limbHealthReduction + bloodLossPenalty + painShockPenalty` (see note below).
- **Unconsciousness score:** `bloodScore + painScore`; reaching 1.0 triggers `UNCONSCIOUS`.
- **State:** `DEAD > UNCONSCIOUS > CRITICAL > HEALTHY`, plus overdose/asphyxia flags that raise the state.
- **Movement/jump:** affected only by leg injuries and blood loss past `bloodMovementPenaltyLossFraction`; stimulant overrides all penalties.

> **Note on health reduction:** The current code sums `limb.getCachedHealthReduction()` across all limbs without a per-limb cap (`Physiology.java` line 22). Each limb's health reduction = `type.healthReductionPerSeverity * severity` only for major trauma. This per-limb total feeds directly into `healthModifier` with no ceiling per limb. A capped-share model (each limb bounded by a fraction of the max-health pool) is planned but not yet implemented.

### 10. Vanilla body reconciliation — `MedicalEffects.apply`

Applies two transient `AttributeModifier`s (fixed UUIDs, so they never stack):

- `MAX_HEALTH` (`ADDITION`): `effectiveMaxHealth - vanillaBase` — sets total max health to the derived value (default 30 HP = 15 hearts when healthy, shrinks with trauma/blood loss).
- `MOVEMENT_SPEED` (`MULTIPLY_TOTAL`): `movementMultiplier - 1.0` — forced to −1.0 (zero speed) when `UNCONSCIOUS`.

Current health is clamped downward toward the derived target (normal ticking never heals); `allowRaise=true` on join/respawn sets it exactly. Unconscious players are pinned to ≥ 1 HP unless they are already at 0 (death finalized).

Sprint blocking and jump scaling are handled by `LivingEntityMixin` and `PlayerMixin` reading `MedicalState.getCached()`.

### 11. Sync — `MedicalSyncPacket` / `ClientMedicalCache`

`MedicalNetworking.sendFull` encodes the authoritative `MedicalProfile` into a `MedicalSyncPacket` (full snapshot — `DerivedStats` + blood pool + pain suppression + drug load + health state + per-limb summaries + `deathProgress`) and sends it to the player. **There is no incremental delta packet;** the full snapshot is the sole S2C state channel.

The client handler (`MedicalSyncPacket.handleClient`) writes the packet into `ClientMedicalCache` (a `volatile` singleton). Overlays and HUD code read from `ClientMedicalCache.stats()` / `ClientMedicalCache.get()`.

A separate `DownedStatePacket` is broadcast to **nearby tracking players** (not just self) whenever the downed predicate changes, so observers can render the prone/unconscious pose.

### Server-authoritative design

All physiology state lives on the server (`MedicalProfile` on the Forge capability). The client holds only the last-received snapshot; it never writes to the profile. Treatment applications flow C2S via `MedicalActionPacket` → `TreatmentService`/`SubstanceService` on the server → full resync S2C.

The engine skips creative/spectator-immune players entirely and keeps health-≤-0 players frozen until respawn to avoid interactions with the vanilla respawn flow.

### Capability plumbing

`MedicalCapabilities.MEDICAL` (`IMedicalData`) is attached to every `Player` and (optionally) to Open Persistence logout bodies via `AttachCapabilitiesEvent`. `MedicalProvider` handles `serializeNBT`/`deserializeNBT` and revocation. `MedicalCapabilities.copy` round-trips through NBT on dimension change (non-death clone). Death respawns start with a fresh default profile. The capability holds a `MedicalProfile` plus a revision counter and sync-dirty flag.

### Dirty / cache / revision scheme

- `Limb.dirty` — set whenever the trauma list or local numbing changes; triggers `rebuildCache()` on the next recompute pass.
- `MedicalProfile.dirty` — set whenever any field (blood, drug load, limb, suppression) changes; causes the engine to call `profile.recompute(params)` on the next pass instead of using the cached `DerivedStats`.
- `IMedicalData.revision` — a monotonic integer bumped on every authoritative change; compared to `lastSentRevision` to decide whether a sync packet is needed.
