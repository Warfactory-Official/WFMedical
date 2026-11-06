package com.warfactory.medical.mixin.ragdoll;

import de.sarocesch.sarosragdoll.ragdoll.physics.RagdollPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "de.sarocesch.sarosragdoll.ragdoll.RagdollEntity", remap = false)
public interface RagdollAccess {
    @Accessor("physics") RagdollPhysics wfmedical$physics();
    @Accessor("spawnYaw") void wfmedical$spawnYaw(float yaw);
}
