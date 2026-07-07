package com.warfactory.medical.client.screen;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.warfactory.medical.client.UiText;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.network.ClientMedicalCache;
import com.warfactory.medical.network.MedicalSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * CLIENT-ONLY quick medical interaction wheel. Ring geometry: button {@code i} of {@code n} sits at
 * {@code theta = i/n * 2*PI - PI/2} (item 0 straight up, clockwise), centre at
 * {@code (cx + RING_RADIUS*cos(theta), cy + RING_RADIUS*sin(theta))}; widget top-left is centre minus
 * BUTTON_HALF. Reads ClientMedicalCache and sends request packets; never mutates medical state.
 */
public final class RadialMenuUI {

    private static final int RING_RADIUS = 96;
    private static final int BUTTON_SIZE = 26;
    /**
     * Half BUTTON_SIZE; converts a ring centre point to the widget's top-left corner.
     */
    private static final int BUTTON_HALF = BUTTON_SIZE / 2;
    private static final int BUTTON_RADIUS = 6;
    private static final int BODY_WIDTH = 56;
    private static final int BODY_HEIGHT = 84;
    private static final int BACKDROP_COLOR = 0x90000000;
    private static final int BUTTON_BG_COLOR = 0xC0202020;
    private static final int BUTTON_HOVER_COLOR = 0xFFFFDD55;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private RadialMenuUI() {
    }

    /**
     * Ring button click requests treatment and closes the wheel; centre limb click re-targets without closing.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        // A fixed-size UI matching the whole screen: gui is centred, so its top-left is (0,0) and the centre
        // is (screenW/2, screenH/2). This keeps all widget coordinates in plain screen space.
        int cx = screenW / 2;
        int cy = screenH / 2;

        ModularUI ui = new ModularUI(screenW, screenH, IUIHolder.EMPTY, player);

        // Translucent backdrop that dims the world behind the wheel.
        ui.mainGroup.addWidget(new ImageWidget(0, 0, screenW, screenH,
                new ColorRectTexture(BACKDROP_COLOR)));

        // Centre: the shared, clickable body diagram for picking the target limb.
        ui.mainGroup.addWidget(MedicalUIParts.bodyDiagram(
                cx - BODY_WIDTH / 2, cy - BODY_HEIGHT / 2, BODY_WIDTH, BODY_HEIGHT));

        // Live vitals + selected-limb readout just below the body diagram.
        addVitals(ui, cx, cy + BODY_HEIGHT / 2 + 4);

        // "Remove Tourniquet" button (UI-driven removal for the currently selected limb).
        addRemoveTourniquetButton(ui, cx, cy + BODY_HEIGHT / 2 + 4 + 4 * 10 + 6);

        List<ItemStack> items = MedicalUIParts.availableMedicalItems();
        if (items.isEmpty()) {
            // Graceful empty state: a centred hint above the diagram.
            LabelWidget hint = new LabelWidget(cx - 70, cy - BODY_HEIGHT / 2 - 18,
                    Component.translatable("gui.wfmedical.radial.no_items").getString());
            hint.setColor(TEXT_COLOR);
            hint.setClientSideWidget();
            ui.mainGroup.addWidget(hint);
        } else {
            addRing(ui, items, cx, cy);
        }

        ClientUIOpener.openClientUI(ui);
    }

    private static void addRing(ModularUI ui, List<ItemStack> items, int cx, int cy) {
        int n = items.size();
        for (int i = 0; i < n; i++) {
            ItemStack stack = items.get(i);
            double theta = (i / (double) n) * (Math.PI * 2.0) - (Math.PI / 2.0);
            int centreX = cx + (int) Math.round(RING_RADIUS * Math.cos(theta));
            int centreY = cy + (int) Math.round(RING_RADIUS * Math.sin(theta));
            int btnX = centreX - BUTTON_HALF;
            int btnY = centreY - BUTTON_HALF;

            addItemButton(ui, stack, btnX, btnY, centreX);
        }
    }

    private static void addItemButton(ModularUI ui, ItemStack stack, int btnX, int btnY, int centreX) {
        GuiTextureGroup face = new GuiTextureGroup(
                new ColorRectTexture(BUTTON_BG_COLOR).setRadius(BUTTON_RADIUS),
                new ItemStackTexture(stack));

        ButtonWidget button = new ButtonWidget(btnX, btnY, BUTTON_SIZE, BUTTON_SIZE, face,
                (ClickData cd) -> {
                    MedicalUIParts.requestAction(stack, MedicalUIParts.selectedLimb());
                    Minecraft.getInstance().setScreen(null);
                });
        button.setHoverTexture(new ColorBorderTexture(2, BUTTON_HOVER_COLOR).setRadius(BUTTON_RADIUS));
        button.setHoverTooltips(itemTooltip(stack));
        button.setClientSideWidget();
        ui.mainGroup.addWidget(button);

        // Short name label centred under the button. Escape so a '%' in a (possibly modded) item name
        // doesn't render as LDLib's "Format error:" (see UiText).
        String shortName = shortName(stack);
        int labelX = centreX - shortName.length() * 3;
        LabelWidget label = new LabelWidget(labelX, btnY + BUTTON_SIZE + 1, UiText.escape(shortName));
        label.setColor(TEXT_COLOR);
        label.setClientSideWidget();
        ui.mainGroup.addWidget(label);
    }

    /**
     * Server no-ops if the selected limb has no tourniquet, so the button can always be shown.
     */
    private static void addRemoveTourniquetButton(ModularUI ui, int cx, int y) {
        int w = 110;
        int h = 14;
        int x = cx - w / 2;
        ButtonWidget button = new ButtonWidget(x, y, w, h,
                new GuiTextureGroup(new ColorRectTexture(BUTTON_BG_COLOR).setRadius(BUTTON_RADIUS)),
                (ClickData cd) -> {
                    MedicalUIParts.requestRemoveTourniquet(MedicalUIParts.selectedLimb());
                    Minecraft.getInstance().setScreen(null);
                });
        button.setHoverTexture(new ColorBorderTexture(2, BUTTON_HOVER_COLOR).setRadius(BUTTON_RADIUS));
        button.setClientSideWidget();
        ui.mainGroup.addWidget(button);

        LabelWidget label = new LabelWidget(cx - 48, y + 3,
                Component.translatable("gui.wfmedical.tourniquet.remove").getString());
        label.setColor(TEXT_COLOR);
        label.setClientSideWidget();
        ui.mainGroup.addWidget(label);
    }

