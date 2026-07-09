package com.warfactory.medical.client.overlay;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.warfactory.medical.client.UiText;
import com.warfactory.medical.client.screen.MedicalUIParts;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.network.ActiveTreatmentPacket;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;

/**
 * CLIENT-ONLY HUD overlay showing a labelled progress bar during a medical action. Progress is driven
 * exclusively by the server's {@code ActiveTreatmentPacket} (cached in {@code ClientMedicalCache}).
 * Nothing drawn when no treatment is active; all nullable state is guarded, no throws inside the render
 * hook.
 */
@OnlyIn(Dist.CLIENT)
public final class ActionProgressOverlay implements IGuiOverlay {

    public static final IGuiOverlay INSTANCE = new ActionProgressOverlay();

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

    /**
     * Build a friendly "<action> (<limb>)" label. Falls back gracefully when either part is null.
     */
    private static String actionLabel(TreatmentAction action, LimbType limb) {
        String actionName = action == null
                ? Component.translatable("gui.wfmedical.action.generic").getString()
                : friendlyAction(action);
        if (limb == null) {
            return actionName;
        }
        return actionName + " (" + MedicalUIParts.limbName(limb).getString() + ")";
    }

    /**
     * Localised progress label for a treatment action, resolved from {@code gui.wfmedical.action.<name>}.
     */
    private static String friendlyAction(TreatmentAction action) {
        return Component.translatable("gui.wfmedical.action." + action.name().toLowerCase(Locale.ROOT)).getString();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        if (!ClientMedicalCache.hasActiveTreatment()) {
            return;
        }

        // Server-driven active treatment.
        ActiveTreatmentPacket a = ClientMedicalCache.activeTreatment();
        if (a == null || !a.active()) {
            return;
        }

        long elapsed = mc.level.getGameTime() - a.startGameTime();
        float progress = a.totalTicks() <= 0 ? 1.0F : elapsed / (float) a.totalTicks();

        // Prefix label with the target's name when treating another entity (not self).
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

        if (progress < 0.0F) {
            progress = 0.0F;
        } else if (progress > 1.0F) {
            progress = 1.0F;
        }

        int x = screenW / 2 - BAR_WIDTH / 2;
        int y = screenH - 60;

        // Label sits just above the bar. Escape so a '%' in a (possibly modded) item name / label never
        // renders as LDLib's "Format error:" (see UiText).
        LABEL.updateText(UiText.escape(label));
        LABEL.draw(graphics, -1, -1, x, y - 11, BAR_WIDTH, 9);

        BACKGROUND.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT);
        FILL.setProgress(progress);
        FILL.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT);

        PERCENT.updateText(UiText.escape(Math.round(progress * 100.0F) + "%"));
        PERCENT.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT);
    }
}
