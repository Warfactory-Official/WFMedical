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

Not covered by the new system (WFLib's listed gaps): no corpse entity or death inventory container, so a dead body just
holds its pose until vanilla removes it; no collision with vehicles or other entities; the first-person camera does not
clip against blocks.

Test: `ragdoll.gametest.RagdollGameTest` (overdose downs a patient => WFLib ragdoll active; recovery => released,
posture STAND).
