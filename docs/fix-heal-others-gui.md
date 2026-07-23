# Fixing the "heal another player via GUI" system

Task briefing for an AI/dev session working on **WFMedical** (Minecraft 1.20.1 Forge, LDLib-based UI).
This document maps the entire treat-another-player flow, states what has already been audited as
correct (do not re-suspect it), and lists the concrete defects found, ranked, each with a failure
scenario and a suggested fix. All paths are relative to the repo root; line numbers are from
commit `1d4b914` (pre-fix).

## Resolution status (2026-07-19, updated 2026-07-23)

Findings **F1, F2, F3, F4 and most of F5 are FIXED** in the working tree (each finding below carries a
✅ note describing the implemented change). F1–F3 + F5 landed in commit `40e36c1`; **F4 was built on
2026-07-23** after the project owner opted in (open-sheet key now examines/treats the aimed-at teammate —
see the F4 note). Compile-verified (`./gradlew compileJava`); the in-game scenarios in §3 still need a
2-client manual pass. Notable implementation choices:

- The **self** localized flow now also round-trips through `TreatmentTargetRequestPacket`
  (`targetEntityId = -1`), because only the server can compute the per-limb treatable mask (the
  client's `LimbSummary` cache cannot distinguish trauma from minor-damage pools).
- `TreatmentTargetInfoPacket` gained a `treatableMask` int; `RemoveTourniquetPacket` gained a
  `targetEntityId`; `SetTargetLimbPacket` (id 3) was deleted. The channel `PROTOCOL` was bumped
  `"1"` → `"2"` accordingly.
- New lang keys: `gui.wfmedical.treat.no_treatable`, `.no_effect`, `.applied`, `.busy`.

---

## 1. How healing another player is SUPPOSED to work

There are two separate GUI systems. Only the **limb wheel** flow can target another player:

| GUI | Opened by | Target |
|---|---|---|
| `LimbWheelScreen` (donut wheel) | Right-clicking with a `MedicalItem` in main hand | Self **or** aimed-at player / downed body |
| `MedInteractionScreen` (EXAMINATION / TREATMENT sheet) | `OPEN_SHEET` key (`MedicalClientEvents.onClientTick`, line 63) | **Self only** (hard-wired) |
| `RadialMenuUI` (legacy G-key radial) | `OPEN_RADIAL` key | Self only |

### End-to-end flow for treating another player

1. **Client input** — `client/TreatmentInteractions.onUseInput` (line 67) intercepts the use-key
   `InputEvent.InteractionKeyMappingTriggered`, cancels vanilla handling for main-hand `MedicalItem`s,
   and calls `beginTreatment` (line 95).
2. **Target pick** — `pickTargetId` (line 177) reads `mc.hitResult`; another `Player` (or an
   Open-Persistence logout body, config-gated) under the crosshair yields its entity id, else `-1` (self).
3. **Branch on treatment type** (`beginTreatment`):
   - `InjectableItem` → systemic self-injection, no target, no wheel.
   - Global action (`TreatmentAction.isGlobal()`: `RESTORE_BLOOD`, `REDUCE_PAIN`, `BOOST_CLOTTING`) →
     `sendAction(itemId, null, targetId)` immediately.
   - Localized action + other target → the client doesn't know the target's injuries, so it sends
     `TreatmentTargetRequestPacket(targetId, itemId)` and waits.
4. **Server examine** — `server/MedicalActionService.requestTargetInfo` (line 143) validates hands /
   item / reach (6 blocks, `REACH_SQR` line 43), **recomputes the target profile**, snapshots per-limb
   `LimbSummary`s and replies with `TreatmentTargetInfoPacket`.
5. **Client reply** — `TreatmentInteractions.onTargetInfo` (line 129) → `proceedLocalized` (line 145):
   no damaged limb → "No injuries to treat"; exactly one → auto-`sendAction`; several → opens
   `LimbWheelScreen(targetId, itemId, limbs)`.
6. **Wheel click** — `LimbWheelScreen.mouseClicked` (line 255) → `TreatmentInteractions.sendAction`
   (locks the hotbar slot client-side) → `MedicalActionPacket(itemId, limb, targetId)`.
7. **Server start** — `MedicalActionService.start` (line 56): validates creative-immunity, actor hands
   (`MedicalState.isHandsDisabled`), no treatment already running, item present (`slotToLock`), and the
   target (alive, within 6 blocks, has the `MEDICAL` capability). Tourniquets apply **instantly**
   (`applyTourniquet`, line 260); everything else records a timed active treatment on the **actor's**
   profile (`MedicalProfile.setActiveTreatment`, `core/MedicalProfile.java:568`) and sends the actor an
   `ActiveTreatmentPacket` (drives `ActionProgressOverlay` + the client item lock in
   `TreatmentInteractions.onClientTick`).
8. **Completion** — `MedicalEngine.onServerTick` → `tickPlayer` (`server/MedicalEngine.java:107`) calls
   `MedicalActionService.tick` (line 175) once elapsed ≥ totalTicks: re-validates the item is still in
   the recorded slot and the target still resolvable/within reach, then
   `TreatmentService.applyTargeted(targetData, gameTime, treatment, activeLimb)`
   (`server/TreatmentService.java:67`) mutates the **target's** profile. On success the item shrinks and
   the target is re-synced (`syncTarget`, line 342 → `MedicalEngine.resync` for players). The active
   treatment always clears and the actor gets `ActiveTreatmentPacket.inactive()`.

### Audited and found CORRECT — don't burn time re-checking

- All packet `encode`/`decode` pairs are field-order symmetric (`MedicalActionPacket`,
  `TreatmentTargetRequestPacket`, `TreatmentTargetInfoPacket`, `ActiveTreatmentPacket`,
  `MedicalSyncPacket.writeLimb/readLimb`).
- Every handler is registered `consumerMainThread` (`network/MedicalNetworking.register`) — no
  network-thread `mc.setScreen` hazard.
- `MedicalEngine.isActive` includes `hasActiveTreatment()`, so a perfectly healthy medic's treatment
  does tick and complete.
- `setActiveTreatment(action, limb, itemId, totalTicks, startGameTime, targetId, slot)` argument order
  matches the call in `start` (recordedTarget/slot are not swapped).
- Engine is wired: `event/MedicalEventHandler.java:105` → `MedicalEngine.onServerTick`; default cadence
  `updateIntervalTicks = 10` (config line 139), so completion lands ≤ 0.5 s late — fine.
- `Minecraft.startUseItem` fires the Forge input event **before** the vanilla entity-interact branch,
  so cancelling in `onUseInput` correctly suppresses both the vanilla use and entity interaction.
- Client/server entity ids and `level.getGameTime()` are vanilla-synced; the progress bar math in
  `ActionProgressOverlay.drawBar` is sound.

---

## 2. Defects found (ranked)

### F1 — Treatments on another player frequently complete as a SILENT NO-OP (high, most likely "the bug")

**Where:** `core/limb/LimbStatus` + `server/TreatmentService.applyTargeted` + `MedicalActionService.tick` (line 214).

The wheel lists a limb whenever it is *generically* damaged (`LimbStatus.isDamaged`: health < 99.9%,
bleeding, pain, or fracture) — deliberately independent of what the held item can treat. But on
completion, `TreatmentService.pickTarget` (line 223) only matches **`Trauma` objects** that
`respondsTo(action)` and whose category the treatment `appliesTo`. Two common cases produce no match:

- The limb's damage lives only in the regenerating **`minorDamage` pool** (no `Trauma` at all) — its
  health% is reduced, the wheel shows it, but `pickTarget` finds nothing.
- The item doesn't address that limb's wound type (splint on a bleed-only limb, bandage on a fracture).

Result: the medic channels the **full duration**, then `applied == false` → item not consumed, no chat
message, no visual change. From the player's perspective, "healing other players via the GUI does not
work". The same hole exists for self-treatment, but it bites hardest in the other-player flow because
the medic picked the limb off the wheel expecting an effect.

**Aggravator:** `limbHint` is a *bias*, not a filter (`LIMB_HINT_BONUS`, line 24): if the clicked limb
has no matching trauma but another limb does, the treatment silently lands on the **other** limb.

**Fix direction:**
1. In `MedicalActionService.tick`, when `applied == false`, send the actor a status-bar message
   (add a lang key like `gui.wfmedical.treat.no_effect`) so it never fails silently.
2. Better: filter at wheel-build time. `requestTargetInfo` already knows the item; have the server
   include per-limb "treatable by this item" info in `TreatmentTargetInfoPacket` (or compute a
   `Treatment`-aware variant of `LimbStatus.isDamaged`) so the wheel only offers limbs the held item can
   actually affect, and `proceedLocalized`'s "No injuries to treat" message covers the rest.
3. Decide whether `limbHint` should be a hard filter for wheel-originated requests (recommended:
   respect the explicit click; return false rather than cross-heal another limb).

> ✅ **FIXED — all three parts.** (1) `tick` now messages `treat.applied` / `treat.no_effect`.
> (2) `TreatmentService.canTreatLimb`/`treatableMask` compute per-limb treatability server-side
> (trauma match for wound-care actions, cached pain for `NUMB_LIMB`, bare arm/leg for
> `APPLY_TOURNIQUET`); the mask rides in `TreatmentTargetInfoPacket` and
> `TreatmentInteractions.proceedLocalized` intersects it with `isDamaged` before offering slices —
> untreatable limbs get `treat.no_treatable`. (3) `limbHint` is now a hard filter in
> `TreatmentService.pickTarget` (`LIMB_HINT_BONUS` removed); an explicit limb click never cross-heals.

### F2 — Examining a patient EATS their pending physiology update (medium, real desync)

**Where:** `MedicalActionService.requestTargetInfo`, line 165: `profile.recompute(...)`.

`recompute` clears the profile's dirty flag (`MedicalProfile.java:545`). The engine's next pass keys
`MedicalEffects.apply` + `bumpRevision`/sync off `wasDirty = profile.isDirty()`
(`MedicalEngine.java:132–195`). If a medic right-clicks a patient between a dirtying event (damage,
bleed advance) and the patient's next engine pass, that pass now sees a clean profile and **skips both
the vanilla-body reconciliation and the client sync** for that change. The patient's HP/HUD stay stale
until something else dirties them.

**Fix direction:** in `requestTargetInfo`, don't recompute blindly. If the profile is dirty and the
target is a `ServerPlayer`, call `MedicalEngine.resync(sp)` (recompute + apply + full sync) before
snapshotting; for a non-player body, recompute and also re-stamp via `MedicalEffects.applyToBody`
(mirror `syncTarget`, line 342). Otherwise just snapshot `profile.cached()`.

> ✅ **FIXED** exactly as described: `requestTargetInfo` now routes a dirty profile through the
> existing `syncTarget` helper (full `MedicalEngine.resync` for players, recompute + body re-stamp for
> persistent bodies) and otherwise snapshots the clean cache; the dirty flag is never eaten.

### F3 — A tourniquet applied to another player can never be removed by the medic (medium, functional gap)

**Where:** `MedicalActionService.removeTourniquet` (line 294) is explicitly self-only;
`RemoveTourniquetPacket` carries no target id; `LimbWheelScreen` has no remove slice;
`MedInteractionScreen`'s red remove button (line 303) acts on the local player.

A medic tourniquets a downed teammate (works — `applyTourniquet` handles other targets), but the only
removal path is the wearer's own self-GUI, which an unconscious patient cannot use. The tourniquet then
persists indefinitely on an unconscious patient.

**Fix direction:** add `targetEntityId` to `RemoveTourniquetPacket` (self default `-1`), validate reach
+ capability server-side exactly like `start`, and surface a remove affordance in the other-player flow
(e.g. right-clicking a tourniquetted limb's wheel slice with a free hand, or a red slice when the
target limb wears one — mirror the `MedInteractionScreen` pattern via `ClientTourniquetTracker.has`,
which already tracks remote players' masks).

> ✅ **FIXED.** `RemoveTourniquetPacket` carries `targetEntityId` (`-1` = self, old behavior kept via a
> convenience constructor); `MedicalActionService.removeTourniquet(actor, limb, targetId)` validates
> actor hands + reach for non-self targets, syncs the target via `syncTarget`, and the recovery roll
> pays the REMOVER. GUI affordance: while holding a tourniquet item, a limb already wearing one shows
> in the wheel as a red **remove** slice (mirrors the interaction sheet's red button) instead of a
> second apply.

### F4 — `MedInteractionScreen` is hard-wired to self (relevant only if the sheet is *meant* to examine others)

`MedInteractionScreen` reads exclusively from `ClientMedicalCache` (the local player's synced snapshot)
and `MedicalUIParts.requestAction` (line 98) always sends the 2-arg `MedicalActionPacket` → `targetId = -1`.
There is no plumbing to open the sheet *for* another player. If the desired UX is "open the interaction
sheet while aiming at a teammate to examine/treat them", it requires: an entity-id parameter on
`open()`, sourcing limb data from a `TreatmentTargetRequestPacket` round-trip instead of
`ClientMedicalCache`, and threading `targetId` through `requestAction`/`requestRemoveTourniquet`.
Check with the project owner before building this — the wheel may be the only intended other-player GUI.

> ✅ **BUILT (2026-07-23).** The project owner opted in. Pressing the open-sheet key (`OPEN_SHEET`, default
> `H`) while aiming at another player / downed body now opens the EXAMINATION / TREATMENT sheet bound to
> THAT target; aiming at nothing opens the local player's own sheet as before. Implementation:
> - New packet pair `TargetSheetRequestPacket` (id 14, C2S) / `TargetSheetInfoPacket` (id 15, S2C):
>   `MedicalActionService.requestTargetSheet` validates hands + reach (and brings a dirty profile current via
>   `syncTarget`, so this examine never eats a pending physiology update — same guard as F2), then replies with
>   the target's FULL `MedicalSyncPacket` snapshot + worn-tourniquet mask (the sheet needs all limbs + vitals,
>   not the item-specific mask `TreatmentTargetRequestPacket` returns). Channel `PROTOCOL` bumped `"2"` → `"3"`.
> - `MedInteractionScreen` gained a static `targetId` (`-1` = self) + `targetSnapshot`; all readouts route
>   through `sheetSnapshot()`/`sheetLimb()`/`sheetStats()`/`sheetState()` (self cache vs target snapshot), the
>   body tiles use a new `MedicalUIParts.addLimbTile(..., Function<LimbType,LimbSummary>)` source overload, and
>   every treatment / tourniquet button threads `targetId` (`requestAction(stack, limb, targetId)`,
>   `requestRemoveTourniquet(limb, targetId)`). The STATUS header doubles as an amber patient-name banner.
> - Live refresh: while the sheet is bound to a teammate the client re-requests their snapshot every 10 ticks
>   (`MedicalClientEvents.pollTargetSheet`), which also unbinds on close. A `pendingOpenTarget` gate makes only
>   an EXPLICIT open request pop the screen, so an in-flight poll reply can never re-open a sheet the medic just
>   closed.
>
> The medic's own timed-treatment bookkeeping (progress bar, item lock) is unchanged — it still keys off
> `ClientMedicalCache.hasActiveTreatment()` (the actor's profile), which is correct when treating others.

### F5 — Minor issues (fix opportunistically)

- **Dead code:** `SetTargetLimbPacket` stores `preferredLimb` (`MedicalProfile.java:629`) but nothing
  reads `getPreferredLimb()` — the "server biases treatments" comment in
  `MedicalUIParts.selectLimb` (line 88) is a lie. Either use it as the default limb hint in
  `start` when `limb == null`, or delete the packet + field.
- **Reach asymmetry:** client entity pick (`mc.hitResult`) reaches ~3 blocks; server allows 6
  (`REACH_SQR`). Harmless, but a custom ray of 4–5 blocks in `pickTargetId` would make targeting downed
  bodies less finicky.
- **Dropped reply:** `onTargetInfo` returns if *any* screen is open (line 132) — a chest opened during
  the round trip silently swallows the wheel. Consider queuing or just letting it drop, but know it exists.
- **No "already being treated" lock on the target:** two medics can channel on the same patient
  concurrently; both consume items, later completion may no-op (ties into F1's silent-failure UX).
- **`LimbWheelScreen.mouseClicked`** acts on any mouse button; harmless but sloppy.
- **No success feedback** on completion for the medic beyond the bar vanishing (chat/status message
  would pair well with the F1 failure message).

> ✅ **Mostly FIXED:** `SetTargetLimbPacket` + `preferredLimb` deleted (packet id 3 retired, channel
> protocol bumped to "2"); `pickTargetId` falls back to a 4.5-block `ProjectileUtil` ray (inherits the
> envelope hit boxes, so downed bodies are easier to aim at); `start` rejects a target already being
> channelled on by another medic (`treat.busy`); the wheel acts on left-click only (other buttons
> dismiss); success feedback is the `treat.applied` message. **Left as-is:** the dropped
> `onTargetInfo` reply when a screen is open mid-round-trip (harmless — the medic just clicks again).

---

## 3. Verification playbook

No automated tests cover this flow (`gametest` package only covers hit-location/rig). Verify in a
2-client dev run:

```bash
./gradlew runClient   # start twice, or use runClient + a second IDE run config; join a LAN world
```

Useful ops commands (`server/command/WFMedicalCommands.java`, root literal `wfmedical`):

- `/wfmedical trauma add <player> <limb> <type> <severity>` — wound the patient precisely.
- `/wfmedical query <player>` — dump the authoritative profile (includes wakeup score).
- `/wfmedical blood set|add`, `/wfmedical suppression set`, `/wfmedical heal|reset|revive`.

Scenarios to run after any fix:

1. Wound patient's LEFT_ARM (cut) + RIGHT_LEG (fracture); medic right-clicks with a bandage → wheel
   must show both limbs; clicking the fractured leg must **not** silently no-op (F1) and must not heal
   the arm instead (F1 aggravator).
2. Give the patient only minor damage (punches) → wheel/auto-select path must not offer an untreatable
   limb or must message the medic (F1).
3. Damage the patient, then medic right-clicks (examine) repeatedly before the engine pass; patient's
   own HUD must still reflect the damage (F2).
4. Tourniquet a downed patient's arm, then remove it as the medic (F3, after implementing).
5. Mid-channel: swap hotbar slot server-authoritatively (`item_changed` cancel), walk >6 blocks away
   (`target_gone` cancel), patient logs out — bar must clear, no item consumed.
6. Regression: self-heal via wheel, via `MedInteractionScreen` treatment grid, tourniquet apply/remove
   on self, and injectables (self-only) must all still work.

## 4. Key file index

| Concern | File |
|---|---|
| Right-click flow, item lock, target pick | `src/main/java/com/warfactory/medical/client/TreatmentInteractions.java` |
| Limb wheel GUI | `src/main/java/com/warfactory/medical/client/screen/LimbWheelScreen.java` |
| Self interaction sheet | `src/main/java/com/warfactory/medical/client/screen/MedInteractionScreen.java` + `MedicalUIParts.java` |
| Start/tick/cancel, tourniquets, examine reply | `src/main/java/com/warfactory/medical/server/MedicalActionService.java` |
| Treatment application + trauma picking | `src/main/java/com/warfactory/medical/server/TreatmentService.java` |
| Engine cadence, resync | `src/main/java/com/warfactory/medical/server/MedicalEngine.java` |
| Packets + registration | `src/main/java/com/warfactory/medical/network/` (`MedicalNetworking.register` has the id table) |
| Damaged-limb predicate (wheel contents) | `src/main/java/com/warfactory/medical/core/limb/LimbStatus.java` |
| Active-treatment bookkeeping | `src/main/java/com/warfactory/medical/core/MedicalProfile.java` (lines 553–623) |
| Progress bar | `src/main/java/com/warfactory/medical/client/overlay/ActionProgressOverlay.java` |
