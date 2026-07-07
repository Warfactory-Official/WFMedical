package com.warfactory.medical.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
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
 * CLIENT-ONLY pulsating black vignette that scales with perceived pain, reaching full at
 * {@link MedicalConfig#painUnconsciousThreshold()}. Uses ZERO/ONE_MINUS_SRC_ALPHA blend (darkens edges,
 * not glow). Early-returns at low pain, restores all GL state, never throws. Self-registering via
 * the nested {@link Registrar}.
 */
@OnlyIn(Dist.CLIENT)
public final class PainVignetteOverlay implements IGuiOverlay {

    public static final IGuiOverlay INSTANCE = new PainVignetteOverlay();
    public static final String OVERLAY_ID = "wfmedical_pain_vignette";

    /**
     * Vanilla vignette sprite reused for the edge falloff.
     */
    private static final ResourceLocation VIGNETTE_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/vignette.png");

    /**
     * Below this perceived-pain fraction the effect is invisible; keeps healthy-player cost ~0.
     */
    private static final float PAIN_THRESHOLD = 0.02F;
    /**
     * Heavy vignette floor while asphyxiating (suffocating). Asphyxia carries no pain but should read as an
     * oppressive tunnel-vision closing in, combined with the passout blur.
     */
    private static final float ASPHYXIA_INTENSITY = 0.90F;
    private static final float BASE_STRENGTH = 0.85F;
    /**
     * Hard clamp so the screen never fully blacks out.
     */
    private static final float MAX_ALPHA = 0.85F;
    /**
     * Slowest pulse (radians/tick) at low pain.
     */
    private static final float PULSE_SPEED_MIN = 0.15F;
    /**
     * Fastest pulse (radians/tick) at maximum pain — quickening throb reads as more intense.
     */
    private static final float PULSE_SPEED_MAX = 0.40F;
    /**
     * Fraction of the amplitude that pulses; the remainder is a steady floor.
     */
    private static final float PULSE_DEPTH = 0.40F;
    /**
     * Matches vanilla's far-back vignette depth so world/HUD sit in front.
     */
    private static final int Z_OFFSET = -90;

    private PainVignetteOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenW, int screenH) {
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
        // Ramp so the vignette reaches full intensity at the pain that KNOCKS YOU OUT
        // (painUnconsciousThreshold, default 0.70), not at the near-unreachable pain=1. t=0 at the visibility
        // threshold, t=1 at passout -- the pain analogue of anchoring the blood greyout to the death loss.
        float passout = MedicalConfig.painUnconsciousThreshold();
        float span = passout - PAIN_THRESHOLD;
        float intensity = span <= 0.0F ? 1.0F : (pain - PAIN_THRESHOLD) / span;
        if (intensity > 1.0F) {
            intensity = 1.0F;
        }
        // Asphyxia (suffocating) drives the vignette to a heavy floor even with no pain, so the screen closes in
        // hard while you gasp for air — reads with the passout blur as "can't breathe".
        if (asphyxiating && intensity < ASPHYXIA_INTENSITY) {
            intensity = ASPHYXIA_INTENSITY;
        }

        // Time base in ticks + partial for a smooth (frame-rate independent) pulse.
        ClientLevel level = mc.level;
        float pulseSpeed = Mth.lerp(intensity, PULSE_SPEED_MIN, PULSE_SPEED_MAX);
        // getGameTime() grows without bound; multiplying it (as a float) into Mth.sin loses sub-index
        // precision on established worlds, stalling the pulse. Reduce the phase modulo one period in
        // double first so the argument stays small and the sine keeps advancing smoothly.
        double phase = ((double) (level != null ? level.getGameTime() : 0L) + partialTick) * pulseSpeed;
        float pulse = 0.5F + 0.5F * Mth.sin((float) (phase % (2.0 * Math.PI))); // 0..1
        float modulation = (1.0F - PULSE_DEPTH) + PULSE_DEPTH * pulse; // steady floor + throb

        float alpha = intensity * BASE_STRENGTH * modulation;
        if (alpha <= 0.0F) {
            return;
        }
        if (alpha > MAX_ALPHA) {
            alpha = MAX_ALPHA;
        }

        // --- Vanilla renderVignette idiom: BLACK edge darkening (tunnel vision), intensity scaled by pain. ---
        RenderSystem.enableBlend();
        // ZERO / ONE_MINUS_SRC_ALPHA on colour multiplies the framebuffer toward black at the edges, by the
        // vignette texture's alpha gradient scaled by our alpha (so the edges close in dark as pain rises);
        // keep the destination alpha channel intact (ONE / ZERO).
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ZERO,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, alpha);
        // Stretch the whole vignette texture across the screen (uWidth==textureWidth => full 0..1 sample).
        graphics.blit(VIGNETTE_TEXTURE, 0, 0, Z_OFFSET, 0.0F, 0.0F, screenW, screenH, screenW, screenH);

        // Restore GL state to defaults so nothing downstream inherits our blend/colour.
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
