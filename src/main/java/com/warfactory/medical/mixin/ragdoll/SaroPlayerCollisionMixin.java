package com.warfactory.medical.mixin.ragdoll;

import com.jme3.math.Vector3f;
import com.warfactory.medical.ragdoll.Ragdolls;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.sarocesch.sarosragdoll.ragdoll.physics.BasePhysicsWorld", remap = false)
public abstract class SaroPlayerCollisionMixin {
    @Shadow @Final protected Level mcWorld;

    @Inject(method = "syncPlayers", at = @At("HEAD"))
    private void wfmedical$excludeDownedOwners(Int2ObjectMap<Vector3f> players, CallbackInfo ci) {
        if (ModList.get().isLoaded("corpse") && Ragdolls.enabled()) {
            players.int2ObjectEntrySet().removeIf(entry ->
                    mcWorld.getEntity(entry.getIntKey()) instanceof Player player && (!player.isAlive() || Ragdolls.isManaged(player)));
        }
    }
}
