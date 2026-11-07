# Medical ragdolls (NeoForge 1.21.1)

A downed (unconscious) patient goes limp under **WFLib's ragdoll** (`com.wf.wflib.ragdoll`, see WFLib `docs/ragdoll.md`):
a server-side solver over the AHF rig, drawn on the AHF jointed body. WFMedical only says when: the `ragdoll` system
(`wfmedical-systems.toml`, requires `body` and `wflib:ragdoll`) fills `BodyHooks.DOWNED`, which `MedicalStance.downed`
calls around the AHF posture lock.

- **Downed:** after the posture lock, `Ragdolls.set(player, true)`. The player entity itself is the body (no proxy
  entity, no second inventory); injuries, inventory, damage and treatment stay on the player.
- **Recovered:** before the posture lock is released, `Ragdolls.set(player, false)`: the body stands up where it lies
  (a 14-tick blend), then the lock goes. In that order, because a lock released with the entity at the body's low point
  reads no room and forces prone.
- **Hits and blasts** knock the body about through WFLib's own damage listener (rounds push the nearest body part,
  explosions throw it); WFMedical does nothing for that.
- Medical, hit registration and rewind read the simulated pose (WFLib: server authority).

This replaces the earlier bridge to Saro's Player Ragdoll and Corpse (a proxy entity, Bullet physics and Saro mixins);
neither mod, nor the Bullet dependency, is needed any more.

## Corpses

WFLib's ragdoll leaves no body behind; loot-storing corpses are the **Corpse** mod's (`corpse` 1.1.13, optional, both
sides). With it installed and `keepInventory` **off**, any player or soldier (`SoldierPlayer`) that dies leaves a Corpse
entity holding their kit (gear shown on the body, native access rules, skeleton and despawn timers from Corpse's own
config); nothing in WFMedical is involved. The dying body plays its death and is removed as usual and the corpse lies
where it fell. With `keepInventory` on, Corpse stores nothing (the stage and filming harnesses turn it on for their
bots). The corpse is Corpse's own static model, not the ragdoll's last pose.

Not covered (WFLib's listed gaps): no collision with vehicles or other entities; the first-person camera does not clip
against blocks.

Test: `ragdoll.gametest.RagdollGameTest` (overdose downs a patient => WFLib ragdoll active; recovery => released,
posture STAND).
