# Combat stimulant knockout investigation — 2026-11-07

The reported bug is **not yet reproduced**: the user took their first combat stimulant with a brief
click/release and then saw the genuine unconscious/give-up state, with movement disabled. This was
not just the drug's visual fade. Do not describe the held-use fix below as the confirmed cause.

## What was verified

- The installed definitions specify load **1.4**, overdose threshold **1.6**, stimulant strength **0.97**,
  clotting boost **1.0**, and a **3600-tick** buff. The file predates the current session.
- The running Prism client's open medical jar had byte-identical `Substances`, `SubstanceService`,
  `SubstanceDefinitions`, and `Physiology` classes to the source baseline (`1b9dda5`).
- The latest save and its backup contain a post-respawn sober/healthy profile. They do not preserve
  the earlier episode's transient overdose/asphyxia flags. Logs show recent deaths while fighting
  `Hostile_8` and `Hostile_4`; that alone does not identify the knockout's cause.
- In an isolated native client using copies of the user's `wfmedical-common.toml` and definitions,
  a brief right-click consumed exactly one syringe and left a healthy character at **drugLoad 1.40**,
  **HEALTHY**, **isDowned=false**, full blood, and **movement x1.29**.
- A deliberate second click caused the expected **UNCONSCIOUS / cause=overdose** at about 2.71 load.
  This preserves the intended risk from repeated doses.
- No filming save or live player's medical state was modified. The native test used a copied generic
  world under ignored `build/stim-smoke`, on Xvfb `:199`. The test client and Xvfb were closed afterward.

## Related bug fixed

Vanilla calls `startUseItem()` repeatedly while the use key remains held. `TreatmentInteractions`
previously started another cast as soon as the current one ended, allowing a held mouse button to
consume more syringes and overdose the player. The client now requires a release before another
medical-use attempt. Pressing during a busy treatment cannot queue another dose for later. This
works with either mouse or keyboard bindings and resets on disconnect.

The native fixed client was held for eight seconds after resetting medical state: only one dose
landed, with load 1.40 and HEALTHY state. Three JUnit checks cover held-use repetition, release/repress,
and presses while busy. A new GameTest casts the actual stimulant item, checks that only one syringe
is consumed, later completion ticks do not repeat it, and a sober player stays conscious.

## Diagnostics for the original report

The next occurrence can be diagnosed from `minecraft/logs/latest.log` after installing this build and
restarting normally:

- `[medical-dose]`: actor, target, actual item, load before/after, active overdose threshold, state
  before/after, blood, overdose/asphyxia flags, and the unconscious latch.
- `[medical-action]`: cancelled treatment, its item, reason (including incoming damage), and elapsed
  cast time. An interrupted cast is not an administered dose.
- `[balance] ... transition ... UNCONSCIOUS`: emitted for knockouts even when full balance telemetry
  is off, including immediate damage resyncs; records blood, bleeding, drug load, stimulant, pain,
  derived health, overdose/asphyxia, latch and forced state.

`/wfmedical query @s` also reports the live cause and drug load while downed. Preserve that output and
the dose/transition log lines before giving up or respawning. Do not make stimulants override blood
loss or destroyed vital parts just to hide the symptom. Target names in the dose log also establish
whether a syringe aimed at another player/bot actually dosed that target instead of the user.

## Validation and coordination

All **435 JUnit tests pass**; all **114 GameTests pass**; `jar` builds successfully. An initial full
GameTest run had an unrelated head-stagger timing failure; the subsequent run with the completed
diagnostics passed all tests. No hit-reaction tuning was changed.

Build command (local WFLib 1.8.39 must win over the remote snapshot):

```sh
./gradlew test jar runGameTestServer -Pwflib_version=1.8.39 \
  -I /tmp/claude-1000/-home-overlord/4c762bd2-ec75-420e-ac51-3943b1e92ab3/scratchpad/local-first.gradle
```

Worktree: `/home/overlord/Codebases/combat-stimulant-fix`.
Branch: `fix/combat-stimulant-first-dose`, based on the other agent's current medical commit `1b9dda5`.
Only relevant classes are patched into the installed jar, with a backup and install receipt in the
archive evidence folder; merge/cherry-pick these commits before a future rebuild replaces the patch.

The user requested silent testing. The isolated OpenAL stream was muted without muting their own
game. `build/stim-smoke/options.txt` now has `soundCategory_master:0.0` for future launches. All future
isolated clients for this task should start muted.

Evidence/logs: `/run/media/overlord/External/The Beginning/Rommie Gear Video Folder/Warforge Archive/Notes/stimulant-investigation-20261107`.
