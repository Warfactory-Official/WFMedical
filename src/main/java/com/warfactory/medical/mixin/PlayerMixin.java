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
 * Reshapes a DOWNED (unconscious) player's collision box and eye-height so a passed-out player physically lies
 * on the ground instead of standing invisibly upright.
 *
 * <p>Applied on BOTH logical sides (this is a common mixin) because the effects are needed in two places:</p>
 * <ul>
 *   <li><b>Hitbox</b> ({@link #wfmedical$downedDimensions}) — the standing {@code 0.6 x 1.8} box is swapped for
 *       a low, wide {@code 1.2 x 0.6} box: the vanilla AABB cannot literally rotate, so a "rotated 90°" lying
 *       body is approximated by a short box wide enough to cover its length. The SERVER box makes melee / ray
 *       hits register on the lying body (so a downed player can be finished off), and the CLIENT box makes an
 *       attacker's own raytrace aim at where the body actually is.</li>
 *   <li><b>Eye-height / camera</b> ({@link #wfmedical$downedEyeHeight}) — dropped to a near-ground value so the
 *       first-person camera sits where the lying head is (the vanilla pose stays {@code STANDING}, so
 *       {@code getStandingEyeHeight} would otherwise keep returning 1.62 and leave the camera floating at
 *       standing height). The camera reads the cached eye-height, which only refreshes on
 *       {@code refreshDimensions()} — the medical layer calls that on every downed enter/exit edge (server
 *       engine + client downed packet).</li>
 * </ul>
 *
 * <p>Both hooks gate on {@link MedicalState#isDowned(Player)}, which is side-safe (server capability / client
 * downed tracker) and null-safe during early construction, so a conscious player is completely unaffected.</p>
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    /**
     * Low, wide lying-body collision box (a 90°-rotated approximation of the {@code 0.6 x 1.8} standing box).
     */
    private static final EntityDimensions WFMEDICAL$DOWNED_DIMENSIONS = EntityDimensions.scalable(1.2F, 0.6F);
    /**
     * Near-ground first-person eye height while downed, so the camera sits roughly where the lying head is.
     */
    private static final float WFMEDICAL$DOWNED_EYE_HEIGHT = 0.4F;

    /**
     * Swap in the low/wide downed collision box while the player is unconscious.
     */
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void wfmedical$downedDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> callbackInfo) {
        if (MedicalState.isDowned((Player) (Object) this)) {
            callbackInfo.setReturnValue(WFMEDICAL$DOWNED_DIMENSIONS);
        }
    }

    /**
     * Drop the eye height to near-ground while downed so the first-person camera is at the lying head, not at
     * the (still {@code STANDING}) 1.62 standing height.
     */
    @Inject(method = "getStandingEyeHeight", at = @At("HEAD"), cancellable = true)
    private void wfmedical$downedEyeHeight(Pose pose, EntityDimensions dimensions,
                                           CallbackInfoReturnable<Float> callbackInfo) {
        if (MedicalState.isDowned((Player) (Object) this)) {
            callbackInfo.setReturnValue(WFMEDICAL$DOWNED_EYE_HEIGHT);
        }
    }
}
