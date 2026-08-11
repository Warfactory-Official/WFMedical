package com.warfactory.medical.mixin;

import com.warfactory.medical.api.MedicalState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shrinks a downed player to a prone hitbox.
 *
 * <p>Targets {@link Entity} rather than {@link Player}: in 1.20.1 {@code Player} declared its own
 * {@code getDimensions(Pose)} override, but 1.21.1 removed it, leaving the single declaration on
 * {@code Entity}. The player check is therefore done in the handler instead of by the mixin target.
 */
@Mixin(Entity.class)
public abstract class PlayerMixin {

    private static final float WFMEDICAL$DOWNED_EYE_HEIGHT = 0.4F;

    // 1.21 folded eye height into EntityDimensions and deleted Entity#getStandingEyeHeight, so the
    // downed eye height is baked into the dimensions here instead of overridden in a second injection.
    private static final EntityDimensions WFMEDICAL$DOWNED_DIMENSIONS =
            EntityDimensions.scalable(1.2F, 0.6F).withEyeHeight(WFMEDICAL$DOWNED_EYE_HEIGHT);

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void wfmedical$downedDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> callbackInfo) {
        if ((Object) this instanceof Player player && MedicalState.isDowned(player)) {
            callbackInfo.setReturnValue(WFMEDICAL$DOWNED_DIMENSIONS);
        }
    }
}