    private static void addVitals(ModularUI ui, int cx, int topY) {
        int lineH = 10;

        LabelWidget limbLabel = new LabelWidget(cx - 50, topY, () -> {
            LimbType sel = MedicalUIParts.selectedLimb();
            if (sel == null) {
                return Component.translatable("gui.wfmedical.radial.no_limb").getString();
            }
            return MedicalUIParts.limbName(sel).getString();
        });
        limbLabel.setColor(TEXT_COLOR);
        limbLabel.setClientSideWidget();
        ui.mainGroup.addWidget(limbLabel);

        LabelWidget healthLabel = new LabelWidget(cx - 50, topY + lineH, () -> {
            DerivedStats s = MedicalUIParts.stats();
            return "HP " + Math.round(s.effectiveCurrentHealth()) + "/" + Math.round(s.effectiveMaxHealth());
        });
        healthLabel.setColor(TEXT_COLOR);
        healthLabel.setClientSideWidget();
        ui.mainGroup.addWidget(healthLabel);

        LabelWidget bloodLabel = new LabelWidget(cx - 50, topY + 2 * lineH,
                () -> UiText.escape("Blood " + bloodPercent() + "%"));
        bloodLabel.setColor(TEXT_COLOR);
        bloodLabel.setClientSideWidget();
        ui.mainGroup.addWidget(bloodLabel);

        LabelWidget painLabel = new LabelWidget(cx - 50, topY + 3 * lineH,
                () -> UiText.escape("Pain " + Math.round(MedicalUIParts.stats().totalPain() * 100.0F) + "%"));
        painLabel.setColor(TEXT_COLOR);
        painLabel.setClientSideWidget();
        ui.mainGroup.addWidget(painLabel);
    }

    private static int bloodPercent() {
        MedicalSyncPacket snap = ClientMedicalCache.get();
        if (snap == null || snap.maxBloodMl() <= 0.0D) {
            return 100;
        }
        double pct = snap.bloodMl() / snap.maxBloodMl() * 100.0D;
        if (pct < 0.0D) {
            pct = 0.0D;
        } else if (pct > 100.0D) {
            pct = 100.0D;
        }
        return (int) Math.round(pct);
    }

    private static List<Component> itemTooltip(ItemStack stack) {
        return stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL);
    }

    private static String shortName(ItemStack stack) {
        String name = stack.getHoverName().getString();
        if (name.length() > 12) {
            return name.substring(0, 11) + "..";
        }
        return name;
    }
}
