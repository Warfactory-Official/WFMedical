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
 * CLIENT-ONLY closing dark vignette while the local player is PASSED OUT. Gates on the single merged
 * {@code UNCONSCIOUS} state (covers both overdose and bleed-out causes). Composes with the separate
 * {@link com.warfactory.medical.client.effect.PassoutBlurEffect} and blood-loss desaturation: together
 * they read as a blurry, vignetted, dimmed view rather than a hard cut to black.
 */
@OnlyIn(Dist.CLIENT)
public final class UnconsciousOverlay implements IGuiOverlay {

    public static final IGuiOverlay INSTANCE = new UnconsciousOverlay();

    public static final String OVERLAY_ID = "wfmedical_unconscious";

    private static final ResourceLocation VIGNETTE_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/vignette.png");

    /**
     * Matches PassoutBlurEffect's FADE_STEP so vignette and blur close/open together.
     */
    private static final float FADE_STEP = 0.06F;
    /**
     * Below this the overlay is invisible; early-return keeps conscious cost ~0.
     */
    private static final float FADE_EPSILON = 0.001F;

    private static final float VIGNETTE_STRENGTH = 1.0F;
    /**
     * Edges are extreme but never a perfectly opaque frame on their own.
     */
    private static final float VIGNETTE_MAX = 0.97F;
    /**
     * Low so the centre stays visible; produces "just vignetting" look outside the final death stretch.
     */
    private static final float UNIFORM_DARK_BASE = 0.35F;
    /**
     * Death-timer progress above which the full-screen blackout ramps in. Set high so the blackout is
     * confined to the last stretch before death.
     */
    private static final float BLACKOUT_START = 0.75F;
    private static final int Z_OFFSET = -90;

    /**
     * Drives the extreme vignette + base dim.
     */
    private static float fade;
    /**
     * Drives the full-screen black; only ramps up near death.
     */
    private static float black;

    private UnconsciousOverlay() {
    }

    /**
     * Snap fades to zero on respawn so a pre-death blackout does not linger onto the fresh life.
     */
    public static void reset() {
        fade = 0.0F;
        black = 0.0F;
    }

    private static float ease(float value, float target) {
        return clamp01(value + (target - value) * FADE_STEP);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
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
     * Self-registers via MOD bus on CLIENT; drawn above all other overlays.
     */
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registrar {

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
