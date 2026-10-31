package com.warfactory.medical.mixin.body;

import com.warfactory.medical.body.client.ClientDownedTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityDownedLookMixin {

    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
    private void wfmedical$lockLookWhenDowned(double yaw, double pitch, CallbackInfo callbackInfo) {
        Entity self = (Entity) (Object) this;
        if (self == Minecraft.getInstance().player && ClientDownedTracker.isDowned(self.getId())) {
            callbackInfo.cancel();
        }
    }
}
