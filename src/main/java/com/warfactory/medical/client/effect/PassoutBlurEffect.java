package com.warfactory.medical.client.effect;

import com.mojang.blaze3d.shaders.AbstractUniform;
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
 * CLIENT-ONLY "passed-out" blur. Drives a real full-screen {@link PostChain} post-processing effect that
 * blurs the 3D scene while the LOCAL player is passed out — gated on the SINGLE merged
 * {@link com.warfactory.medical.core.HealthState#UNCONSCIOUS} state
 * ({@link ClientMedicalCache#stats()}.{@code unconscious()}), which covers both internal causes (an opioid
 * overdose blackout and a bleeding-out knockdown). It composes with the separate blood-loss
 * desaturation pass and the reworked {@link com.warfactory.medical.client.overlay.BlackoutOverlay closing
 * vignette}: together they read as "losing consciousness" (blurry, dimmed, edges closing in) rather than a
 * hard cut to black. Purely presentational: it reads synced client state and never mutates medical state.
 *
 * <p>Self-registers on the FORGE bus, {@link Dist#CLIENT} only, so a dedicated server never class-loads any
 * of the client render types referenced here.</p>
 *
 * <h2>Robustness (mirrors {@link BloodDesaturationEffect})</h2>
 * <ul>
 *   <li>The {@link PostChain} is created lazily inside a {@code try/catch}; a failed shader load sets
 *       {@link #disabled} and logs once, so it never retry-spams and never crashes.</li>
 *   <li>The whole render handler body is wrapped in {@code try/catch}: any GL/shader error disables the
 *       effect for the rest of the session instead of taking the game down.</li>
 *   <li>A conscious player whose fade has settled never touches the chain: the {@code fade <= EPSILON}
 *       early return runs before any lazy creation, so the effect pays effectively zero cost.</li>
 * </ul>
 *
 * <h2>Intensity model</h2>
 * <p>A static, client-side {@link #fade} is eased toward {@code passedOut ? 1 : 0} at {@link #FADE_STEP}
 * per frame, so the blur ramps in as the player passes out and ramps back out on waking. The blur radius is
 * {@code Radius = fade * }{@link #MAX_RADIUS}; the horizontal pass (index 0) uses {@code BlurDir = (1,0)} and
 * the vertical pass (index 1) uses {@code BlurDir = (0,1)}, forming a separable 2D blur.</p>
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PassoutBlurEffect {

    /**
     * Per-frame easing factor toward the fade target; small so the blur eases in/out smoothly.
     */
    private static final float FADE_STEP = 0.06F;
    /**
     * Below this fade the effect is a no-op; the early return keeps a conscious player off the hot path.
     */
    private static final float EPSILON = 0.001F;
    /**
     * Blur radius (in texels along each axis) at full fade; keeps the tap count bounded and cheap.
     */
    private static final float MAX_RADIUS = 6.0F;

    private static final ResourceLocation SHADER =
            new ResourceLocation(WFMedical.MOD_ID, "shaders/post/passout_blur.json");

    /**
     * Cached post-processing chain; {@code null} until first needed or after teardown.
     */
    private static PostChain chain;
    /**
     * Window dimensions the cached {@link #chain} was last sized for.
     */
    private static int chainWidth = -1;
    private static int chainHeight = -1;
    /**
     * Set once on unrecoverable failure; blocks all further processing for the session.
     */
    private static boolean disabled;

    /**
     * Smoothed client-side fade toward the current passed-out target; persists across frames.
     */
    private static float fade;

    private PassoutBlurEffect() {
    }

    /**
     * Fires after the 3D world and held item have been drawn to the main render target but before the HUD,
     * so the blur softens the scene while the HUD (drawn afterwards) stays crisp and readable.
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
        boolean passedOut = isLocalPlayerPassedOut();
        float target = passedOut ? 1.0F : 0.0F;
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
            mc.getMainRenderTarget().bindWrite(false);
        } catch (Throwable t) {
            // A shader/GL failure must never crash the game: disable and tear down for the session.
            WFMedical.LOGGER.warn("[{}] Passed-out blur effect failed; disabling for this session",
                    WFMedical.MOD_ID, t);
            disable();
        }
    }

    /**
     * Tear down the chain and reset the fade when leaving a world so we never leak or reuse stale GL state.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        fade = 0.0F;
        closeChain();
    }

    // ------------------------------------------------------------------ internals

    /**
     * @return {@code true} while the LOCAL player is passed out — the single merged
     * {@link com.warfactory.medical.core.HealthState#UNCONSCIOUS} state (either overdose or bleed-out
     * cause). Mirrors the server-side {@code isDowned} definition on the client.
     */
    private static boolean isLocalPlayerPassedOut() {
        return ClientMedicalCache.stats().unconscious();
    }

    /**
     * Lazily create the chain (and (re)size it to the current window). Returns {@code null} if creation
     * failed, having permanently {@link #disable() disabled} the effect.
     */
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
     * Push the per-frame {@code Radius} onto every pass and enforce the separable {@code BlurDir}: even
     * indices blur horizontally {@code (1,0)}, odd indices vertically {@code (0,1)}. {@code safeGetUniform}
     * never returns null (it yields a harmless dummy for undeclared names), so this is safe on any pass.
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

    /**
     * Close the chain and forget its cached size; safe to call when already torn down.
     */
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

    /**
     * Permanently disable the effect for this session and release any GL resources.
     */
    private static void disable() {
        disabled = true;
        closeChain();
    }
}
