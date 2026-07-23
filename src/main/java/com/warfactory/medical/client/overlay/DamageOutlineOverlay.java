package com.warfactory.medical.client.overlay;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.screen.MedicalUIParts;
import com.warfactory.medical.config.MedicalClientConfig;
import com.warfactory.medical.config.MedicalClientConfig.HudAnchor;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLIENT-ONLY HUD overlay: when the local player takes damage, a limb-health-coloured silhouette of their body
 * flashes onto the screen, then holds and eases out. Damage is detected from the vanilla {@code hurtTime} rising
 * edge (any hit – combat, fall, fire...). Each limb is tinted by its current synced health (red→green via
 * {@link MedicalUIParts#limbColor}), so the outline "accounts for the current damage state" and keeps updating
 * while it is visible. Position, scale and enablement come from the per-client {@link MedicalClientConfig}.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DamageOutlineOverlay implements IGuiOverlay {

    public static final DamageOutlineOverlay INSTANCE = new DamageOutlineOverlay();

    /**
     * Base silhouette bounding box (px at scale 1); limb rects below are in this space.
     */
    private static final int MODEL_W = 40;
    private static final int MODEL_H = 64;

    /**
     * Limb rectangles {x1, y1, x2, y2} in the base model box, indexed by {@link LimbType#ordinal()}
     * (HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG). "Left" is anatomical, drawn on the viewer's left.
     */
    private static final int[][] LIMB_RECTS = {
            {14, 0, 26, 12},   // HEAD
            {12, 12, 28, 38},  // TORSO
            {4, 12, 12, 36},   // LEFT_ARM
            {28, 12, 36, 36},  // RIGHT_ARM
            {13, 38, 20, 64},  // LEFT_LEG
            {20, 38, 27, 64},  // RIGHT_LEG
    };

    private static final int OUTLINE_RGB = 0x0B0E12;

    /**
     * Hold fully visible for this long after the last hit, then ease out over {@link #FADE_MS}.
     */
    private static final long HOLD_MS = 2000L;
    private static final long FADE_MS = 1000L;

    /**
     * Wall-clock time (ms) of the most recent damage; both written (tick) and read (render) on the client thread.
     */
    private static long lastDamageMs = Long.MIN_VALUE;
    /**
     * Previous-tick {@code hurtTime}, to catch the rising edge of a fresh hit.
     */
    private static int prevHurtTime;

    private DamageOutlineOverlay() {
    }

    // ------------------------------------------------------------------ damage detection

    /**
     * Detect a fresh hit from the vanilla hurt-flash timer: a fresh hit snaps {@code hurtTime} up to
     * {@code hurtDuration}, so any increase over the previous tick is a new damage event.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenW, int screenH) {
        // Debug master gate for WF Medical screen effects (toggle with MedicalKeyMappings.TOGGLE_SCREEN_FX).
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

    /**
     * Two passes so the dark outline forms a continuous border behind the limb fills.
     */
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

    /**
     * 1 while holding, then a smooth ease down to 0 over the fade window; 0 once fully faded / never triggered.
     */
    private static float currentAlpha() {
        long elapsed = Util.getMillis() - lastDamageMs;
        if (elapsed < 0L || elapsed >= HOLD_MS + FADE_MS) {
            return 0.0F;
        }
        if (elapsed < HOLD_MS) {
            return 1.0F;
        }
        float t = (elapsed - HOLD_MS) / (float) FADE_MS; // 0..1 across the fade
        return 1.0F - t * t * (3.0F - 2.0F * t);        // 1 - smoothstep(t)
    }

    /**
     * Combine an RGB colour with a 0..1 alpha into ARGB (the RGB's own alpha byte is discarded).
     */
    private static int argb(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
