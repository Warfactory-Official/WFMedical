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
 * CLIENT-ONLY: fixes the THIRD-PERSON (F5) camera on a DOWNED player.
 *
 * <p>The downed first-person camera is placed at the lying head via a low eye-height (0.4) in
 * {@code PlayerMixin#getStandingEyeHeight}. But {@code Camera.setup} uses that SAME eye-height as the pivot the
 * third-person camera orbits and zooms back from — so in third person the orbit point sits at ground level and
 * the zoom-back raytrace ({@code getMaxZoom}) immediately hits the floor, jamming the camera into the ground /
 * the body. This injects right before the detached zoom-back call and raises the pivot to roughly torso height
 * above the player's feet, so the third-person camera orbits the lying body properly. First person (not
 * detached) is untouched and keeps the head-on-ground view.</p>
 *
 * <p>Registered CLIENT-only so {@code Minecraft} / {@code ClientDownedTracker} never load on a dedicated
 * server. The gate is a cheap camera-entity identity + downed-set lookup.</p>
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    /**
     * Third-person orbit pivot height above the downed player's feet (≈ standing torso height).
     */
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
        double pivotY = Mth.lerp((double) partialTick, entity.yOld, entity.getY()) + WFMEDICAL$DOWNED_TP_PIVOT;
        setPosition(pos.x, pivotY, pos.z);
    }
}
