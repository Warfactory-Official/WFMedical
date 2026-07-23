package com.warfactory.medical.client.overlay;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLIENT-ONLY pain vignette modelled on the ARMA Reforger injury effect: a strong, <b>white</b> radial wash
 * that pulses (heartbeat-like) at the screen edges and reaches well inward, scaling with perceived pain and
 * reaching full at {@link MedicalConfig#painUnconsciousThreshold()}.
 *
 * <p>White (mix-toward-white blend) is deliberate: unlike the old multiply-toward-black vignette – which
 * vanished into dark scenes and any other mod's screen darkening – a bright wash reads clearly over almost
 * anything, so it neither looks weak nor gets swallowed by other effects.</p>
 *
 * <p>Asphyxia (suffocation) is kept as a separate <b>dark</b> tunnel-vision pass so "can't breathe" still
 * reads as the screen closing in dark rather than a white flash. Early-returns at low pain, restores all GL
 * state, never throws. Self-registering via the nested {@link Registrar}.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class PainVignetteOverlay implements IGuiOverlay {

    public static final IGuiOverlay INSTANCE = new PainVignetteOverlay();
    public static final String OVERLAY_ID = "wfmedical_pain_vignette";

    /**
     * Vanilla vignette sprite reused for the dark asphyxia tunnel (and as a white fallback).
     */
    private static final ResourceLocation VIGNETTE_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/vignette.png");

    /**
     * Below this perceived-pain fraction the effect is invisible; keeps healthy-player cost ~0.
     */
    private static final float PAIN_THRESHOLD = 0.02F;
    /**
     * Heavy dark-vignette floor while asphyxiating (suffocating). Asphyxia carries no pain but should read as
     * an oppressive tunnel-vision closing in, combined with the passout blur.
     */
    private static final float ASPHYXIA_INTENSITY = 0.90F;

    /**
     * Peak white strength at full pain (before the per-pixel gradient and pulse scale it down).
     */
    private static final float BASE_STRENGTH = 0.90F;
    /**
     * Hard clamp so the white wash never fully whites out the screen.
     */
    private static final float MAX_ALPHA = 0.90F;
    /**
     * Peak darkness for the asphyxia tunnel (kept close to the old behaviour).
     */
    private static final float DARK_STRENGTH = 0.85F;
    private static final float DARK_MAX_ALPHA = 0.85F;

    /**
     * Slowest pulse (radians/tick) at low severity.
     */
    private static final float PULSE_SPEED_MIN = 0.20F;
    /**
     * Fastest pulse (radians/tick) at maximum severity – a quickening heartbeat reads as more intense.
     */
    private static final float PULSE_SPEED_MAX = 0.55F;
    /**
     * Fraction of the amplitude that pulses; the remainder is a steady floor so the wash never disappears.
     */
    private static final float PULSE_DEPTH = 0.50F;
    /**
     * Matches vanilla's far-back vignette depth so world/HUD sit in front.
     */
    private static final int Z_OFFSET = -90;

    // --- Procedural white radial-gradient texture (built once, lazily, on the render thread). ---
    private static final int TEX_SIZE = 256;
    /**
     * Normalised radius (0 = centre, 1 = corner) where the white band starts / reaches full. Tuned so a wide
     * outer band whitens while an inner oval stays clear – the ARMA "clear centre, bright surround" look.
     */
    private static final float WHITE_INNER = 0.30F;
    private static final float WHITE_OUTER = 1.00F;
    private static ResourceLocation whiteTexture;
    private static boolean whiteTextureFailed;

    private PainVignetteOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenW, int screenH) {
        // Debug master gate for WF Medical screen effects (toggle with MedicalKeyMappings.TOGGLE_SCREEN_FX).
        if (!com.warfactory.medical.client.MedicalDebug.screenEffectsEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options == null) {
            return;
        }
        // Respect F1 (hidden GUI) and don't nag spectators.
        if (mc.options.hideGui || player.isSpectator()) {
            return;
        }

        var stats = ClientMedicalCache.stats();
        float pain = stats.totalPain();
        boolean asphyxiating = stats.asphyxiating();
        if (pain <= PAIN_THRESHOLD && !asphyxiating) {
            return;
        }

        // Ramp so the white wash reaches full intensity at the pain that KNOCKS YOU OUT
        // (painUnconsciousThreshold, default 0.70), not at the near-unreachable pain=1. Ease-out so it becomes
        // clearly visible early (ARMA reads strong even at light injury), not just near passout.
        float passout = MedicalConfig.painUnconsciousThreshold();
        float span = passout - PAIN_THRESHOLD;
        float t = span <= 0.0F ? 1.0F : (pain - PAIN_THRESHOLD) / span;
        t = Mth.clamp(t, 0.0F, 1.0F);
        float painIntensity = t * (2.0F - t); // ease-out: front-loaded visibility

        // Shared throb, quickening with severity. getGameTime() grows without bound; reduce the phase modulo
        // one period in double first so the sine keeps advancing smoothly on long-lived worlds.
        ClientLevel level = mc.level;
        float severity = Math.max(painIntensity, asphyxiating ? ASPHYXIA_INTENSITY : 0.0F);
        float pulseSpeed = Mth.lerp(severity, PULSE_SPEED_MIN, PULSE_SPEED_MAX);
        double phase = ((double) (level != null ? level.getGameTime() : 0L) + partialTick) * pulseSpeed;
        float s = 0.5F + 0.5F * Mth.sin((float) (phase % (2.0 * Math.PI))); // 0..1
        float pulse = s * s; // sharpen the peaks into a heartbeat-like throb
        float modulation = (1.0F - PULSE_DEPTH) + PULSE_DEPTH * pulse; // steady floor + throb

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);

        // 1) Asphyxia dark tunnel
        if (asphyxiating) {
            float darkAlpha = Mth.clamp(ASPHYXIA_INTENSITY * DARK_STRENGTH * modulation, 0.0F, DARK_MAX_ALPHA);
            if (darkAlpha > 0.0F) {
                drawDarkTunnel(graphics, darkAlpha, screenW, screenH);
            }
        }

        // 2) Pain white wash
        if (painIntensity > 0.0F) {
            float whiteAlpha = Mth.clamp(painIntensity * BASE_STRENGTH * modulation, 0.0F, MAX_ALPHA);
            if (whiteAlpha > 0.0F) {
                drawWhiteWash(graphics, whiteAlpha, screenW, screenH);
            }
        }

        // Restore GL state to defaults so nothing downstream inherits our blend/colour.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Multiply-toward-black edge darkening (ZERO / ONE_MINUS_SRC_ALPHA on colour), keeping dest alpha intact.
     */
    private static void drawDarkTunnel(GuiGraphics graphics, float alpha, int screenW, int screenH) {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, alpha);
        graphics.blit(VIGNETTE_TEXTURE, 0, 0, Z_OFFSET, 0.0F, 0.0F, screenW, screenH, screenW, screenH);
    }

    /**
     * Mix-toward-white edge wash (SRC_ALPHA / ONE_MINUS_SRC_ALPHA on colour): final = mix(scene, white,
     * texAlpha * alpha). The gradient's alpha keeps the centre clear while the periphery washes bright.
     */
    private static void drawWhiteWash(GuiGraphics graphics, float alpha, int screenW, int screenH) {
        ResourceLocation tex = whiteVignetteTexture();
        boolean custom = tex != null;
        if (!custom) {
            // Fallback: the vanilla vignette alpha still concentrates at the edges, so a white tint reads.
            tex = VIGNETTE_TEXTURE;
        }
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(tex, 0, 0, Z_OFFSET, 0.0F, 0.0F, screenW, screenH, screenW, screenH);
    }


    private static ResourceLocation whiteVignetteTexture() {
        if (whiteTexture != null) {
            return whiteTexture;
        }
        if (whiteTextureFailed) {
            return null;
        }
        try {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
            float half = TEX_SIZE / 2.0F;
            for (int y = 0; y < TEX_SIZE; y++) {
                for (int x = 0; x < TEX_SIZE; x++) {
                    float nx = (x + 0.5F - half) / half; // -1..1
                    float ny = (y + 0.5F - half) / half; // -1..1
                    float d = Mth.sqrt(nx * nx + ny * ny) / 1.41421356F; // 0 centre .. 1 corner
                    float a = smoothstep(WHITE_INNER, WHITE_OUTER, d);
                    int alpha = (int) (Mth.clamp(a, 0.0F, 1.0F) * 255.0F) & 0xFF;
                    // NativeImage packs ABGR; white is symmetric so only the alpha byte varies.
                    image.setPixelRGBA(x, y, (alpha << 24) | 0x00FFFFFF);
                }
            }
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = new ResourceLocation(WFMedical.MOD_ID, "pain_vignette_white");
            Minecraft.getInstance().getTextureManager().register(id, texture);
            // Linear filtering so the 256px gradient stays smooth when stretched across a full-res screen.
            texture.setFilter(true, false);
            whiteTexture = id;
            return whiteTexture;
        } catch (Throwable t) {
            WFMedical.LOGGER.warn("[{}] Failed to build pain vignette texture; using vanilla fallback",
                    WFMedical.MOD_ID, t);
            whiteTextureFailed = true;
            return null;
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Self-registers via MOD bus on CLIENT; drawn above all other overlays.
     */
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registrar {

        /**
         * Guards against double-registration on the mod bus.
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
