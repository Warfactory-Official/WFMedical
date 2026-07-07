package com.warfactory.medical.mixin;

import com.warfactory.medical.client.ClientDownedTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CLIENT-ONLY: freezes the local player's view rotation while downed. {@code Entity.turn} is the
 * mouse-look funnel; cancelling it at HEAD stops yRot/xRot advancing, which means no rotation is sent
 * to the server either — so no server-side hook is needed.
 */
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
