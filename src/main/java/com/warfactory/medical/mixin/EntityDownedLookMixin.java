package com.warfactory.medical.mixin;

import com.warfactory.medical.client.ClientDownedTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CLIENT-ONLY: freezes the LOCAL player's view rotation while they are DOWNED (unconscious).
 *
 * <p>{@code Entity.turn(double, double)} is the mouse-look funnel — {@code MouseHandler.turnPlayer()} feeds the
 * per-frame mouse delta through it to advance {@code yRot} / {@code xRot}. While the local player is passed out
 * we cancel it at {@code HEAD}, so the camera and the player's head stop spinning with the mouse (which read as
 * a "spinning head" both in first person and to other observers, since the frozen {@code yHeadRot} is what gets
 * synced). Because the client sends no rotation change, the server-side rotation stays frozen too — no extra
 * server hook needed.</p>
 *
 * <p>This is registered in the CLIENT mixin list only, so the {@code Minecraft} / {@code ClientDownedTracker}
 * references here are never classloaded on a dedicated server. The gate is a cheap identity + set lookup.</p>
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
