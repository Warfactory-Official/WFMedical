package com.warfactory.medical.client.overlay;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.warfactory.medical.client.screen.MedicalUIParts;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.item.MedicalItem;
import com.warfactory.medical.network.ActiveTreatmentPacket;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD overlay showing a labelled progress bar WHILE a medical action is in progress. Two sources are
 * supported, checked in this order:
 * <ol>
 *   <li>VANILLA use channel — the player is right-click-using a {@link MedicalItem}; progress is derived
 *       from {@code getUseItemRemainingTicks() / getUseDuration(stack)} and the label is the item name.</li>
 *   <li>SERVER active-treatment — {@link ClientMedicalCache#hasActiveTreatment()} is set; progress is
 *       derived from the {@link ActiveTreatmentPacket}'s {@code startGameTime}/{@code totalTicks} against
 *       the client's own level game time, and the label is a friendly action (+ limb) name.</li>
 * </ol>
 * When neither is active nothing is drawn. CLIENT-ONLY; only referenced from the {@link Dist#CLIENT}
 * scaffolding registration. {@link #render} guards all nullable state and never throws.
 */
@OnlyIn(Dist.CLIENT)
public final class ActionProgressOverlay implements IGuiOverlay {

    /**
     * The singleton overlay instance registered by the client scaffolding.
     */
    public static final IGuiOverlay INSTANCE = new ActionProgressOverlay();

    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 8;

    /**
     * Dark backdrop drawn behind the fill.
     */
    private static final ColorRectTexture BACKGROUND = new ColorRectTexture(0xC0101010);
    /**
     * Empty (dark teal) -> filled (bright teal) progress fill.
     */
    private static final ProgressTexture FILL = new ProgressTexture(
            new ColorRectTexture(0xFF10402F), new ColorRectTexture(0xFF33CC99))
            .setFillDirection(ProgressTexture.FillDirection.LEFT_TO_RIGHT);
    /**
     * Centered label above the bar.
     */
    private static final TextTexture LABEL = new TextTexture("")
            .setType(TextTexture.TextType.NORMAL)
            .setColor(0xFFFFFFFF)
            .setDropShadow(true);
    /**
     * Centered percentage overlaid on the bar.
     */
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
        String actionName = action == null ? "Treatment" : friendlyAction(action);
        if (limb == null) {
            return actionName;
        }
        return actionName + " (" + MedicalUIParts.limbName(limb).getString() + ")";
    }

    /**
     * Human-readable name for a treatment action (self-contained; no lang dependency).
     */
    private static String friendlyAction(TreatmentAction action) {
        return switch (action) {
            case REDUCE_BLEEDING -> "Stopping Bleeding";
            case SUTURE_WOUND -> "Suturing Wound";
            case STABILIZE_FRACTURE -> "Splinting Fracture";
            case RESTORE_BLOOD -> "Restoring Blood";
            case REDUCE_PAIN -> "Administering Painkiller";
            case HEAL_TRAUMA -> "Treating Wound";
            case TREAT_BURN -> "Treating Burn";
            case TREAT_RADIATION -> "Treating Radiation";
        };
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        float progress;
        String label;

        // (a) Vanilla right-click use channel takes priority.
        ItemStack useStack = player.getUseItem();
        if (player.isUsingItem() && useStack.getItem() instanceof MedicalItem medical) {
            int duration = medical.getUseDuration(useStack);
            int remaining = player.getUseItemRemainingTicks();
            progress = duration <= 0 ? 1.0F : 1.0F - (remaining / (float) duration);
            label = useStack.getHoverName().getString();
        } else if (ClientMedicalCache.hasActiveTreatment()) {
            // (b) Server-driven active treatment.
            ActiveTreatmentPacket a = ClientMedicalCache.activeTreatment();
            if (a == null || !a.active()) {
                return;
            }
            long elapsed = mc.level.getGameTime() - a.startGameTime();
            progress = a.totalTicks() <= 0 ? 1.0F : elapsed / (float) a.totalTicks();
            label = actionLabel(a.action(), a.limb());
        } else {
            return;
        }

        if (progress < 0.0F) {
            progress = 0.0F;
        } else if (progress > 1.0F) {
            progress = 1.0F;
        }

        int x = screenW / 2 - BAR_WIDTH / 2;
        int y = screenH - 60;

        // Label sits just above the bar.
        LABEL.updateText(label);
        LABEL.draw(graphics, -1, -1, x, y - 11, BAR_WIDTH, 9);

        BACKGROUND.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT);
        FILL.setProgress(progress);
        FILL.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT);

        PERCENT.updateText(Math.round(progress * 100.0F) + "%");
        PERCENT.draw(graphics, -1, -1, x, y, BAR_WIDTH, BAR_HEIGHT);
    }
}
