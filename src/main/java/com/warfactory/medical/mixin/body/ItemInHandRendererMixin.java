package com.warfactory.medical.mixin.body;

import com.warfactory.medical.body.api.MedicalState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void wfmedical$hideHeldItemsWhenDowned(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && MedicalState.isDowned(player)) {
            ci.cancel();
        }
    }
}
