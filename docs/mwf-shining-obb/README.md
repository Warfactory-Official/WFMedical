# MWF-SHINING OBB / Hitbox System — Reference & Analysis

Documentation of the Oriented-Bounding-Box (OBB) hitbox system in
[mchhui/MWF-SHINING](https://github.com/mchhui/MWF-SHINING) (Forge 1.12.2, package
`com.modularwarfare.utility.raycast`), produced to inform improvements to WFMedical's own
hitbox system (`com.warfactory.medical.core.damage.rig` / `HitGeometry`).

## Contents

| Doc | Subsystem |
|---|---|
| [01 — Geometry primitives](01-geometry-primitives.md) | `OBBModelScene`/`Bone`/`Box`, the center + scaled-axis + unit-normal OBB representation, the `Matrix4f` transform chain, hit structs, coordinate spaces & the `1/16 × 0.9375` scale. |
| [02 — Raycasting core](02-raycasting-core.md) | `DefaultRayCasting`: the per-face-plane ray-vs-OBB test, the ray-as-degenerate-OBB SAT broad phase, entity iteration, penetration budget, client-only evaluation. |
| [03 — Entity OBB management](03-entity-obb-management.md) | `OBBPlayerManager`/`EntityOBBManager`/`HEEntityOBBObject`: per-entity registry, per-tick pose sync, client copies-rendered-model vs server re-derives, no lag comp. |
| [04 — Data pipeline & integration](04-data-pipeline-and-integration.md) | `player.obb.json` format + `BlockBenchOBBInfoLoader`, and the full fire → raycast → hit → damage flow, events, config knobs. |
| [05 — Applying it to WFMedical](05-applying-to-wfmedical.md) | **The synthesis.** Side-by-side comparison, what WFMedical already does better, and prioritised recommendations. |

## TL;DR for WFMedical

WFMedical's rig is the *better core* for vanilla humanoids — server-authoritative,
deterministic, typed `LimbType`, and far richer pose coverage than MWF's. MWF's value is
**flexibility**: data-driven box definitions, penetration passthrough (multi-limb hits), and
OBBs driven from arbitrary rendered/rigged models. The recommendation is to graft that
flexibility on top of WFMedical's core, not replace it. See [doc 05](05-applying-to-wfmedical.md)
for the prioritised plan (top picks: **R1 penetration / through-and-through trauma** and **R2
data-file box specs**).
