package com.warfactory.medical.mixin;

import com.warfactory.medical.api.MedicalState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reshapes a downed player's collision box and eye-height on BOTH sides. The standing 0.6x1.8 box is
 * swapped for a low wide 1.2x0.6 box (AABB cannot rotate, so a lying body is approximated). The pose
 * stays STANDING, so getStandingEyeHeight would otherwise return 1.62 – we drop it to near-ground so the
 * first-person camera sits where the lying head is. The medical layer calls refreshDimensions() on every
 * downed enter/exit edge so the cached eye-height updates.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    private static final EntityDimensions WFMEDICAL$DOWNED_DIMENSIONS = EntityDimensions.scalable(1.2F, 0.6F);
    private static final float WFMEDICAL$DOWNED_EYE_HEIGHT = 0.4F;

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void wfmedical$downedDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> callbackInfo) {
        if (MedicalState.isDowned((Player) (Object) this)) {
            callbackInfo.setReturnValue(WFMEDICAL$DOWNED_DIMENSIONS);
        }
    }

    @Inject(method = "getStandingEyeHeight", at = @At("HEAD"), cancellable = true)
    private void wfmedical$downedEyeHeight(Pose pose, EntityDimensions dimensions,
                                           CallbackInfoReturnable<Float> callbackInfo) {
        if (MedicalState.isDowned((Player) (Object) this)) {
            callbackInfo.setReturnValue(WFMEDICAL$DOWNED_EYE_HEIGHT);
        }
    }
}
