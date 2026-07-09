package com.warfactory.medical.client.effect;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.mixin.PostChainAccessor;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * CLIENT-ONLY passed-out blur. Full-screen PostChain that blurs the 3D scene while the local player is
 * UNCONSCIOUS (covers both overdose and bleed-out) OR in the conscious {@code asphyxiating()} phase.
 * Composes with blood-loss desaturation and the UnconsciousOverlay vignette. PostChain created lazily;
 * all failures disable the effect for the session rather than crashing.
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PassoutBlurEffect {

    private static final float FADE_STEP = 0.06F;
    private static final float EPSILON = 0.001F;
    /**
     * Blur radius at full fade in texels; keeps tap count bounded and cheap.
     */
    private static final float MAX_RADIUS = 6.0F;

    private static final ResourceLocation SHADER =
            new ResourceLocation(WFMedical.MOD_ID, "shaders/post/passout_blur.json");

    private static PostChain chain;
    private static int chainWidth = -1;
    private static int chainHeight = -1;
    /**
     * Set once on unrecoverable failure; blocks all further processing for the session.
     */
    private static boolean disabled;

    /**
     * Smoothed client-side fade; persists across frames.
     */
    private static float fade;

    private PassoutBlurEffect() {
    }

    /**
     * Fires before HUD: blurs the scene while HUD overlays stay crisp.
     */
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (disabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator()) {
            // Not in a world / spectating: bleed the fade back to zero so we resume from off, no processing.
            fade = 0.0F;
            return;
        }

        // Advance the fade every frame (even while conscious) so it can decay smoothly back to zero.
        boolean blur = shouldBlurVision();
        float target = blur ? 1.0F : 0.0F;
        fade += (target - fade) * FADE_STEP;
        if (fade < 0.0F) {
            fade = 0.0F;
        } else if (fade > 1.0F) {
            fade = 1.0F;
        }
        if (fade <= EPSILON) {
            return;
        }

        try {
            PostChain active = ensureChain(mc);
            if (active == null) {
                return;
            }
            setBlurUniforms(active, fade * MAX_RADIUS);
            // Flush any pending GUI batch so the chain reads a complete frame.
            event.getGuiGraphics().flush();
            active.process(event.getPartialTick());
            // Rebind the main target so subsequent HUD rendering draws to the right place.
            var mainTarget = mc.getMainRenderTarget();
            mainTarget.bindWrite(false);
            // PostPass leaves BLEND DISABLED and never restores the caller's render state; without this the HUD
            // drawn next -- and translucent world/sky elements next frame -- render wrong. Restore GUI defaults.
            RenderSystem.viewport(0, 0, mainTarget.viewWidth, mainTarget.viewHeight);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (Throwable t) {
            // A shader/GL failure must never crash the game: disable and tear down for the session.
            WFMedical.LOGGER.warn("[{}] Passed-out blur effect failed; disabling for this session",
                    WFMedical.MOD_ID, t);
            disable();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        fade = 0.0F;
        closeChain();
    }

    /**
     * Snap fade to zero on respawn so the passed-out blur does not linger onto the fresh life.
     */
    public static void reset() {
        fade = 0.0F;
    }

    // ------------------------------------------------------------------ internals

    /**
     * True while unconscious (overdose or bleed-out) OR in the conscious asphyxiating phase.
     */
    private static boolean shouldBlurVision() {
        var stats = ClientMedicalCache.stats();
        return stats.unconscious() || stats.asphyxiating();
    }

    private static PostChain ensureChain(Minecraft mc) {
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();

        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(),
                        mc.getMainRenderTarget(), SHADER);
                chain.resize(width, height);
                chainWidth = width;
                chainHeight = height;
            } catch (Exception e) {
                WFMedical.LOGGER.warn("[{}] Failed to load passed-out blur shader ({}); effect disabled",
                        WFMedical.MOD_ID, SHADER, e);
                disable();
                return null;
            }
            return chain;
        }

        if (width != chainWidth || height != chainHeight) {
            chain.resize(width, height);
            chainWidth = width;
            chainHeight = height;
        }
        return chain;
    }

    /**
     * Even-indexed passes blur horizontally (1,0); odd vertically (0,1) — separable 2D blur.
     */
    private static void setBlurUniforms(PostChain postChain, float radius) {
        List<PostPass> passes = ((PostChainAccessor) postChain).wfmedical$getPasses();
        for (int i = 0; i < passes.size(); i++) {
            EffectInstance effect = passes.get(i).getEffect();
            effect.safeGetUniform("Radius").set(radius);
            AbstractUniform dir = effect.safeGetUniform("BlurDir");
            if ((i & 1) == 0) {
                dir.set(1.0F, 0.0F);
            } else {
                dir.set(0.0F, 1.0F);
            }
        }
    }

    private static void closeChain() {
        if (chain != null) {
            try {
                chain.close();
            } catch (Exception ignored) {
                // Closing is best-effort; nothing actionable if it fails.
            }
            chain = null;
        }
        chainWidth = -1;
        chainHeight = -1;
    }

    private static void disable() {
        disabled = true;
        closeChain();
    }
}
