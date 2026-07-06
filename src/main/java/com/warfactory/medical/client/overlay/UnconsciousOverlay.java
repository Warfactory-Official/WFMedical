package com.warfactory.medical.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLIENT-ONLY screen effect: a CLOSING DARK VIGNETTE shown while the local player is PASSED OUT. This is the
 * generic "unconscious" overlay: it gates on the SINGLE merged
 * {@link com.warfactory.medical.core.HealthState#UNCONSCIOUS} state
 * ({@link ClientMedicalCache#stats()}.{@code unconscious()}), which covers BOTH internal causes — an opioid
 * overdose unconsciousness and a bleed-out unconsciousness. Gated purely on that server-authoritative synced state;
 * this overlay never mutates medical state and only ever reads synced client state.
 *
 * <p>Rather than a hard cut to black, it renders "losing consciousness": the screen edges close in with a
 * dark vignette (the vanilla {@code textures/misc/vignette.png} tinted black, NON-pulsing, unlike the red
 * {@link PainVignetteOverlay}) while a mild uniform darkening dims the whole view. The centre stays faintly
 * visible so it composes with the separate {@link com.warfactory.medical.client.effect.PassoutBlurEffect
 * blur} and blood-loss desaturation passes — the combined read is a blurry, vignetted, dimmed view, not an
 * opaque wall.</p>
 *
 * <p>The transition never snaps: a static client-side {@link #fade} value is eased toward {@code 1.0} while
 * passed out and toward {@code 0.0} otherwise (matching {@link PassoutBlurEffect}'s fade rate), so the edges
 * close in and open back up gradually (e.g. after a {@code NALOXONE} injection ends an overdose unconsciousness, or
 * on revive from a bleed-out unconsciousness).</p>
 *
 * <p>Cost is ~zero for a conscious player whose fade has settled: {@link #render} early-returns before any
 * draw call once {@code fade <= 0.001}. It guards every nullable, restores all {@link RenderSystem} state it
 * changes, and never throws inside the render hook. Allocation-light: no per-frame object churn.</p>
 *
 * <p>SELF-REGISTERING: the nested {@link Registrar} subscribes to the MOD event bus on {@link Dist#CLIENT}
 * and registers {@link #INSTANCE} above all other overlays, mirroring {@link PainVignetteOverlay}.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class UnconsciousOverlay implements IGuiOverlay {

    /**
     * The singleton overlay instance; registered by {@link Registrar}.
     */
    public static final IGuiOverlay INSTANCE = new UnconsciousOverlay();

    /**
     * Overlay id used when registering with Forge.
     */
    public static final String OVERLAY_ID = "wfmedical_unconscious";

    /**
     * Vanilla vignette sprite reused for the edge falloff (opaque edges, transparent centre).
     */
    private static final ResourceLocation VIGNETTE_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/vignette.png");

    /**
     * Per-frame easing factor toward the fade target; matches {@link PassoutBlurEffect} so they close together.
     */
    private static final float FADE_STEP = 0.06F;
    /**
     * Below this fade the overlay is invisible; early-return keeps conscious cost ~0.
     */
    private static final float FADE_EPSILON = 0.001F;

    /**
     * Maps fade (0..1) onto the dark-vignette alpha; the texture's own falloff shapes the edge closing.
     */
    private static final float VIGNETTE_STRENGTH = 1.0F;
    /**
     * Hard clamp on the vignette alpha so the edges are EXTREME but never a perfectly opaque frame on their own.
     */
    private static final float VIGNETTE_MAX = 0.97F;
    /**
     * Whole-screen dim while merely downed but NOT dying: kept low so the centre stays clearly visible (this is
     * the "just extreme vignetting" look the design calls for outside of the final moments before death).
     */
    private static final float UNIFORM_DARK_BASE = 0.35F;
    /**
     * Death-timer progress ({@link ClientMedicalCache#deathProgress()}) above which the FULL pre-death blackout
     * begins ramping in. Below it there is no blackout at all — only the extreme vignette. Set high so the
     * blackout is confined to the last stretch before death ("full screen blackout only right before death").
     */
    private static final float BLACKOUT_START = 0.75F;
    /**
     * Draw z; matches vanilla's far-back vignette depth so world/HUD sit in front.
     */
    private static final int Z_OFFSET = -90;

    /**
     * Smoothed fade toward the passed-out target; drives the extreme vignette + the base dim.
     */
    private static float fade;
    /**
     * Smoothed fade toward the pre-death blackout target; drives the full-screen black, only near death.
     */
    private static float black;

    private UnconsciousOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        // PASSED OUT = overdose-unconscious OR bleeding out; mirrors server-side isDowned.
        boolean passedOut = ClientMedicalCache.stats().unconscious();
        fade = ease(fade, passedOut ? 1.0F : 0.0F);

        // Pre-death blackout target: only in the last stretch of the bleed-out death timer. deathProgress stays
        // 0 while merely downed (and for an overdose unconsciousness that will recover), so 'black' stays 0 and
        // we render ONLY the extreme vignette; it ramps toward 1 (full-screen black) as death approaches.
        float dp = passedOut ? ClientMedicalCache.deathProgress() : 0.0F;
        float blackTarget = dp <= BLACKOUT_START ? 0.0F : (dp - BLACKOUT_START) / (1.0F - BLACKOUT_START);
        black = ease(black, blackTarget);

        if (fade <= FADE_EPSILON) {
            return;
        }

        // --- (1) Uniform darkening: a mild dim while downed (centre visible), ramping to a FULL black only as
        // the pre-death 'black' fade approaches 1. Drawn while the shader colour is still white so the batched
        // fill keeps its ARGB.
        float uniformDark = clamp01(fade * UNIFORM_DARK_BASE + black);
        int dimArgb = ((int) (uniformDark * 255.0F) << 24); // black RGB, variable alpha
        graphics.fill(0, 0, screenW, screenH, dimArgb);

        // --- (2) Extreme closing DARK vignette hugging the edges (vanilla sprite tinted black, non-pulsing). ---
        float vignetteAlpha = Math.min(fade * VIGNETTE_STRENGTH, VIGNETTE_MAX);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc(); // SRC_ALPHA / ONE_MINUS_SRC_ALPHA: blend toward black at the edges.
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, vignetteAlpha);
        // Stretch the whole vignette texture across the screen (uWidth==textureWidth => full 0..1 sample).
        graphics.blit(VIGNETTE_TEXTURE, 0, 0, Z_OFFSET, 0.0F, 0.0F, screenW, screenH, screenW, screenH);

        // Restore GL state to defaults so nothing downstream inherits it.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Snap both fades to zero immediately (no easing). Called on respawn so a vignette / pre-death blackout
     * that was on-screen at the moment of death does not linger and briefly black out the fresh life while it
     * eases back down.
     */
    public static void reset() {
        fade = 0.0F;
        black = 0.0F;
    }

    /**
     * Ease {@code value} one step toward {@code target} at {@link #FADE_STEP}, clamped to {@code [0,1]}.
     */
    private static float ease(float value, float target) {
        return clamp01(value + (target - value) * FADE_STEP);
    }

    /**
     * Clamp to {@code [0,1]}.
     */
    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    /**
     * MOD-bus registrar so the overlay self-registers without touching client scaffolding. Runs only on
     * {@link Dist#CLIENT}; the vignette is registered above all other overlays so it dims the HUD while the
     * player is passed out.
     */
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registrar {

        /**
         * Guards against the mod-bus overlay event reaching this registrar more than once per launch.
         */
        private static boolean registered;

        private Registrar() {
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            if (registered) {
                return;
            }
            registered = true;
            event.registerAboveAll(OVERLAY_ID, INSTANCE);
        }
    }
}
