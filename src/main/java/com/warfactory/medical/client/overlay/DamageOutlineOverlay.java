package com.warfactory.medical.client.overlay;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.screen.MedicalUIParts;
import com.warfactory.medical.config.MedicalClientConfig;
import com.warfactory.medical.config.MedicalClientConfig.HudAnchor;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = WFMedical.MOD_ID, value = Dist.CLIENT)
public final class DamageOutlineOverlay implements LayeredDraw.Layer {

    public static final DamageOutlineOverlay INSTANCE = new DamageOutlineOverlay();

    private static final int MODEL_W = 40;
    private static final int MODEL_H = 64;

    private static final int[][] LIMB_RECTS = {
            {14, 0, 26, 12},
            {12, 12, 28, 38},
            {4, 12, 12, 36},
            {28, 12, 36, 36},
            {13, 38, 20, 64},
            {20, 38, 27, 64},
    };

    private static final int OUTLINE_RGB = 0x0B0E12;

    private static final long HOLD_MS = 2000L;
    private static final long FADE_MS = 1000L;

    private static long lastDamageMs = Long.MIN_VALUE;
    private static int prevHurtTime;

    private DamageOutlineOverlay() {
    }


    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            prevHurtTime = 0;
            return;
        }
        int hurtTime = player.hurtTime;
        if (hurtTime > prevHurtTime) {
            lastDamageMs = Util.getMillis();
        }
        prevHurtTime = hurtTime;
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
        if (player == null || mc.level == null || player.isSpectator()) {
            return;
        }
        float alpha = currentAlpha();
        if (alpha <= 0.0F || !MedicalClientConfig.damageOutlineEnabled()) {
            return;
        }

        float scale = MedicalClientConfig.damageOutlineScale();
        int contentW = Math.round(MODEL_W * scale);
        int contentH = Math.round(MODEL_H * scale);
        HudAnchor anchor = MedicalClientConfig.damageOutlineAnchor();
        int originX = Math.round((screenW - contentW) * anchor.hx) + MedicalClientConfig.damageOutlineOffsetX();
        int originY = Math.round((screenH - contentH) * anchor.vy) + MedicalClientConfig.damageOutlineOffsetY();

        drawSilhouette(graphics, originX, originY, scale, alpha);
    }

    private static void drawSilhouette(GuiGraphics graphics, int ox, int oy, float scale, float alpha) {
        int outline = argb(OUTLINE_RGB, alpha * 0.85F);
        for (int[] r : LIMB_RECTS) {
            graphics.fill(ox + scaled(r[0], scale) - 1, oy + scaled(r[1], scale) - 1,
                    ox + scaled(r[2], scale) + 1, oy + scaled(r[3], scale) + 1, outline);
        }
        for (LimbType limb : LimbType.VALUES) {
            int[] r = LIMB_RECTS[limb.ordinal()];
            int fill = argb(MedicalUIParts.limbColor(MedicalUIParts.limbSummary(limb).healthPercent()), alpha);
            graphics.fill(ox + scaled(r[0], scale), oy + scaled(r[1], scale),
                    ox + scaled(r[2], scale), oy + scaled(r[3], scale), fill);
        }
    }

    private static int scaled(int value, float scale) {
        return Math.round(value * scale);
    }

    private static float currentAlpha() {
        long elapsed = Util.getMillis() - lastDamageMs;
        if (elapsed < 0L || elapsed >= HOLD_MS + FADE_MS) {
            return 0.0F;
        }
        if (elapsed < HOLD_MS) {
            return 1.0F;
        }
        float t = (elapsed - HOLD_MS) / (float) FADE_MS;
        return 1.0F - t * t * (3.0F - 2.0F * t);
    }

    private static int argb(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
