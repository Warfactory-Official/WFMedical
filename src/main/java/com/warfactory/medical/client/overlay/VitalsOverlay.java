package com.warfactory.medical.client.overlay;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.network.ClientMedicalCache;
import com.warfactory.medical.network.MedicalSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Compact corner overlay showing BLOOD (blue) and PAIN (red) indicators, drawn only when there is
 * something to show (blood below full or pain above zero). Purely presentational: reads the synced
 * {@link ClientMedicalCache} snapshot, never mutates state.
 *
 * <p>Both quantities are already normalized: the blood fraction is {@code bloodMl / maxBloodMl}, and
 * {@link DerivedStats#totalPain()} is a saturated 0..1 value (the physiology computes
 * {@code painSum / (painSum + 1)} and then applies painkiller suppression), so no extra scaling guesswork
 * is needed.</p>
 *
 * <p>CLIENT-ONLY. This overlay is NOT part of the scaffolding's mandatory registration contract (only the
 * health and action-progress overlays are). It exposes {@link #INSTANCE} so a later agent can register it
 * (e.g. {@code registerBelowAll} / {@code registerAbove(...)}) if the compact vitals are wanted; if left
 * unregistered it simply never renders. {@link #render} guards all nullable state and never throws.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class VitalsOverlay implements IGuiOverlay {

    /** The singleton overlay instance (register it from client scaffolding to enable it). */
    public static final IGuiOverlay INSTANCE = new VitalsOverlay();

    private static final int BAR_WIDTH = 60;
    private static final int BAR_HEIGHT = 6;
    /** Top-left inset. */
    private static final int MARGIN_X = 4;
    private static final int MARGIN_Y = 4;
    /** Width reserved for the leading label so bars line up. */
    private static final int LABEL_WIDTH = 34;

    private static final ColorRectTexture BACKGROUND = new ColorRectTexture(0xC0101010);
    private static final ProgressTexture BLOOD_FILL = new ProgressTexture(
            new ColorRectTexture(0xFF201038), new ColorRectTexture(0xFF3366CC))
            .setFillDirection(ProgressTexture.FillDirection.LEFT_TO_RIGHT);
    private static final ProgressTexture PAIN_FILL = new ProgressTexture(
            new ColorRectTexture(0xFF200000), new ColorRectTexture(0xFFCC3030))
            .setFillDirection(ProgressTexture.FillDirection.LEFT_TO_RIGHT);
    private static final TextTexture BLOOD_LABEL = new TextTexture("Blood")
            .setType(TextTexture.TextType.LEFT).setColor(0xFF88AAFF).setDropShadow(true);
    private static final TextTexture PAIN_LABEL = new TextTexture("Pain")
            .setType(TextTexture.TextType.LEFT).setColor(0xFFFF8888).setDropShadow(true);

    private VitalsOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenW, int screenH) {
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
            BLOOD_LABEL.draw(graphics, -1, -1, MARGIN_X, y, LABEL_WIDTH, BAR_HEIGHT);
            BACKGROUND.draw(graphics, -1, -1, barX, y, BAR_WIDTH, BAR_HEIGHT);
            BLOOD_FILL.setProgress(bloodFraction);
            BLOOD_FILL.draw(graphics, -1, -1, barX, y, BAR_WIDTH, BAR_HEIGHT);
            y += BAR_HEIGHT + 2;
        }
        if (showPain) {
            PAIN_LABEL.draw(graphics, -1, -1, MARGIN_X, y, LABEL_WIDTH, BAR_HEIGHT);
            BACKGROUND.draw(graphics, -1, -1, barX, y, BAR_WIDTH, BAR_HEIGHT);
            PAIN_FILL.setProgress(pain);
            PAIN_FILL.draw(graphics, -1, -1, barX, y, BAR_WIDTH, BAR_HEIGHT);
        }
    }
}
