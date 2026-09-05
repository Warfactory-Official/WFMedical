# Testing WF Medical

Two suites, split by what they need rather than by what they cover.

| | `./gradlew test` | `./gradlew runGameTestServer` |
|---|---|---|
| What it is | JUnit 5 on a bootstrapped Minecraft | Vanilla GameTest in a real world |
| Needs | registries and the config spec | a server, entities, mixins, TACZ |
| Count | 391 | 130 |
| Runtime | ~10s | ~30s (most of it server boot) |
| Lives in | `src/test/java` | `src/main/java/com/warfactory/medical/gametest` |

`./gradlew checkAll` runs both. `check`/`build` run only the unit tests, so the inner loop stays fast.

## Why the split matters

`neoForge.unitTest` in `build.gradle` puts `src/test/java` on the full Minecraft classpath and runs
FML's JUnit bootstrap first, so `Vec3`, `AABB` and the registries all exist without a server. That
covers everything that is a pure function — physiology, the limb/trauma model and its NBT, the wire
protocol, treatment resolution, the wound table, the definitions parser.

`support/TestConfig` extends that reach considerably: it binds `MedicalConfig.SPEC` to an in-memory
config corrected against the spec defaults. Without it every `MedicalConfig.x()` throws
`IllegalStateException: Config not loaded`, which put most of the mod out of a unit test's reach —
`TraumaGenerator` reads `fallFractureMinBlocks`, `TreatmentService` reads
`clottingAgentDurationTicks`, `ClientMedicalCache` reads `logMedicalSync`. `TestConfig.set(path,
value)` overrides one key for the length of a test, which is what lets a threshold test prove the
threshold is actually the configured one rather than a hardcoded constant.

Anything that needs a posed `LivingEntity`, an applied mixin, an attribute map, a damage-type *tag*,
or the damage pipeline cannot be a unit test and belongs in a gametest. `gametest/TestBodies` holds
the shared fixtures for that suite.

### What each gametest holder covers

| Holder | Covers |
|---|---|
| `HitLocationGameTest`, `LimbRigGameTest`, `RigYawGameTest` | geometric limb classification, at every yaw |
| `HitLocationFallbackGameTest` | the weighted sampler used when a hit has no traceable direction |
| `ArmorEvaluationGameTest` | BLOCKED/PARTIAL/FULL, category effectiveness, per-slot durability |
| `DamageClassifierGameTest` | the tag-driven half of classification, over the real registry |
| `RigCacheGameTest` | per-tick rig memoisation, pose-hint validation, the hit envelope |
| `TraumaPipelineGameTest` | a real TACZ bullet, end to end, to a wound on a named limb |
| `DamagePipelineVariantsGameTest` | falls, fire, blasts, arrows, melee, suffocation, regen clamping |
| `SubstanceServiceGameTest` | analgesia, stimulants, overdose, antidote reversal |
| `MedicalActionServiceGameTest` | the item-use flow: cast, complete, cancel, item consumption |
| `CommandGameTest` | the whole `/wfmedical` tree parses, and the state-changing branches work |
| `MedicalStateApiGameTest` | the `api.MedicalState` surface other mods read |
| `MedicalAttachmentGameTest` | who carries medical state, and respawn copying |
| `TaczMixinContractGameTest` | that the four TACZ mixins actually attached |

### What is deliberately not covered

Everything under `client/` — the HUD overlays, the limb wheel and interaction screens, the downed-body
and tourniquet renderers, the post-processing effects. None of it is reachable from a headless
gametest server, and the pieces that could be extracted are thin wrappers over `ClientMedicalCache`,
which is covered. Changes there still need `./gradlew runClient`, or `runAll` for the two-client
sync check below.

The client-side mixins (`CameraMixin`, `HumanoidModelMixin`, `ItemInHandRendererMixin`,
`EntityDownedLookMixin`) are in the same position. The server-side ones need no contract test: unlike
the TACZ config, `wfmedical.mixins.json` is `required: true` with `defaultRequire: 1`, so a failed
injector is a hard crash at load rather than a silent no-op.

## Traps that have already bitten

**A green suite can mean nothing.** `HarnessSmokeTest.minecraftIsBootstrapped` asserts the entity
registry is *populated*, not merely non-null: a half-initialised Minecraft makes every
registry-dependent assertion pass vacuously instead of failing. Several tests here carry an explicit
"this cannot pass vacuously" guard for the same reason — `ObbRayTest.aRotatedBoxIsActuallyRotated`,
`RigYawGameTest.classificationIsActuallyYawSensitive`, `TaczMixinContractGameTest.taczIsActuallyLoadedForTheseTests`.
Keep that habit: a property test over a transformation is worthless if the transformation is the
identity.

**Gametests need `data/wfmedical/structure/` — singular.** 1.21 renamed the folder
(`StructureTemplateManager.STRUCTURE_RESOURCE_DIRECTORY_NAME = "structure"`). With the 1.20.1 plural
the server dies in the tick loop with `Missing test structure: wfmedical:empty` before running a
single test, which looks like a broken harness rather than a wrong path. This is what made the
gametests unrunnable for the whole port.

