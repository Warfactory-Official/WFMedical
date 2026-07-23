package com.warfactory.medical.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * CLIENT-ONLY debug switchboard for the Warfactory Medical full-screen "screen effects" (blood desaturation,
 * passed-out blur, pain vignette, unconscious vignette, damage outline). Two runtime toggles, driven by
 * {@link MedicalKeyMappings#TOGGLE_SCREEN_FX} / {@link MedicalKeyMappings#TOGGLE_FX_LOG}:
 *
 * <ul>
 *   <li>{@link #screenEffectsEnabled()} -- master gate; when off, every screen effect early-returns so the
 *       underlying vanilla frame (e.g. the underwater blue overlay) is left untouched for A/B comparison.</li>
 *   <li>{@link #verbose()} -- turns on extensive per-frame logging, including framebuffer pixel sampling, so the
 *       exact colour the desaturation blit produces over the water overlay can be observed.</li>
 * </ul>
 *
 * All fields are plain statics on the render thread; no synchronisation needed.
 *
 * <p><b>Developer-gated.</b> Every switch here is inert unless the JVM was started with
 * {@code -Dwfmedical.debug=true} (see {@link #ENABLED}). Without that startup flag the debug key bindings are
 * never registered, the toggles cannot be flipped, logging never fires, and the master gate reports
 * "effects on" -- so ordinary players cannot disable or instrument the medical screen effects.
 */
public final class MedicalDebug {

    /**
     * Master developer switch, read once at class-load from the {@code wfmedical.debug} system property
     * (e.g. {@code -Dwfmedical.debug=true}). The dev {@code runClient} sets it automatically; shipped clients
     * do not, so this is {@code false} for players unless they deliberately pass the flag.
     */
    public static final boolean ENABLED = Boolean.getBoolean("wfmedical.debug");

    /**
     * Reusable single-pixel readback buffer (RGBA float). Never resized; access is render-thread only.
     */
    private static final FloatBuffer PIXEL = BufferUtils.createFloatBuffer(4);

    private static boolean screenEffects = true;
    private static boolean verbose;

    private MedicalDebug() {
    }

    // ------------------------------------------------------------------ master toggle

    /**
     * Whether the WF Medical screen effects should render. Fails OPEN: when the debug flag is off this is
     * always {@code true}, so effects can never be accidentally suppressed on a normal client.
     */
    public static boolean screenEffectsEnabled() {
        return !ENABLED || screenEffects;
    }

    /**
     * Flip the master gate; returns the new state for chat feedback. No-op (reports "on") without the flag.
     */
    public static boolean toggleScreenEffects() {
        if (!ENABLED) {
            return true;
        }
        screenEffects = !screenEffects;
        return screenEffects;
    }

    // ------------------------------------------------------------------ verbose logging

    /**
     * Whether verbose effect logging is active. Always {@code false} without the debug flag.
     */
    public static boolean verbose() {
        return ENABLED && verbose;
    }

    public static boolean toggleVerbose() {
        if (!ENABLED) {
            return false;
        }
        verbose = !verbose;
        return verbose;
    }

    // ------------------------------------------------------------------ diagnostics helpers

    /**
     * True while the local player's eye is submerged in water -- the condition that draws the vanilla blue
     * underwater overlay this bug corrupts. Null-safe so it can be called from any render hook.
     */
    public static boolean localPlayerEyeInWater() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isEyeInFluid(FluidTags.WATER);
    }

    /**
     * Reads a single RGBA pixel (normalised 0..1 floats) from the CENTRE of the given render target. Binds the
     * target for read first so the caller does not have to. Render-thread only; stalls the GPU, so it is only
     * ever called behind {@link #verbose()}. Returns {@code {r,g,b,a}}.
     */
    public static float[] sampleCenterPixel(RenderTarget target) {
        RenderSystem.assertOnRenderThread();
        target.bindWrite(false);
        int x = target.width / 2;
        int y = target.height / 2;
        PIXEL.clear();
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, PIXEL);
        return new float[] {PIXEL.get(0), PIXEL.get(1), PIXEL.get(2), PIXEL.get(3)};
    }

    /**
     * Formats an RGBA float array for logging, e.g. {@code RGBA(0.204, 0.318, 0.612, 0.100)}.
     */
    public static String fmt(float[] rgba) {
        if (rgba == null) {
            return "RGBA(?)";
        }
        return String.format("RGBA(%.3f, %.3f, %.3f, %.3f)", rgba[0], rgba[1], rgba[2], rgba[3]);
    }
}
