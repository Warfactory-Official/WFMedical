# Architecture

## Systems

`com.warfactory.medical.<sys>`; entry `<sys>/<Sys>System implements MedicalSystem` (wflib `WFSystem`). On/off: `config/wfmedical-systems.toml` `[systems]`.

| system | mandatory | `requires()` | off => |
|---|---|---|---|
| `body` | yes | `wflib:artifact`, `wflib:sway` (sets `PainSway.SOURCE`) | n/a |
| `damage` | no | `body`, `wflib:armor` | no wounds from hits; `PLAYER_RESOLUTION` unset => wflib resolves player armour |
| `treatment` | no | `body` | no medical items, sheet, radial, tourniquets; wounds heal by time only |
| `substance` | no | `body`, `treatment` | no syringes; drug state idle |
| `stress` | no | `body` | `Stress` stays 0 |
| `fx` | no, client-only | `body`, `wflib:xray` | no post-process |
| `persistence` | no | `body`, `damage` | logout bodies carry no profile; no-op without `openpersistence` |

- Needed-but-off => turned on + loading warning (`WFSystems`); file not rewritten.
- Client/server effective sets compared in the configuration phase (wflib `SystemsNetwork`, all mods) => mismatch disconnects.
- Guarded, not required: `wflib:anomaly` (psy listener, K-c), `wflib:smoking` (cigarette listener), `wfmedical:substance` (fx opioid haze).
- Other systems through their `api` only: fx -> `substance.api.Opioids`, `treatment.api.client.ActiveTreatment`.
- Gametest runs (`neoforge.enabledGameTestNamespaces`): `WfLibRounds.request` requires `wflib:smoking` + `wflib:anomaly` before `define`.

## Startup

| step | where |
|---|---|
| `WFSystems.define(MOD_ID, …, SYSTEMS)` -> `register(modBus, container)` per on system, list order | `WFMedical` ctor |
| `WFSystems.startClient` -> `registerClient(modBus)` | `WFMedicalClient` (`dist = CLIENT`) |
| client bodies: `BodyClient` / `TreatmentClient` / `StressClient.register` | called from `registerClient` only |

- `SYSTEMS` order body -> damage -> persistence = registration order of same-priority `LivingIncomingDamageEvent` listeners.
- `<Sys>System` names client classes only in call position (`XClient.register`, S2C handler lambdas in `payloads`): verifier loads lambda arg types => `LayeredDraw$Layer` on a dedicated server.
- No `@EventBusSubscriber` in systems; exceptions `body.DataGenerators`, `*.gametest.*`.

## Extension points (`body.BodyHooks`)

| member | kind | filled by |
|---|---|---|
| `ACTIVE_TREATMENT` | `Hook<ActiveTreatment>` (tick, cancel) | treatment |
| `CLIENT` | `Hook<ClientView>` (local stats, downed by id) for `MedicalState` | `BodyClient` |
| `PERSISTENT_BODY` | `Hook<Predicate<Entity>>` | persistence |
| `JOIN` | `OrderedRegistry<Consumer<ServerPlayer>>` | treatment (tourniquet broadcast) |
| `START_TRACKING` | `OrderedRegistry<BiConsumer<viewer, target>>` | treatment (tourniquets, pose) |
| `SUBCOMMANDS` | `OrderedRegistry<Consumer<root>>` | body 0, stress -100, substance -200, treatment -300, damage -400 |

`body` names no other WFMedical system; `body.api` = `MedicalState`, `MedicalHealing` (dependent surface).

## Registries

| registry | key | filled in |
|---|---|---|
| `body.trauma.TraumaTypes.TYPES` | `wfmedical:<id>`; NBT `Type` keeps the TOML `id` string | `BodySystem.register` |
| `treatment.Treatments.TREATMENTS` | item RL | `TreatmentSystem.register` |
| `substance.Substances.SUBSTANCES` | item RL | `SubstanceSystem.register` |

- Source `config/wfmedical_definitions.toml` (bundled copy written when absent); `MedicalDefinitions.load` per table.
- Per table: unreadable / parse error / bad RL / duplicate key => that table's hard-coded defaults, ERROR logged; empty => defaults.
- Freeze `FMLLoadCompleteEvent` (wflib `freezeAll`); no runtime reload.
- Pure code takes `TraumaTypes.Lookup`: `LIVE` in game, `Table` in tests and parsing.
- Syringe items resolve their `Substance` per use (`InjectableItem` holds the RL); treatment reaches it as `MedicalItem.dose()` (`Substance implements MedicalItem.Dose`).
- Syringes join treatment's tab via `BuildCreativeModeTabContentsEvent` on `ModCreativeTab.MEDICAL`.

