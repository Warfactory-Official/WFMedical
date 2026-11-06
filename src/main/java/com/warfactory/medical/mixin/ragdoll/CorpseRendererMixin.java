package com.warfactory.medical.mixin.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import com.warfactory.medical.ragdoll.client.RagdollClient;
import de.maxhenkel.corpse.entities.CorpseEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.maxhenkel.corpse.entities.CorpseRenderer", remap = false)
public abstract class CorpseRendererMixin {
    @Inject(method = "render(Lde/maxhenkel/corpse/entities/CorpseEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void wfmedical$ragdoll(CorpseEntity corpse, float yaw, float partialTick, PoseStack pose,
                                  MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (ModList.get().isLoaded("saros_ragdoll") && ModList.get().isLoaded("corpse")
                && !corpse.isSkeleton() && RagdollClient.hidden(corpse)) {
            ci.cancel();
        }
    }
}
