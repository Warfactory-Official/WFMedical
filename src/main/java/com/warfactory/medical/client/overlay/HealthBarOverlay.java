package com.warfactory.medical.client.overlay;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.warfactory.medical.client.screen.MedicalUIParts;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD overlay that REPLACES the vanilla heart bar with a single derived-health bar showing
 * {@code <health>/<maxHealth>} (already reflecting the server's max-health attribute modifier). The
 * vanilla {@code PLAYER_HEALTH} overlay is cancelled by {@code MedicalClientEvents}, so this fills that
 * slot.
 *
 * <p>CLIENT-ONLY: only referenced from the {@link Dist#CLIENT} scaffolding registration, so a dedicated
 * server never classloads it. All texture instances are static and reused; only mutable per-frame call is
 * {@link ProgressTexture#setProgress(double)} before drawing. The {@link #render} method never throws.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HealthBarOverlay implements IGuiOverlay {

    /**
     * The singleton overlay instance registered by the client scaffolding.
     */
    public static final IGuiOverlay INSTANCE = new HealthBarOverlay();

    /**
     * Bar geometry — mirrors the vanilla left HUD anchor (81px wide, 9px tall, red heart row).
     */
    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 9;

    /**
     * Dark backdrop drawn behind the fill.
     */
    private static final ColorRectTexture BACKGROUND = new ColorRectTexture(0xC0101010);
    /**
     * Empty (dark red) -> filled (bright red) health fill.
     */
    private static final ProgressTexture HEALTH_FILL = new ProgressTexture(
            new ColorRectTexture(0xFF400000), new ColorRectTexture(0xFFDD2222))
            .setFillDirection(ProgressTexture.FillDirection.LEFT_TO_RIGHT);
    /**
     * Centered readout, tinted by state; re-tinted each frame before draw.
     */
    private static final TextTexture LABEL = new TextTexture("")
            .setType(TextTexture.TextType.NORMAL)
            .setDropShadow(true);

    private HealthBarOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        // Only when survival-like combat HUD is active (matches the vanilla heart-bar visibility rules).
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        if (!gui.shouldDrawSurvivalElements()) {
            return;
        }

        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float fraction = maxHealth <= 0.0F ? 0.0F : health / maxHealth;
        if (fraction < 0.0F) {
            fraction = 0.0F;
        } else if (fraction > 1.0F) {
            fraction = 1.0F;
        }

        int x = screenW / 2 - 91;
        int y = screenH - 39;

        BACKGROUND.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT);
        HEALTH_FILL.setProgress(fraction);
        HEALTH_FILL.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT);

        int color = MedicalUIParts.stateColor(ClientMedicalCache.state());
        LABEL.setColor(color);
        LABEL.updateText(Math.round(health) + "/" + Math.round(maxHealth));
        // TextTexture centers within the given rectangle.
        LABEL.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT);
    }
}
