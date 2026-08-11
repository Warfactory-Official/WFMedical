package com.warfactory.medical.client.overlay;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.warfactory.medical.client.screen.MedicalUIParts;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HealthBarOverlay implements LayeredDraw.Layer {

    public static final LayeredDraw.Layer INSTANCE = new HealthBarOverlay();

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
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        // ForgeGui.shouldDrawSurvivalElements() is gone; this is its body.
        if (!(mc.getCameraEntity() instanceof net.minecraft.world.entity.player.Player)) {
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
        int y = screenH - mc.gui.leftHeight;

        BACKGROUND.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT, partialTick);
        HEALTH_FILL.setProgress(fraction);
        HEALTH_FILL.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT, partialTick);

        int color = MedicalUIParts.stateColor(ClientMedicalCache.state());
        LABEL.setColor(color);
        LABEL.updateText(Math.round(health) + "/" + Math.round(maxHealth));
        LABEL.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT, partialTick);

        // Mirror vanilla renderHealth's leftHeight contract so the next left-stack overlay
        // (armor) renders above this bar instead of on top of it.
        mc.gui.leftHeight += 10;
    }
}
