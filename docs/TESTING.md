# Testing WF Medical

Two suites, split by what they need rather than by what they cover.

| | `./gradlew test` | `./gradlew runGameTestServer` |
|---|---|---|
| What it is | JUnit 5 on a bootstrapped Minecraft | Vanilla GameTest in a real world |
| Needs | registries only | a server, entities, mixins, TACZ |
| Runtime | ~8s | ~30s (most of it server boot) |
| Lives in | `src/test/java` | `src/main/java/com/warfactory/medical/gametest` |

`./gradlew checkAll` runs both. `check`/`build` run only the unit tests, so the inner loop stays fast.

## Why the split matters

`neoForge.unitTest` in `build.gradle` puts `src/test/java` on the full Minecraft classpath and runs
FML's JUnit bootstrap first, so `Vec3`, `AABB` and the registries all exist without a server. That
covers everything that is a pure function — the OBB slab test, the rig spec, the TACZ hit cache.

Anything that needs a posed `LivingEntity`, an applied mixin, or the damage pipeline cannot be a unit
test and belongs in a gametest.

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

**The rig is built in the victim's local frame.** The OBBs are identical at every yaw; `HitGeometry`
rotates the incoming ray into that frame instead. So asserting "the boxes move when the player turns"
fails, and any yaw test that asserts on box geometry is testing the wrong thing. Assert on
classification of a world-space ray instead.

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
