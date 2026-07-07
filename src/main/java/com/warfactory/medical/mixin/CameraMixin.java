package com.warfactory.medical.mixin;

import com.warfactory.medical.client.ClientDownedTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CLIENT-ONLY: fixes the third-person camera on a downed player. The low eye-height (0.4) set by
 * PlayerMixin also moves the orbit pivot to ground level, which makes the zoom-back raytrace immediately
 * hit the floor and jam the camera into the body. This injects before the zoom-back and raises the pivot
 * to torso height; first-person is untouched.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    private static final double WFMEDICAL$DOWNED_TP_PIVOT = 1.0D;

    @Shadow
    public abstract Vec3 getPosition();

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;move(DDD)V", ordinal = 0))
    private void wfmedical$downedThirdPersonPivot(BlockGetter level, Entity entity, boolean detached,
                                                  boolean thirdPersonReverse, float partialTick,
                                                  CallbackInfo callbackInfo) {
        if (!detached || !(entity instanceof Player player)) {
            return;
        }
        if (entity != Minecraft.getInstance().getCameraEntity()) {
            return;
        }
        if (!ClientDownedTracker.isDowned(player.getId())) {
            return;
        }
        // The camera position is currently the (low, downed) eye pivot; raise it to torso height before the
        // vanilla zoom-back (which runs immediately after this injection point) orbits/zooms from it.
        Vec3 pos = getPosition();
        double pivotY = Mth.lerp(partialTick, entity.yOld, entity.getY()) + WFMEDICAL$DOWNED_TP_PIVOT;
        setPosition(pos.x, pivotY, pos.z);
    }
}
