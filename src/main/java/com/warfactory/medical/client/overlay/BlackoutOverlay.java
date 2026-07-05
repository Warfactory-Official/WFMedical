package com.warfactory.medical.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.HealthState;
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
 * CLIENT-ONLY screen effect: a CLOSING DARK VIGNETTE shown while the local player is PASSED OUT — either
 * blacked out from an injectable overdose ({@link ClientMedicalCache#stats()}.{@code blackout()}) or knocked
 * down / bleeding out ({@code state() == }{@link HealthState#KNOCKED_DOWN}). Gated purely on that
 * server-authoritative synced state; this overlay never mutates medical state and only ever reads synced
 * client state.
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
 * close in and open back up gradually (e.g. after a {@code NALOXONE} injection ends a blackout, or on
 * revive from a knockdown).</p>
 *
 * <p>Cost is ~zero for a conscious player whose fade has settled: {@link #render} early-returns before any
 * draw call once {@code fade <= 0.001}. It guards every nullable, restores all {@link RenderSystem} state it
 * changes, and never throws inside the render hook. Allocation-light: no per-frame object churn.</p>
 *
 * <p>SELF-REGISTERING: the nested {@link Registrar} subscribes to the MOD event bus on {@link Dist#CLIENT}
 * and registers {@link #INSTANCE} above all other overlays, mirroring {@link PainVignetteOverlay}.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class BlackoutOverlay implements IGuiOverlay {

    /** The singleton overlay instance; registered by {@link Registrar}. */
    public static final IGuiOverlay INSTANCE = new BlackoutOverlay();

    /** Overlay id used when registering with Forge. */
    public static final String OVERLAY_ID = "wfmedical_blackout";

    /** Vanilla vignette sprite reused for the edge falloff (opaque edges, transparent centre). */
    private static final ResourceLocation VIGNETTE_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/vignette.png");

    /** Per-frame easing factor toward the fade target; matches {@link PassoutBlurEffect} so they close together. */
    private static final float FADE_STEP = 0.06F;
    /** Below this fade the overlay is invisible; early-return keeps conscious cost ~0. */
    private static final float FADE_EPSILON = 0.001F;

    /** Maps fade (0..1) onto the dark-vignette alpha; the texture's own falloff shapes the edge closing. */
    private static final float VIGNETTE_STRENGTH = 1.0F;
    /** Hard clamp on the vignette alpha so the closing edges never become a perfectly opaque frame. */
    private static final float VIGNETTE_MAX = 0.95F;

    /** Uniform whole-screen darkening at full fade before the deep-fade boost (keeps the centre visible). */
    private static final float UNIFORM_DARK_BASE = 0.35F;
    /** Deepest uniform darkening at maximum fade; deliberately NOT opaque so the view stays faintly visible. */
    private static final float UNIFORM_DARK_MAX = 0.60F;
    /** Fade above which the uniform darkening ramps from BASE toward MAX (the centre dims further). */
    private static final float DEEPEN_THRESHOLD = 0.85F;

    /** Above this fade the centred "unconscious" hint is legible against the dimmed view and gets drawn. */
    private static final float HINT_THRESHOLD = 0.85F;
    /** Draw z; matches vanilla's far-back vignette depth so world/HUD sit in front. */
    private static final int Z_OFFSET = -90;
    /** Faint hint text drawn near the centre while deeply passed out. */
    private static final String HINT_TEXT = "...";

    /** Smoothed client-side fade toward the current passed-out target; persists across frames. */
    private static float fade;

    private BlackoutOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        // PASSED OUT = blacked out (overdose) OR knocked down (bleeding out); mirrors server-side isDowned.
        boolean passedOut = ClientMedicalCache.stats().blackout()
                || ClientMedicalCache.stats().state() == HealthState.KNOCKED_DOWN;
        float target = passedOut ? 1.0F : 0.0F;

        // Ease toward the target and clamp; the vignette closes in and opens back up smoothly.
        fade += (target - fade) * FADE_STEP;
        if (fade < 0.0F) {
            fade = 0.0F;
        } else if (fade > 1.0F) {
            fade = 1.0F;
        }
        if (fade <= FADE_EPSILON) {
            return;
        }

        // --- (1) Mild uniform darkening across the whole screen (never fully opaque). ---
        // Drawn while the shader colour is still the default white so the batched fill keeps its ARGB.
        float uniformDark = fade * UNIFORM_DARK_BASE;
        if (fade > DEEPEN_THRESHOLD) {
            float t = (fade - DEEPEN_THRESHOLD) / (1.0F - DEEPEN_THRESHOLD);
            uniformDark += t * (UNIFORM_DARK_MAX - UNIFORM_DARK_BASE);
        }
        if (uniformDark > UNIFORM_DARK_MAX) {
            uniformDark = UNIFORM_DARK_MAX;
        }
        int dimArgb = ((int) (uniformDark * 255.0F) << 24); // black RGB, variable alpha
        graphics.fill(0, 0, screenW, screenH, dimArgb);

        // --- (2) Closing DARK vignette hugging the edges (vanilla sprite tinted black, non-pulsing). ---
        float vignetteAlpha = fade * VIGNETTE_STRENGTH;
        if (vignetteAlpha > VIGNETTE_MAX) {
            vignetteAlpha = VIGNETTE_MAX;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc(); // SRC_ALPHA / ONE_MINUS_SRC_ALPHA: blend toward black at the edges.
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, vignetteAlpha);
        // Stretch the whole vignette texture across the screen (uWidth==textureWidth => full 0..1 sample).
        graphics.blit(VIGNETTE_TEXTURE, 0, 0, Z_OFFSET, 0.0F, 0.0F, screenW, screenH, screenW, screenH);

        // Restore GL state to defaults so nothing downstream (incl. the batched fill/hint) inherits it.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        // --- (3) A faint centred hint once we're deep enough for it to read against the dimmed view. ---
        if (fade >= HINT_THRESHOLD) {
            int textWidth = mc.font.width(HINT_TEXT);
            int tx = (screenW - textWidth) / 2;
            int ty = screenH / 2;
            graphics.drawString(mc.font, HINT_TEXT, tx, ty, 0x40FFFFFF, false);
        }
    }

    /**
     * MOD-bus registrar so the overlay self-registers without touching client scaffolding. Runs only on
     * {@link Dist#CLIENT}; the vignette is registered above all other overlays so it dims the HUD while the
     * player is passed out.
     */
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registrar {

        /** Guards against the mod-bus overlay event reaching this registrar more than once per launch. */
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
