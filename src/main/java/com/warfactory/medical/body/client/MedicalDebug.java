package com.warfactory.medical.body.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public final class MedicalDebug {

    public static final boolean ENABLED = Boolean.getBoolean("wfmedical.debug");

    private static final FloatBuffer PIXEL = BufferUtils.createFloatBuffer(4);

    private static boolean screenEffects = true;
    private static boolean verbose;

    private MedicalDebug() {
    }


    public static boolean screenEffectsEnabled() {
        return !ENABLED || screenEffects;
    }

    public static boolean toggleScreenEffects() {
        if (!ENABLED) {
            return true;
        }
        screenEffects = !screenEffects;
        return screenEffects;
    }


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


    public static boolean localPlayerEyeInWater() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isEyeInFluid(FluidTags.WATER);
    }

    public static float[] sampleCenterPixel(RenderTarget target) {
        RenderSystem.assertOnRenderThread();
        target.bindWrite(false);
        int x = target.width / 2;
        int y = target.height / 2;
        PIXEL.clear();
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, PIXEL);
        return new float[] {PIXEL.get(0), PIXEL.get(1), PIXEL.get(2), PIXEL.get(3)};
    }

    public static String fmt(float[] rgba) {
        if (rgba == null) {
            return "RGBA(?)";
        }
        return String.format("RGBA(%.3f, %.3f, %.3f, %.3f)", rgba[0], rgba[1], rgba[2], rgba[3]);
    }
}
