package com.warfactory.medical.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@OnlyIn(Dist.CLIENT)
public final class UnconsciousOverlay implements LayeredDraw.Layer {

    public static final LayeredDraw.Layer INSTANCE = new UnconsciousOverlay();

    public static final ResourceLocation OVERLAY_ID =
            ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "unconscious");

    private static final ResourceLocation VIGNETTE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/vignette.png");

    private static final float FADE_STEP = 0.06F;
    private static final float FADE_EPSILON = 0.001F;

    private static final float VIGNETTE_STRENGTH = 1.0F;
    private static final float VIGNETTE_MAX = 0.97F;
    private static final float UNIFORM_DARK_BASE = 0.35F;
    private static final float BLACKOUT_START = 0.75F;
    private static final int Z_OFFSET = -90;

    private static float fade;
    private static float black;

    private UnconsciousOverlay() {
    }

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
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        if (!com.warfactory.medical.client.MedicalDebug.screenEffectsEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        boolean passedOut = ClientMedicalCache.stats().unconscious();
        fade = ease(fade, passedOut ? 1.0F : 0.0F);

        float dp = passedOut ? ClientMedicalCache.deathProgress() : 0.0F;
        float blackTarget = dp <= BLACKOUT_START ? 0.0F : (dp - BLACKOUT_START) / (1.0F - BLACKOUT_START);
        black = ease(black, blackTarget);

        if (fade <= FADE_EPSILON) {
            return;
        }

        float uniformDark = clamp01(fade * UNIFORM_DARK_BASE + black);
        int dimArgb = ((int) (uniformDark * 255.0F) << 24);
        graphics.fill(0, 0, screenW, screenH, dimArgb);

        float vignetteAlpha = Math.min(fade * VIGNETTE_STRENGTH, VIGNETTE_MAX);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, vignetteAlpha);
        graphics.blit(VIGNETTE_TEXTURE, 0, 0, Z_OFFSET, 0.0F, 0.0F, screenW, screenH, screenW, screenH);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
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
            event.registerAbove(OVERLAY_ID, GiveUpOverlay.OVERLAY_ID, GiveUpOverlay.INSTANCE);
        }
    }
}