## Config

| file | spec | reads |
|---|---|---|
| `wfmedical-common.toml` | `MedicalConfig.SPEC` | `MedicalConfig.*`; damage keys via `damage.DamageConfig` |
| `wfmedical-client.toml` | `MedicalClientConfig.SPEC` | `MedicalClientConfig.*` |
| `wfmedical-systems.toml` | STARTUP, kernel | `WFSystems` |

- Accessors read an immutable `Values` snapshot built on `ModConfigEvent.Loading`/`Reloading`; `stressParams()`, `toPhysiologyParams()`, self-only map built once per snapshot.
- Programmatic `ConfigValue.set` => `MedicalConfig.rebuild()` (gametests, `TestConfig`).
- One spec for all systems (R7 split not adopted).

## Hit path

| event | priority | handler | system |
|---|---|---|---|
| `LivingIncomingDamageEvent` | HIGH | `onDrownDamage`, `onDownedSuffocation` | body |
| `LivingIncomingDamageEvent` | HIGH | `onLivingAttackGapReject` (AHF `shouldRejectGap`) | damage |
| `LivingIncomingDamageEvent` | NORMAL | `onLivingHurt` | damage |
| `LivingIncomingDamageEvent` | NORMAL | `onPersistentBodyHurt` | persistence |
| `ServerTickEvent.Post` | NORMAL | `MedicalEngine.onServerTick` (every `updateIntervalTicks`) | body |
| `PlayerTickEvent.Post` | NORMAL | `MedicalEngine.tickBreathing` (every tick) | body |

`onLivingHurt`: `DamageClassifier` -> AHF limbs (`Strikes.parts` first) -> `ArmorResolution.resolve` (wflib `ArmorResolution`, `ArmorOutcome`) -> instakill if `outcome != STOPPED && canInstakillOnImpact && through >= maxHealthPoints * majorTraumaFraction` -> `TraumaGenerator` / `TraumaEscalation` per limb, energy chained by `penetrationEnergyFalloff`. Hit not taken (creative, bypass, no data) => wflib `ArmorResolution.applyToEvent`.

- `ArmorOutcome.STOPPED` => vanilla damage `min(15%, 1)`; else 0.

## Data

- Attachment `wfmedical:medical` (`MedicalAttachments`): players + `PERSISTENT_BODY` entities; `IMedicalData` = `MedicalProfile` + revision.
- Non-death clone copies; death respawn starts fresh.
- Server authoritative; client holds last snapshot (`ClientMedicalCache`, `ClientTreatmentCache`).

## Payloads

| registrar | payloads |
|---|---|
| `body-1` | `MedicalSyncPacket`, `MedicalDeltaPacket`, `DownedStatePacket` (S2C); `GiveUpPacket` (C2S) |
| `treatment-1` | `ActiveTreatmentPacket`, `TreatmentPosePacket`, `TourniquetStatePacket`, `TreatmentTargetInfoPacket`, `TargetSheetInfoPacket` (S2C); `MedicalActionPacket`, `RemoveTourniquetPacket`, `ResuscitatePacket`, `TreatmentTargetRequestPacket`, `CancelTreatmentPacket`, `TargetSheetRequestPacket` (C2S) |

Registered in `<Sys>System.payloads`; S2C handlers = client classes; packets hold no client code.

Sync: `BodyNetwork.syncTo` = delta vs last sent, full every `syncFullResyncIntervalTicks`.

## Mixins (`wfmedical.mixins.json`, root `com.warfactory.medical.mixin`)

| mixin | side | system |
|---|---|---|
| `body.LivingEntityMixin` | both | body |
| `body.CameraMixin`, `body.EntityDownedLookMixin`, `body.ItemInHandRendererMixin` | client | body |
| `fx.PostChainAccessor` | client | fx (accessor; no K-b gate) |

## ArchUnit

Plain rules, no store.

| rule |
|---|
| `WflibApiBoundaryTest`: wflib `..api..` only |
| `SystemBoundaryTest`: system -> own + `requires()` + other systems' `api` (gametest, datagen exempt) |
| `SystemBoundaryTest`: common -> `..client..` forbidden (`*System`, `*Client`, mixin, fx exempt) |