**TACZ mixins fail silently by design.** `wfmedical.tacz.mixins.json` sets `defaultRequire: 0`,
because TACZ is optional and a required injector would hard-crash a server without it. So an upstream
rename makes every injector match nothing while the mod still loads perfectly, and javac cannot see it
— mixin targets are strings resolved at class load. `TaczMixinContractGameTest` is the only thing
standing between that and a dead integration. It force-loads the TACZ classes (an ordinary gametest
run never fires a gun, so they are otherwise never loaded) and asserts both that the targeted method
still exists and that the handler was merged in. Mixin uniquifies merged handlers, so
`wfmedical$captureHitPos` arrives as `handler$zbb000$wfmedical$captureHitPos` — match on the suffix.

**A test-constructed player cannot be hurt, three times over.** All three gates sit *before*
`LivingIncomingDamageEvent` is fired, so a damage test that misses one passes while never invoking a
line of WFMedical. `TestBodies` handles all three in one place:

1. `FakePlayer.isInvulnerableTo` returns `true` unconditionally. Overridden back in `TestBodies.Victim`.
2. A fresh `ServerPlayer` starts with 60 ticks of `spawnInvulnerableTime`, which only decays in
   `ServerPlayer.tick` — which `FakePlayer` also no-ops. It is private, hence the one entry in
   `META-INF/accesstransformer.cfg` (an AT only widens access; it changes no behaviour).
3. `FakePlayer.canHarmPlayer` returns `false` unconditionally. This one only bites *player-versus-player*
   damage, so a suite can pass every fall/fire/arrow test and still never land a melee hit.
   `TestBodies.Victim` restores the rule `ServerPlayer`/`Player` would have applied, rather than
   returning a bare `true` — the server PvP flag and the team policy are real gates in front of the mod.

The tell for all three is that `hurt()` returns `false`. **Check that return value first**: a damage
test that ignores it reports "the mod produced no wound" when the truth is that the mod never ran.

**A gametest server starts with PvP off.** `ServerPlayer.hurt` then refuses all player-dealt damage,
which looks identical to a hit-registration bug. `TestBodies.attacker` calls `setPvpAllowed(true)`.

**Watch wounds, not the health bar, for "did this hit land?"** The pipeline absorbs the vanilla damage
amount for every hit it handles, so a landed hit and a cancelled one both leave health untouched. Use
`hurt()`'s return value (cancellation) or the resulting traumas.

**Melee needs the attacker aimed at the victim.** `HitGeometry.shouldRejectGap` traces the attacker's
eye ray out to `meleeReach` and discards the hit as a whiff if it clears every limb box. Two players
constructed at the same spot both look along +Z, so the ray never crosses the victim.
`TestBodies.attacker(helper, target, distance)` positions and orients one properly.

**Don't assert a fixed wound count.** One ballistic hit legitimately produces several traumas —
penetration walks every limb the ray crossed and `TraumaGenerator` can emit more than one per limb. The
TACZ double-hurt property is *"the second hurt event adds no wounds"*, measured as a before/after
delta. Asserting "exactly one wound" tests an unrelated tuning value and reports a coalescing bug that
isn't there.

**The rig is built in the victim's local frame.** The OBBs are identical at every yaw; `HitGeometry`
rotates the incoming ray into that frame instead. So asserting "the boxes move when the player turns"
fails, and any yaw test that asserts on box geometry is testing the wrong thing. Assert on
classification of a world-space ray instead.

**A probabilistic branch needs a pinned roll or a sample, never one draw.** Armour mitigation, the
fracture roll and the weighted limb sampler are all dice. `Fixtures.alwaysRolls()`/`neverRolls()` pin a
branch as a decision; the gametests measure a rate over a fixed seed. A single random outcome asserted
once is a test that fails on someone else's machine next month.

**Adding a real player to the player list runs the full login sequence.**
`GameTestHelper.makeMockServerPlayerInLevel()` does that, and any mod with a login-time sync packet
(TACZ has one) throws in the middle of an unrelated test. `CommandGameTest` builds a
`CommandSourceStack.withEntity(victim)` instead, so `@s` resolves without going near the player list.

**Brigadier "parses" a command that cannot run.** `/wfmedical blood` consumes every character and
reports no error while stopping on an intermediate literal with no `Command` attached. A parse check
has to assert `parse.getContext().getLastChild().getCommand() != null` as well, or it passes for
half the tree.

**`/wfmedical reset` swaps the profile object.** It installs a fresh `MedicalProfile` rather than
clearing the existing one, so anything holding the old reference keeps reading pre-reset values.

**A config-threshold test must prove the threshold moved.** `TraumaGeneratorTest` asserts a short fall
cannot break a leg *and* that raising `fallFractureMinBlocks` makes a long one safe. Without the second
half the first passes just as well against a hardcoded constant.

## Running a single test

```bash
./gradlew test --tests '*ObbRayTest*'
./gradlew test --tests '*ObbRayTest.Rotation*'
./gradlew test -PtestOutput          # let System.out through (the FML bootstrap is loud)
```

GameTests have no per-test filter through Gradle; use the dev client instead:

```bash
./gradlew runClient
# then, in game:
/test run wfmedical:frontalheadateveryyaw
/test runall wfmedical
```

## Multiplayer / sync

Medical state is server-authoritative and delta-synced, so "does the victim's client agree with the
shooter's" needs two real clients. `./gradlew runAll` starts an offline dedicated server plus two
isolated clients (`Player1`, `Player2`) that auto-join it; `./gradlew stopAll` kills all three.
