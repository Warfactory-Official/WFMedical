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
 * HUD overlay replacing the vanilla heart bar with a derived-health bar; the vanilla PLAYER_HEALTH overlay
 * is cancelled by {@code MedicalClientEvents} so this fills that slot. All texture instances are static
 * and reused; only {@link ProgressTexture#setProgress(double)} is mutated per frame.
 */
@OnlyIn(Dist.CLIENT)
public final class HealthBarOverlay implements IGuiOverlay {

    public static final IGuiOverlay INSTANCE = new HealthBarOverlay();

    /**
     * Mirrors the vanilla left HUD anchor (81px wide, 9px tall, red heart row).
     */
    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 9;

    private static final ColorRectTexture BACKGROUND = new ColorRectTexture(0xC0101010);
    private static final ProgressTexture HEALTH_FILL = new ProgressTexture(
            new ColorRectTexture(0xFF400000), new ColorRectTexture(0xFFDD2222))
            .setFillDirection(ProgressTexture.FillDirection.LEFT_TO_RIGHT);
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
