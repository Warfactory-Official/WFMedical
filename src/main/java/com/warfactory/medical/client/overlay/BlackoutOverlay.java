package com.warfactory.medical.client.overlay;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLIENT-ONLY screen effect: a full-screen BLACK overlay shown while the local player is UNCONSCIOUS
 * (blacked out from an injectable overdose). Gated purely on the server-authoritative
 * {@link ClientMedicalCache#stats()}.{@code blackout()} flag; this overlay never mutates medical state and
 * only ever reads synced client state.
 *
 * <p>The black-out does not snap in/out — a static client-side {@link #fade} value is eased toward
 * {@code 1.0} while {@code blackout} is true and toward {@code 0.0} otherwise, so consciousness fades to
 * black and comes back gradually (e.g. after a {@code NALOXONE} injection ends the blackout). The whole
 * screen is then filled with black at {@code alpha = fade} (capped just below fully opaque so it reads as a
 * true black-out without a hard 100% wall), and a faint centred "unconscious" hint is drawn once the fade
 * is deep enough to be legible.</p>
 *
 * <p>Cost is ~zero for a conscious player whose fade has settled: {@link #render} early-returns before
 * touching any draw call once {@code fade <= 0.001}. It guards every nullable ({@code mc.player},
 * {@code mc.level}) and never throws inside the render hook. Allocation-light: no per-frame object churn.</p>
 *
 * <p>This is intentionally NOT a desaturation pass (blood desaturation is a separate effect) — it is an
 * opaque black cover drawn ABOVE ALL other overlays so it hides the HUD while the player is out.</p>
 *
 * <p>SELF-REGISTERING: the nested {@link Registrar} subscribes to the MOD event bus on {@link Dist#CLIENT}
 * and registers {@link #INSTANCE} above all other overlays, mirroring {@link PainVignetteOverlay}.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class BlackoutOverlay implements IGuiOverlay {

    /** The singleton overlay instance; registered by {@link Registrar}. */
    public static final IGuiOverlay INSTANCE = new BlackoutOverlay();

    /** Overlay id used when registering with Forge. */
    public static final String OVERLAY_ID = "wfmedical_blackout";

    /** Per-frame easing factor toward the fade target; small so the transition is smooth, not instant. */
    private static final float FADE_STEP = 0.08F;
    /** Below this fade the overlay is invisible; early-return keeps conscious cost ~0. */
    private static final float FADE_EPSILON = 0.001F;
    /** Alpha ceiling: essentially fully black without a hard 100% wall. */
    private static final float MAX_FADE = 0.98F;
    /** Above this fade the centred "unconscious" hint is legible and gets drawn. */
    private static final float HINT_THRESHOLD = 0.85F;
    /** Faint hint text drawn near the centre while deeply blacked out. */
    private static final String HINT_TEXT = "...";

    /** Smoothed client-side fade toward the current blackout target; persists across frames. */
    private static float fade;

    private BlackoutOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        boolean blackout = ClientMedicalCache.stats().blackout();
        float target = blackout ? 1.0F : 0.0F;

        // Frame-rate-independent enough for a slow cover; ease toward the target and clamp.
        fade += (target - fade) * FADE_STEP;
        if (fade < 0.0F) {
            fade = 0.0F;
        } else if (fade > 1.0F) {
            fade = 1.0F;
        }
        if (fade <= FADE_EPSILON) {
            return;
        }

        float alpha = fade > MAX_FADE ? MAX_FADE : fade;
        int argb = ((int) (alpha * 255.0F) << 24); // black RGB, variable alpha
        graphics.fill(0, 0, screenW, screenH, argb);

        // A faint centred hint once we're deep enough for it to read against the black.
        if (fade >= HINT_THRESHOLD) {
            int textWidth = mc.font.width(HINT_TEXT);
            int tx = (screenW - textWidth) / 2;
            int ty = screenH / 2;
            graphics.drawString(mc.font, HINT_TEXT, tx, ty, 0x40FFFFFF, false);
        }
    }

    /**
     * MOD-bus registrar so the overlay self-registers without touching client scaffolding. Runs only on
     * {@link Dist#CLIENT}; the black cover is registered above all other overlays so it hides the HUD while
     * the player is unconscious.
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
