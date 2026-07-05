package com.warfactory.medical.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
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
 * CLIENT-ONLY screen effect: a pulsating RED vignette hugging the screen edges whose intensity scales
 * with perceived pain ({@link ClientMedicalCache#stats()}.{@code totalPain()}, already a saturated 0..1
 * value with painkiller masking applied). This mirrors the vanilla low-health / portal vignette drawn by
 * {@code net.minecraft.client.gui.Gui#renderVignette} using {@code textures/misc/vignette.png}, but tinted
 * red and animated with a sine pulse.
 *
 * <p>Unlike vanilla's darkening blend ({@code ZERO / ONE_MINUS_SRC_COLOR}), this uses an ADDITIVE blend
 * ({@code SRC_ALPHA / ONE}) so the vignette texture's opaque edges GLOW red rather than darken, reading as
 * pain rather than dying. The shader colour is set to {@code (1, 0.05, 0.05, alpha)} so only the red
 * channel meaningfully contributes; the additive destination factor means the alpha in the shader colour
 * scales the emitted red per-texel.</p>
 *
 * <p>Cost is ~zero for a healthy player: {@link #render} early-returns before touching any GL state while
 * {@code pain <= 0.02}. It reads synced client state only, never mutates medical state, guards every
 * nullable, restores all {@link RenderSystem} state it changes, and never throws inside the render hook.</p>
 *
 * <p>SELF-REGISTERING: the nested {@link Registrar} subscribes to the MOD event bus on {@link Dist#CLIENT}
 * and registers {@link #INSTANCE} above all other overlays, so no shared scaffolding file has to be
 * edited.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class PainVignetteOverlay implements IGuiOverlay {

    /** The singleton overlay instance; registered by {@link Registrar}. */
    public static final IGuiOverlay INSTANCE = new PainVignetteOverlay();

    /** Overlay id used when registering with Forge. */
    public static final String OVERLAY_ID = "wfmedical_pain_vignette";

    /** Vanilla vignette sprite reused for the edge falloff (fully opaque centre-out gradient). */
    private static final ResourceLocation VIGNETTE_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/vignette.png");

    /** Below this perceived-pain fraction the effect is invisible; early-return keeps healthy cost ~0. */
    private static final float PAIN_THRESHOLD = 0.02F;
    /** Maps pain (0..1) onto the base opacity ceiling before pulse modulation. */
    private static final float BASE_STRENGTH = 0.85F;
    /** Hard clamp so the screen never fully saturates red. */
    private static final float MAX_ALPHA = 0.85F;
    /** Slowest pulse (radians/tick) at low pain. */
    private static final float PULSE_SPEED_MIN = 0.15F;
    /** Fastest pulse (radians/tick) at maximum pain — a faster throb reads as more intense. */
    private static final float PULSE_SPEED_MAX = 0.40F;
    /** Fraction of the amplitude that pulses; the remainder is a steady floor. */
    private static final float PULSE_DEPTH = 0.40F;
    /** Draw z; matches vanilla's far-back vignette depth so world/HUD sit in front. */
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

        float pain = ClientMedicalCache.stats().totalPain();
        if (pain <= PAIN_THRESHOLD) {
            return;
        }
        if (pain > 1.0F) {
            pain = 1.0F;
        }

        // Time base in ticks + partial for a smooth (frame-rate independent) pulse.
        ClientLevel level = mc.level;
        float pulseSpeed = Mth.lerp(pain, PULSE_SPEED_MIN, PULSE_SPEED_MAX);
        // getGameTime() grows without bound; multiplying it (as a float) into Mth.sin loses sub-index
        // precision on established worlds, stalling the pulse. Reduce the phase modulo one period in
        // double first so the argument stays small and the sine keeps advancing smoothly.
        double phase = ((double) (level != null ? level.getGameTime() : 0L) + partialTick) * pulseSpeed;
        float pulse = 0.5F + 0.5F * Mth.sin((float) (phase % (2.0 * Math.PI))); // 0..1
        float modulation = (1.0F - PULSE_DEPTH) + PULSE_DEPTH * pulse; // steady floor + throb

        float alpha = pain * BASE_STRENGTH * modulation;
        if (alpha <= 0.0F) {
            return;
        }
        if (alpha > MAX_ALPHA) {
            alpha = MAX_ALPHA;
        }

        // --- Vanilla renderVignette idiom, tinted red + additive so edges GLOW instead of darken. ---
        RenderSystem.enableBlend();
        // SRC_ALPHA / ONE for colour = additive red edge glow; keep alpha channel intact (ONE / ZERO).
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 0.05F, 0.05F, alpha);
        // Stretch the whole vignette texture across the screen (uWidth==textureWidth => full 0..1 sample).
        graphics.blit(VIGNETTE_TEXTURE, 0, 0, Z_OFFSET, 0.0F, 0.0F, screenW, screenH, screenW, screenH);

        // Restore GL state to defaults so nothing downstream inherits our blend/colour.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * MOD-bus registrar so the overlay self-registers without touching {@code WFMedicalClient}. Runs only
     * on {@link Dist#CLIENT}; the vignette is drawn above all other overlays (on top of the vanilla and
     * medical HUD).
     */
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registrar {

        private Registrar() {
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll(OVERLAY_ID, INSTANCE);
        }
    }
}
