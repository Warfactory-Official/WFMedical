package com.warfactory.medical.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Client-only accessor mixin exposing the private {@link PostChain#passes} list so the blood-loss
 * desaturation driver can push a per-frame {@code Saturation} uniform onto every {@link PostPass}.
 *
 * <p>{@link PostChain} keeps its pass list private and offers no per-uniform mutation API, so the only
 * non-reflective way to set a custom uniform each frame is to read the passes and go through each pass's
 * effect. Setting {@code Saturation} on <em>all</em> passes is safe because
 * {@code EffectInstance#safeGetUniform} returns a harmless dummy uniform for passes (such as the trailing
 * blit) that do not declare it.</p>
 */
@Mixin(PostChain.class)
public interface PostChainAccessor {

    /**
     * @return the live, ordered list of post-processing passes owned by this chain.
     */
    @Accessor("passes")
    List<PostPass> wfmedical$getPasses();
}
