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

@Mixin(Camera.class)
public abstract class CameraMixin {

    private static final double WFMEDICAL$DOWNED_TP_PIVOT = 1.0D;

    @Shadow
    public abstract Vec3 getPosition();

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;move(FFF)V", ordinal = 0))
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
        Vec3 pos = getPosition();
        double pivotY = Mth.lerp(partialTick, entity.yOld, entity.getY()) + WFMEDICAL$DOWNED_TP_PIVOT;
        setPosition(pos.x, pivotY, pos.z);
    }
}
