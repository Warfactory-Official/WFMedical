package com.warfactory.medical.mixin;

import com.warfactory.medical.api.MedicalState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "getJumpPower()F", at = @At("RETURN"), cancellable = true)
    private void wfmedical$scaleJumpPower(CallbackInfoReturnable<Float> callbackInfo) {
        if (!((Object) this instanceof Player player)) {
            return;
        }
        callbackInfo.setReturnValue(callbackInfo.getReturnValueF() * MedicalState.jumpMultiplier(player));
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void wfmedical$blockJumpWhenUnconscious(CallbackInfo callbackInfo) {
        if ((Object) this instanceof Player player && MedicalState.isUnconscious(player)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setSprinting(Z)V", at = @At("HEAD"), cancellable = true)
    private void wfmedical$preventBlockedSprint(boolean sprinting, CallbackInfo callbackInfo) {
        if (sprinting
                && (Object) this instanceof Player player
                && MedicalState.isSprintBlocked(player)) {
            callbackInfo.cancel();
        }
    }
}
