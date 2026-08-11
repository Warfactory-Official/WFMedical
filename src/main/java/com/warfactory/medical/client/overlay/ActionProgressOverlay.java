package com.warfactory.medical.client.overlay;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.warfactory.medical.client.UiText;
import com.warfactory.medical.client.screen.MedicalUIParts;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.network.ActiveTreatmentPacket;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class ActionProgressOverlay implements LayeredDraw.Layer {

    public static final LayeredDraw.Layer INSTANCE = new ActionProgressOverlay();

    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 8;

    private static final ColorRectTexture BACKGROUND = new ColorRectTexture(0xC0101010);
    private static final ProgressTexture FILL = new ProgressTexture(
            new ColorRectTexture(0xFF10402F), new ColorRectTexture(0xFF33CC99))
            .setFillDirection(ProgressTexture.FillDirection.LEFT_TO_RIGHT);
    private static final TextTexture LABEL = new TextTexture("")
            .setType(TextTexture.TextType.NORMAL)
            .setColor(0xFFFFFFFF)
            .setDropShadow(true);
    private static final TextTexture PERCENT = new TextTexture("")
            .setType(TextTexture.TextType.NORMAL)
            .setColor(0xFFFFFFFF)
            .setDropShadow(true);

    private ActionProgressOverlay() {
    }

    private static String actionLabel(TreatmentAction action, LimbType limb) {
        String actionName = action == null
                ? Component.translatable("gui.wfmedical.action.generic").getString()
                : friendlyAction(action);
        if (limb == null) {
            return actionName;
        }
        return actionName + " (" + MedicalUIParts.limbName(limb).getString() + ")";
    }

    private static String friendlyAction(TreatmentAction action) {
        return Component.translatable("gui.wfmedical.action." + action.name().toLowerCase(Locale.ROOT)).getString();
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        drawBar(graphics, screenW / 2 - BAR_WIDTH / 2, screenH - 60, BAR_WIDTH, partialTick);
    }

    public static boolean drawBar(GuiGraphics graphics, int x, int barY, int width, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !ClientMedicalCache.hasActiveTreatment()) {
            return false;
        }
        ActiveTreatmentPacket a = ClientMedicalCache.activeTreatment();
        if (a == null || !a.active()) {
            return false;
        }

        long elapsed = mc.level.getGameTime() - a.startGameTime();
        float progress = a.totalTicks() <= 0 ? 1.0F : elapsed / (float) a.totalTicks();
        if (progress < 0.0F) {
            progress = 0.0F;
        } else if (progress > 1.0F) {
            progress = 1.0F;
        }

        String label;
        int targetId = a.targetEntityId();
        if (targetId >= 0) {
            Entity target = mc.level.getEntity(targetId);
            if (target != null && target != mc.player) {
                label = target.getName().getString() + " -> " + actionLabel(a.action(), a.limb());
            } else {
                label = actionLabel(a.action(), a.limb());
            }
        } else {
            label = actionLabel(a.action(), a.limb());
        }

        LABEL.updateText(UiText.escape(label));
        LABEL.draw(graphics, -1, -1, x, barY - 11, width, 9, partialTick);

        BACKGROUND.draw(graphics, -1, -1, x, barY, width, BAR_HEIGHT, partialTick);
        FILL.setProgress(progress);
        FILL.draw(graphics, -1, -1, x, barY, width, BAR_HEIGHT, partialTick);

        PERCENT.updateText(UiText.escape(Math.round(progress * 100.0F) + "%"));
        PERCENT.draw(graphics, -1, -1, x, barY, width, BAR_HEIGHT, partialTick);
        return true;
    }
}
