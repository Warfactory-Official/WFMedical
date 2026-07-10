# WFMedical – Developer & Designer Reference

WFMedical is a Forge 1.20.1 mod that replaces vanilla health with a per-body-part medical trauma system. Incoming damage is classified by type and hit location, translated into persistent wound objects on one of six body parts, and periodically recalculated into derived stats that vanilla health and movement attributes track. Players can self-treat or be treated by others using medical items; the system is tunable via a COMMON config and a data-driven TOML definitions file.

## High-level state model

```
HEALTHY → CRITICAL → UNCONSCIOUS → DEAD
```

| State | Trigger | Effect |
|---|---|---|
| `HEALTHY` | Default | No penalties |
| `CRITICAL` | Unconsciousness score ≥ 0.5, or effective health ≤ `bloodCriticalFraction` × max | HUD warning only |
| `UNCONSCIOUS` | KO score ≥ 1.0, overdose, or asphyxia pass-out | Movement locked, 1-HP pin, actions disabled |
| `DEAD` | Blood loss past `bloodDeathLossFraction`, major-trauma instant-kill, lethal overdose drain, or asphyxia deadline | health set to 0, vanilla death pipeline |

Ordinal order `HEALTHY < CRITICAL < UNCONSCIOUS < DEAD` is load-bearing – it governs severity comparisons for admin-forced state overrides and is the NBT serialization contract.

There is no `lethalBlowsEnabled` toggle. Instant death from a single hit is **intrinsic**: a hit the medical armor did not fully block whose raw damage reaches `majorTraumaFraction(category) × maxHealthPoints` kills on impact, regardless of current health (see [mechanics](mechanics.md#death-vs-unconsciousness)).

## The six body parts

| `LimbType` | Hit weight | Vital | Leg | Arm |
|---|---|---|---|---|
| `HEAD` | 0.10 | yes | no | no |
| `TORSO` | 0.40 | yes | no | no |
| `LEFT_ARM` | 0.12 | no | no | yes |
| `RIGHT_ARM` | 0.12 | no | no | yes |
| `LEFT_LEG` | 0.13 | no | yes | no |
| `RIGHT_LEG` | 0.13 | no | yes | no |

Hit weights are base probabilities used by the weighted fallback sampler; the geometric OBB-based hit location system takes precedence when enabled.

## Table of contents

| Doc | Contents |
|---|---|
| [architecture.md](architecture.md) | Package layout, end-to-end hit trace, server-authoritative design, capability/dirty/revision scheme, client/server split |
| [mechanics.md](mechanics.md) | Gameplay systems: trauma, pain, blood loss, fractures, asphyxia, drugs, death vs unconsciousness, persistent bodies |
| [config.md](config.md) | Reference table for every `MedicalConfig` option; `wfmedical_definitions.toml` schema |
| [extending.md](extending.md) | Adding a new trauma type, substance, treatment, and item; enum-dispatch sites that must be updated together |
