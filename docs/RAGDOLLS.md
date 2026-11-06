# Medical ragdolls (NeoForge 1.21.1)

Install **Saro's Player Ragdoll 2.0.5** (`saros_ragdoll`) and **Corpse 1.1.13**
with WFMedical, on both client and server. Both are optional; the `ragdoll`
WFMedical system activates only when both are present. It requires the existing
`body` and `treatment` systems. The third-party mods are separate dependencies,
not included in WFMedical's JAR.

- Downed/unconscious players get a physics body. Their original player still owns
  their injuries, inventory, damage, and treatment. No death inventory is made
  while a player is alive. Recovery removes the proxy and finds nearby standing
  room; the normal medical revival rules remain in effect.
- On actual death, Corpse creates its normal inventory. The physics body links to
  that entity; an existing downed body is reused to retain its settled pose.
- Right-click a dead body to open the native Corpse inventory, including while
  holding a medical item. Corpse's access policy still applies. Proxy interactions
  validate limb reach and block visibility on the server.
- Right-click an unconscious player with an empty hand to request the existing
  medical interaction sheet. Medical items resolve the physics body to the actual
  patient and retain the normal server treatment validation.
- Native limb poses and UUID links are saved. Source corpses still age, become
  skeletons, and despawn according to Corpse's configuration. The proxy carries
  no items; removing it never removes the original corpse or its loot.

Saro's independent downing, instant-revive clicks, death GUI actions, join kill,
logout cleanup, and commands are disabled while this bridge is active: WFMedical
owns those decisions. Physics, networking, and native library extraction stay
with Saro. A narrow loader hook loads the shipped Bullet binary through Bullet's
own class loader, required by NeoForge's separate plugin layer.

The renderer uses native skin, armor, and held-item layers with physics poses.
If physics is unavailable, normal patient and Corpse visuals remain visible.
The integration is pinned to the inspected releases because its small mixin
hooks depend on their implementation details.

Known limits: capes/elytra/accessory layers are not drawn. Fresh offline loads
retain Corpse's skin fallback. Saro's single-precision world coordinates lose joint
precision millions of blocks from origin; film near origin. AHF projectile anatomy remains the medical
player's downed hit rig; it is not replaced with six Bullet collision shapes.
Native filming, backward seeking, and an 11-second 1280x720/60fps Flashback
export passed in Warforge Harness Dev. Replay `2026-11-06T12_18_43.zip` contains
medical downing, actual death, and medical recovery; the clip uses FOV 50 and a
diagonal camera at yaw -45/pitch 38. Proof and footage are in Warforge Archive.

Sources: [Saro's Player Ragdoll](https://www.curseforge.com/minecraft/mc-mods/saro-s-player-ragdoll),
[Corpse](https://github.com/henkelmax/corpse). Saro is All Rights Reserved; use the
unmodified separately installed release. This bridge does not copy its physics
implementation or redistribute a patched mod.
