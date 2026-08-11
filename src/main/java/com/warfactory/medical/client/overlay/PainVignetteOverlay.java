package com.warfactory.medical.client.overlay;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@OnlyIn(Dist.CLIENT)
public final class PainVignetteOverlay implements LayeredDraw.Layer {

    public static final LayeredDraw.Layer INSTANCE = new PainVignetteOverlay();
    public static final ResourceLocation OVERLAY_ID =
            ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "pain_vignette");

    private static final ResourceLocation VIGNETTE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/vignette.png");

    private static final float PAIN_THRESHOLD = 0.02F;
    private static final float ASPHYXIA_INTENSITY = 0.90F;

    private static final float BASE_STRENGTH = 0.90F;
    private static final float MAX_ALPHA = 0.90F;
    private static final float DARK_STRENGTH = 0.85F;
    private static final float DARK_MAX_ALPHA = 0.85F;

    private static final float PULSE_SPEED_MIN = 0.20F;
    private static final float PULSE_SPEED_MAX = 0.55F;
    private static final float PULSE_DEPTH = 0.50F;
    private static final int Z_OFFSET = -90;

    private static final int TEX_SIZE = 256;
    private static final float WHITE_INNER = 0.30F;
    private static final float WHITE_OUTER = 1.00F;
    private static ResourceLocation whiteTexture;
    private static boolean whiteTextureFailed;

    private PainVignetteOverlay() {
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        if (!com.warfactory.medical.client.MedicalDebug.screenEffectsEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options == null) {
            return;
        }
        if (mc.options.hideGui || player.isSpectator()) {
            return;
        }

        var stats = ClientMedicalCache.stats();
        float pain = stats.totalPain();
        boolean asphyxiating = stats.asphyxiating();
        if (pain <= PAIN_THRESHOLD && !asphyxiating) {
            return;
        }

        float passout = MedicalConfig.painUnconsciousThreshold();
        float span = passout - PAIN_THRESHOLD;
        float t = span <= 0.0F ? 1.0F : (pain - PAIN_THRESHOLD) / span;
        t = Mth.clamp(t, 0.0F, 1.0F);
        float painIntensity = t * (2.0F - t);

        ClientLevel level = mc.level;
        float severity = Math.max(painIntensity, asphyxiating ? ASPHYXIA_INTENSITY : 0.0F);
        float pulseSpeed = Mth.lerp(severity, PULSE_SPEED_MIN, PULSE_SPEED_MAX);
        double phase = ((double) (level != null ? level.getGameTime() : 0L) + partialTick) * pulseSpeed;
        float s = 0.5F + 0.5F * Mth.sin((float) (phase % (2.0 * Math.PI)));
        float pulse = s * s;
        float modulation = (1.0F - PULSE_DEPTH) + PULSE_DEPTH * pulse;

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);

        if (asphyxiating) {
            float darkAlpha = Mth.clamp(ASPHYXIA_INTENSITY * DARK_STRENGTH * modulation, 0.0F, DARK_MAX_ALPHA);
            if (darkAlpha > 0.0F) {
                drawDarkTunnel(graphics, darkAlpha, screenW, screenH);
            }
        }

        if (painIntensity > 0.0F) {
            float whiteAlpha = Mth.clamp(painIntensity * BASE_STRENGTH * modulation, 0.0F, MAX_ALPHA);
            if (whiteAlpha > 0.0F) {
                drawWhiteWash(graphics, whiteAlpha, screenW, screenH);
            }
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void drawDarkTunnel(GuiGraphics graphics, float alpha, int screenW, int screenH) {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, alpha);
        graphics.blit(VIGNETTE_TEXTURE, 0, 0, Z_OFFSET, 0.0F, 0.0F, screenW, screenH, screenW, screenH);
    }

    private static void drawWhiteWash(GuiGraphics graphics, float alpha, int screenW, int screenH) {
        ResourceLocation tex = whiteVignetteTexture();
        boolean custom = tex != null;
        if (!custom) {
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
                    float nx = (x + 0.5F - half) / half;
                    float ny = (y + 0.5F - half) / half;
                    float d = Mth.sqrt(nx * nx + ny * ny) / 1.41421356F;
                    float a = smoothstep(WHITE_INNER, WHITE_OUTER, d);
                    int alpha = (int) (Mth.clamp(a, 0.0F, 1.0F) * 255.0F) & 0xFF;
                    image.setPixelRGBA(x, y, (alpha << 24) | 0x00FFFFFF);
                }
            }
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "pain_vignette_white");
            Minecraft.getInstance().getTextureManager().register(id, texture);
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

    @OnlyIn(Dist.CLIENT)
    @EventBusSubscriber(modid = WFMedical.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registrar {

        private static boolean registered;

        private Registrar() {
        }

        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            if (registered) {
                return;
            }
            registered = true;
            event.registerAboveAll(OVERLAY_ID, INSTANCE);
        }
    }
}
