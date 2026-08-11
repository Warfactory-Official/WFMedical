package com.warfactory.medical.client.overlay;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.network.ClientMedicalCache;
import com.warfactory.medical.network.MedicalSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class VitalsOverlay implements LayeredDraw.Layer {

    public static final LayeredDraw.Layer INSTANCE = new VitalsOverlay();

    private static final int BAR_WIDTH = 60;
    private static final int BAR_HEIGHT = 6;
    private static final int MARGIN_X = 4;
    private static final int MARGIN_Y = 4;
    private static final int LABEL_WIDTH = 34;

    private static final ColorRectTexture BACKGROUND = new ColorRectTexture(0xC0101010);
    private static final ProgressTexture BLOOD_FILL = new ProgressTexture(
            new ColorRectTexture(0xFF201038), new ColorRectTexture(0xFF3366CC))
            .setFillDirection(ProgressTexture.FillDirection.LEFT_TO_RIGHT);
    private static final ProgressTexture PAIN_FILL = new ProgressTexture(
            new ColorRectTexture(0xFF200000), new ColorRectTexture(0xFFCC3030))
            .setFillDirection(ProgressTexture.FillDirection.LEFT_TO_RIGHT);
    private static final TextTexture BLOOD_LABEL = new TextTexture("")
            .setType(TextTexture.TextType.LEFT).setColor(0xFF88AAFF).setDropShadow(true);
    private static final TextTexture PAIN_LABEL = new TextTexture("")
            .setType(TextTexture.TextType.LEFT).setColor(0xFFFF8888).setDropShadow(true);

    private VitalsOverlay() {
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

        MedicalSyncPacket snap = ClientMedicalCache.get();
        double bloodMl = snap == null ? 0.0D : snap.bloodMl();
        double maxBloodMl = snap == null ? 0.0D : snap.maxBloodMl();
        float bloodFraction = maxBloodMl <= 0.0D ? 1.0F : (float) (bloodMl / maxBloodMl);
        if (bloodFraction < 0.0F) {
            bloodFraction = 0.0F;
        } else if (bloodFraction > 1.0F) {
            bloodFraction = 1.0F;
        }

        DerivedStats stats = ClientMedicalCache.stats();
        float pain = stats.totalPain();
        if (pain < 0.0F) {
            pain = 0.0F;
        } else if (pain > 1.0F) {
            pain = 1.0F;
        }

        boolean showBlood = bloodFraction < 0.999F;
        boolean showPain = pain > 0.001F;
        if (!showBlood && !showPain) {
            return;
        }

        int barX = MARGIN_X + LABEL_WIDTH;
        int y = MARGIN_Y;
        if (showBlood) {
            BLOOD_LABEL.updateText(Component.translatable("gui.wfmedical.blood").getString());
            BLOOD_LABEL.draw(graphics, -1, -1, MARGIN_X, y, LABEL_WIDTH, BAR_HEIGHT, partialTick);
            BACKGROUND.draw(graphics, -1, -1, barX, y, BAR_WIDTH, BAR_HEIGHT, partialTick);
            BLOOD_FILL.setProgress(bloodFraction);
            BLOOD_FILL.draw(graphics, -1, -1, barX, y, BAR_WIDTH, BAR_HEIGHT, partialTick);
            y += BAR_HEIGHT + 2;
        }
        if (showPain) {
            PAIN_LABEL.updateText(Component.translatable("gui.wfmedical.pain").getString());
            PAIN_LABEL.draw(graphics, -1, -1, MARGIN_X, y, LABEL_WIDTH, BAR_HEIGHT, partialTick);
            BACKGROUND.draw(graphics, -1, -1, barX, y, BAR_WIDTH, BAR_HEIGHT, partialTick);
            PAIN_FILL.setProgress(pain);
            PAIN_FILL.draw(graphics, -1, -1, barX, y, BAR_WIDTH, BAR_HEIGHT, partialTick);
        }
    }
}
