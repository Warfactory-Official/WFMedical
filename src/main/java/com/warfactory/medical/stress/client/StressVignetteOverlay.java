package com.warfactory.medical.stress.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.client.MedicalDebug;
import com.warfactory.medical.body.client.MedicalClientConfig;
import com.warfactory.medical.body.client.ClientMedicalCache;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Black vignette: faint from {@link #FROM}, strong from {@link StressShake#CRITICAL}, pulsing. */
@OnlyIn(Dist.CLIENT)
public final class StressVignetteOverlay implements LayeredDraw.Layer {

    public static final LayeredDraw.Layer INSTANCE = new StressVignetteOverlay();
    public static final ResourceLocation OVERLAY_ID =
            ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "stress_vignette");

    private static final float FROM = 0.4F;
    private static final float FAINT_MAX = 0.30F;
    private static final float CRITICAL_ALPHA = 0.65F;
    private static final float PULSE_SPEED = 0.35F;
    private static final float PULSE_DEPTH = 0.10F;
    private static final float SECOND_PASS = 0.6F;
    private static final int Z_OFFSET = -90;
    private static final int TEX_SIZE = 256;
    private static final float INNER = 0.25F;
    private static final float OUTER = 0.95F;

    private static ResourceLocation texture;

    private StressVignetteOverlay() {
    }

    /** Alpha of the first pass; 0 below {@link #FROM}. */
    public static float alpha(float stress) {
        if (stress <= FROM) {
            return 0.0F;
        }
        if (stress < StressShake.CRITICAL) {
            return FAINT_MAX * smoothstep(0.0F, 1.0F, (stress - FROM) / (StressShake.CRITICAL - FROM));
        }
        return CRITICAL_ALPHA + (1.0F - CRITICAL_ALPHA) * (stress - StressShake.CRITICAL) / (1.0F - StressShake.CRITICAL);
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui || player.isSpectator()
                || !MedicalClientConfig.stressVignetteEnabled() || !MedicalDebug.screenEffectsEnabled()) {
            return;
        }
        float stress = ClientMedicalCache.stats().stress();
        float alpha = alpha(stress);
        if (alpha <= 0.0F) {
            return;
        }
        double phase = (mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false)) * PULSE_SPEED;
        alpha *= 1.0F - PULSE_DEPTH + PULSE_DEPTH * Mth.sin((float) (phase % (2.0 * Math.PI)));
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        ResourceLocation tex = texture();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Math.min(alpha, 1.0F));
        graphics.blit(tex, 0, 0, Z_OFFSET, 0.0F, 0.0F, w, h, w, h);
        if (stress >= StressShake.CRITICAL) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Math.min(alpha * SECOND_PASS, 1.0F));
            graphics.blit(tex, 0, 0, Z_OFFSET, 0.0F, 0.0F, w, h, w, h);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static ResourceLocation texture() {
        if (texture == null) {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
            float half = TEX_SIZE / 2.0F;
            for (int y = 0; y < TEX_SIZE; y++) {
                for (int x = 0; x < TEX_SIZE; x++) {
                    float nx = (x + 0.5F - half) / half;
                    float ny = (y + 0.5F - half) / half;
                    float d = Mth.sqrt(nx * nx + ny * ny) / 1.41421356F;
                    int a = (int) (smoothstep(INNER, OUTER, d) * 255.0F) & 0xFF;
                    image.setPixelRGBA(x, y, a << 24);
                }
            }
            DynamicTexture dynamic = new DynamicTexture(image);
            dynamic.setFilter(true, false);
            texture = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "stress_vignette_texture");
            Minecraft.getInstance().getTextureManager().register(texture, dynamic);
        }
        return texture;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
