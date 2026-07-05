package com.warfactory.medical.mixin;

import com.warfactory.medical.api.MedicalState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Restricts sprint and jump for players with leg fractures or in the knocked-down state.
 * All decisions are read from the server-authoritative {@link MedicalState} facade, which is
 * null-safe for non-players and pre-sync clients.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "getJumpPower", at = @At("RETURN"), cancellable = true)
    private void wfmedical$scaleJumpPower(CallbackInfoReturnable<Float> callbackInfo) {
        if (!((Object) this instanceof Player player)) {
            return;
        }
        callbackInfo.setReturnValue(callbackInfo.getReturnValueF() * MedicalState.jumpMultiplier(player));
    }

    // LivingEntity.setSprinting is the single funnel that both sets the sprint flag AND adds the
    // +30% sprint speed modifier. Cancelling at this chokepoint (rather than Entity.setSprinting)
    // blocks BOTH the flag and the speed boost, on client and server, so a stray START_SPRINTING
    // packet cannot re-enable sprinting server-side either.
    @Inject(method = "setSprinting(Z)V", at = @At("HEAD"), cancellable = true)
    private void wfmedical$preventBlockedSprint(boolean sprinting, CallbackInfo callbackInfo) {
        if (sprinting
                && (Object) this instanceof Player player
                && MedicalState.isSprintBlocked(player)) {
            callbackInfo.cancel();
        }
    }
}
