package com.warfactory.medical.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes the private {@link PostChain#passes} list so the blood-loss desaturation driver can push a
 * per-frame {@code Saturation} uniform. Safe to set on all passes because {@code safeGetUniform} returns
 * a dummy for passes that do not declare the uniform.
 */
@Mixin(PostChain.class)
public interface PostChainAccessor {

    @Accessor("passes")
    List<PostPass> wfmedical$getPasses();
}
