package com.warfactory.medical.client.screen;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import com.warfactory.medical.client.ClientTourniquetTracker;
import com.warfactory.medical.client.UiText;
import com.warfactory.medical.client.overlay.ActionProgressOverlay;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.item.MedicalItem;
import com.warfactory.medical.item.ModItems;
import com.warfactory.medical.network.ClientMedicalCache;
import com.warfactory.medical.network.MedicalSyncPacket;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * CLIENT-ONLY medical interaction screen, built entirely in code. The left side is split into two vertically
 * stacked grids that both re-key on the selected limb and on state changes:
 *
 * <ul>
 *     <li><b>EXAMINATION</b> — a grid of the selected limb's wounds as solid colour squares, each with a tooltip
 *         describing the injury and which treatments address it;</li>
 *     <li><b>TREATMENT</b> — a grid of radial-menu-styled icon buttons, one per available medical item, applied
 *         to the selected limb. When the selected (arm/leg) limb wears a tourniquet, a RED tourniquet button
 *         takes the first cell and REMOVES it instead (and the normal apply button is hidden).</li>
 * </ul>
 *
 * <p>The centre is a health-tinted, selectable body silhouette with a live status readout; the right column is a
 * live vitals overview. Both grids are {@link RefreshingGroup}s that rebuild whenever their signature (selected
 * limb + injuries / tourniquet state / carried items) changes, so switching limb or a physiology update refreshes
 * them without reopening.</p>
 *
 * <p>Reads the synced {@link ClientMedicalCache} snapshot and sends request packets only; it never mutates
 * medical state. The whole root is marked {@code setClientSideWidget()} so supplier-driven widgets re-read the
 * cache each tick (required for client-only LDLib UIs).</p>
 */
public final class MedInteractionScreen {

    // ------------------------------------------------------------------ root (px)
    private static final int ROOT_W = 453;
    private static final int ROOT_H = 152;
    private static final int ROOT_BG = 0x44222222;

    // ------------------------------------------------------------------ left column (EXAMINATION / TREATMENT)
    private static final int LEFT_X = 12;

    private static final int WOUND_Y = 17;
    private static final int WOUND_CELL = 24;
    private static final int WOUND_GAP = 4;

    private static final int TREAT_LABEL_Y = 45;
    private static final int GRID_Y = 57;
    private static final int GRID_CELL = 26;
    private static final int GRID_GAP = 5;
    private static final int GRID_COLS = 6;
    private static final int GRID_RADIUS = 6;
    private static final int GRID_BG_COLOR = 0xC0202020;
    private static final int GRID_HOVER_COLOR = 0xFFFFDD55;
    /**
     * Red face for the tourniquet-removal button (it stands out from the neutral apply buttons).
     */
    private static final int TQ_REMOVE_BG_COLOR = 0xD0B02020;

    // ------------------------------------------------------------------ body silhouette tiles
    private record LimbTile(LimbType limb, int x, int y, int w, int h) {
    }

    private static final List<LimbTile> BODY_TILES = List.of(
            new LimbTile(LimbType.HEAD, 216, 22, 20, 20),
            new LimbTile(LimbType.LEFT_ARM, 204, 45, 10, 34),
            new LimbTile(LimbType.TORSO, 216, 45, 20, 33),
            new LimbTile(LimbType.RIGHT_ARM, 238, 45, 10, 33),
            new LimbTile(LimbType.LEFT_LEG, 216, 81, 9, 38),
            new LimbTile(LimbType.RIGHT_LEG, 227, 81, 9, 38));

    // ------------------------------------------------------------------ status readout
    private static final int STATUS_CENTER_X = 226;
    private static final int STATUS_Y = 124;

    // ------------------------------------------------------------------ OVERVIEW column (right)
    private static final int OVERVIEW_X = 302;
    private static final int OVERVIEW_Y = 22;
    private static final int OVERVIEW_LINE_H = 12;

    private MedInteractionScreen() {
    }

    /**
     * Build, wire and open the interaction screen.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        WidgetGroup root = new WidgetGroup(0, 0, ROOT_W, ROOT_H);
        root.setBackground(new ColorRectTexture(ROOT_BG));

        addHeaders(root);
        addBodyDiagram(root);
        addStatusReadout(root);
        addExaminationGrid(root);
        addTreatmentGrid(root);
        addOverview(root);

        // Every live widget re-reads its supplier only when marked client-side (client-only UI).
        root.setClientSideWidget();

        ModularUI ui = new ModularUI(root, IUIHolder.EMPTY, player);
        ClientUIOpener.openClientUI(ui);
    }

    // ------------------------------------------------------------------ headers

    private static void addHeaders(WidgetGroup root) {
        root.addWidget(new LabelWidget(LEFT_X, 5, "EXAMINATION"));
        root.addWidget(new LabelWidget(LEFT_X, TREAT_LABEL_Y, "TREATMENT"));
        root.addWidget(new LabelWidget(208, 5, "STATUS"));
        root.addWidget(new LabelWidget(340, 5, "OVERVIEW"));
    }

    // ------------------------------------------------------------------ body diagram

    /**
     * Six health-tinted, selectable limb tiles laid out as a body silhouette (fill + selection border + click +
     * live tooltip each, via {@link MedicalUIParts#addLimbTile}).
     */
    private static void addBodyDiagram(WidgetGroup root) {
        for (LimbTile tile : BODY_TILES) {
            MedicalUIParts.addLimbTile(root, tile.limb(), tile.x(), tile.y(), tile.w(), tile.h());
        }
    }

    // ------------------------------------------------------------------ EXAMINATION (wounds grid)

    private static void addExaminationGrid(WidgetGroup root) {
        root.addWidget(new RefreshingGroup(LEFT_X, WOUND_Y, 185, 30,
                MedInteractionScreen::examinationSignature, MedInteractionScreen::buildWounds));
    }

    /**
     * Re-key the wounds grid on the selected limb and its injury values.
     */
    private static Object examinationSignature() {
        LimbType limb = MedicalUIParts.selectedLimb();
        if (limb == null) {
            return "none";
        }
        LimbSummary s = MedicalUIParts.limbSummary(limb);
        return limb + "|" + s.healthPercent() + "|" + s.bleeding() + "|" + s.pain() + "|" + s.fracture();
    }

    /**
     * One solid colour square per active wound on the selected limb; empty/absent states show a hint label.
     */
    private static void buildWounds(WidgetGroup group) {
        LimbType limb = MedicalUIParts.selectedLimb();
        if (limb == null) {
            group.addWidget(new LabelWidget(0, 8, Component.translatable("gui.wfmedical.wound.no_limb").getString()));
            return;
        }
        LimbSummary s = MedicalUIParts.limbSummary(limb);
        int col = 0;
        for (Wound wound : Wound.VALUES) {
            if (!wound.present(s)) {
                continue;
            }
            int x = col * (WOUND_CELL + WOUND_GAP);
            ImageWidget square = new ImageWidget(x, 0, WOUND_CELL, WOUND_CELL,
                    new ColorRectTexture(wound.color).setRadius(3));
            square.setHoverTooltips(wound.tooltip(s));
            group.addWidget(square);
            col++;
        }
        if (col == 0) {
            group.addWidget(new LabelWidget(0, 8, Component.translatable("gui.wfmedical.wound.none").getString()));
        }
    }

    // ------------------------------------------------------------------ TREATMENT (item grid)

    private static void addTreatmentGrid(WidgetGroup root) {
        root.addWidget(new RefreshingGroup(LEFT_X, GRID_Y, 185, 95,
                MedInteractionScreen::treatmentSignature, MedInteractionScreen::buildTreatments));
    }

    /**
     * Re-key the treatment grid on whether a treatment is running (which replaces the whole grid with the
     * progress bar), the selected limb, its tourniquet state and the carried medical items.
     */
    private static Object treatmentSignature() {
        LimbType limb = MedicalUIParts.selectedLimb();
        StringBuilder sb = new StringBuilder();
        sb.append(ClientMedicalCache.hasActiveTreatment()).append('|')
                .append(limb).append('|').append(tourniquetApplied(limb)).append('|');
        for (ItemStack stack : MedicalUIParts.availableMedicalItems()) {
            sb.append(stack.getItem().getDescriptionId()).append(',');
        }
        return sb.toString();
    }

    /**
     * A radial-menu-styled icon button per available medical item. When the selected arm/leg wears a tourniquet,
     * the first cell is a RED remove-tourniquet button and the normal tourniquet apply button is hidden. While a
     * treatment is running the whole grid is replaced by the live progress bar + an interrupt button, so no other
     * treatment can be started until it finishes or is cancelled.
     */
    private static void buildTreatments(WidgetGroup group) {
        if (ClientMedicalCache.hasActiveTreatment()) {
            buildActiveTreatment(group);
            return;
        }

        LimbType limb = MedicalUIParts.selectedLimb();
        boolean tqApplied = tourniquetApplied(limb);

        int idx = 0;
        if (tqApplied) {
            addTourniquetRemoveButton(group, cellX(idx), cellY(idx));
            idx++;
        }
        for (ItemStack stack : MedicalUIParts.availableMedicalItems()) {
            if (tqApplied && isTourniquetItem(stack)) {
                continue; // its slot is the red remove button; can't apply a second tourniquet
            }
            addTreatmentButton(group, stack, cellX(idx), cellY(idx));
            idx++;
        }
        if (idx == 0) {
            group.addWidget(new LabelWidget(0, 8, Component.translatable("gui.wfmedical.radial.no_items").getString()));
        }
    }

    private static int cellX(int idx) {
        return (idx % GRID_COLS) * (GRID_CELL + GRID_GAP);
    }

    private static int cellY(int idx) {
        return (idx / GRID_COLS) * (GRID_CELL + GRID_GAP);
    }

    /**
     * Active-treatment view: the same progress bar as the HUD overlay (drawn in-screen since the HUD is hidden
     * behind the menu) plus an interrupt button that cancels the running treatment.
     */
    private static void buildActiveTreatment(WidgetGroup group) {
        group.addWidget(new ProgressWidget(0, 0, 165, 20));
        String cancel = Component.translatable("gui.wfmedical.treat.interrupt").getString();
        group.addWidget(new ButtonWidget(43, 27, 80, 16,
                new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON, new TextTexture(cancel)),
                (ClickData cd) -> MedicalUIParts.requestCancelTreatment()));
    }

    /**
     * One treatment button: rounded background + item icon, hover-border highlight, item tooltip (name first).
     * Applies the item to the currently selected limb; leaves the menu open.
     */
    private static void addTreatmentButton(WidgetGroup group, ItemStack stack, int x, int y) {
        GuiTextureGroup face = new GuiTextureGroup(
                new ColorRectTexture(GRID_BG_COLOR).setRadius(GRID_RADIUS),
                new ItemStackTexture(stack));
        ButtonWidget button = new ButtonWidget(x, y, GRID_CELL, GRID_CELL, face,
                (ClickData cd) -> MedicalUIParts.requestAction(stack, MedicalUIParts.selectedLimb()));
        button.setHoverTexture(new ColorBorderTexture(2, GRID_HOVER_COLOR).setRadius(GRID_RADIUS));
        button.setHoverTooltips(stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL));
        group.addWidget(button);
    }

    /**
     * Red tourniquet button (tourniquet icon on a red face) that REMOVES the tourniquet from the selected limb.
     * The server rolls the configured recovery chance to return the item.
     */
    private static void addTourniquetRemoveButton(WidgetGroup group, int x, int y) {
        GuiTextureGroup face = new GuiTextureGroup(
                new ColorRectTexture(TQ_REMOVE_BG_COLOR).setRadius(GRID_RADIUS),
                new ItemStackTexture(new ItemStack(ModItems.TOURNIQUET.get())));
        ButtonWidget button = new ButtonWidget(x, y, GRID_CELL, GRID_CELL, face,
                (ClickData cd) -> MedicalUIParts.requestRemoveTourniquet(MedicalUIParts.selectedLimb()));
        button.setHoverTexture(new ColorBorderTexture(2, GRID_HOVER_COLOR).setRadius(GRID_RADIUS));
        button.setHoverTooltips(List.of(Component.translatable("gui.wfmedical.tourniquet.remove")));
        group.addWidget(button);
    }

    /**
     * Whether the selected limb is an arm/leg currently wearing a tourniquet (server-synced worn mask).
     */
    private static boolean tourniquetApplied(LimbType limb) {
        if (limb == null || !(limb.isArm() || limb.isLeg())) {
            return false;
        }
        Player player = Minecraft.getInstance().player;
        return player != null && ClientTourniquetTracker.has(player.getId(), limb.ordinal());
    }

    private static boolean isTourniquetItem(ItemStack stack) {
        return stack.getItem() instanceof MedicalItem medical
                && medical.getTreatment() != null
                && medical.getTreatment().action() == TreatmentAction.APPLY_TOURNIQUET;
    }

    // ------------------------------------------------------------------ status readout

    /**
     * Live, recolored status line centred under the body: the selected limb's condition, or the overall health
     * state when nothing is selected.
     */
    private static void addStatusReadout(WidgetGroup root) {
        root.addWidget(new StatusLabel(STATUS_CENTER_X, STATUS_Y));
    }

    /**
     * Centred, self-recoloring status line. LabelWidget draws left-anchored, so it recentres itself around
     * {@link #STATUS_CENTER_X} each tick as the text length changes.
     */
    private static final class StatusLabel extends LabelWidget {
        private final int centerX;
        private final int lineY;

        private StatusLabel(int centerX, int y) {
            super(centerX, y, MedInteractionScreen::statusText);
            this.centerX = centerX;
            this.lineY = y;
        }

        @Override
        public void updateScreen() {
            super.updateScreen();
            setColor(statusColor());
            int width = Minecraft.getInstance().font.width(statusText());
            setSelfPosition(new Position(centerX - width / 2, lineY));
        }
    }

    private static String statusText() {
        LimbType limb = MedicalUIParts.selectedLimb();
        if (limb == null) {
            return MedicalUIParts.stateName(ClientMedicalCache.state()).getString();
        }
        LimbSummary s = MedicalUIParts.limbSummary(limb);
        String line = MedicalUIParts.limbName(limb).getString()
                + "  " + Math.round(s.healthPercent() * 100.0F) + "%";
        if (s.fracture()) {
            line += "  †"; // dagger marks a fracture
        }
        return UiText.escape(line);
    }

    private static int statusColor() {
        LimbType limb = MedicalUIParts.selectedLimb();
        if (limb == null) {
            return MedicalUIParts.stateColor(ClientMedicalCache.state());
        }
        return MedicalUIParts.limbColor(MedicalUIParts.limbSummary(limb).healthPercent());
    }

    // ------------------------------------------------------------------ OVERVIEW

    /**
     * Live top-level vitals in the right column, mirroring the character sheet's vitals panel.
     */
    private static void addOverview(WidgetGroup root) {
        int y = OVERVIEW_Y;
        Player player = Minecraft.getInstance().player;

        addLine(root, y, () ->
                "Health: " + Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth()));
        y += OVERVIEW_LINE_H;

        addLine(root, y, () -> {
            MedicalSyncPacket snap = ClientMedicalCache.get();
            double blood = snap == null ? 0.0 : snap.bloodMl();
            double maxBlood = snap == null ? 0.0 : snap.maxBloodMl();
            return "Blood: " + Math.round(blood) + "/" + Math.round(maxBlood) + " ml";
        });
        y += OVERVIEW_LINE_H;

        addLine(root, y, () ->
                UiText.escape("Pain: " + Math.round(MedicalUIParts.stats().totalPain() * 100.0F) + "%"));
        y += OVERVIEW_LINE_H;

        addLine(root, y, () ->
                "Bleeding: " + fmt((float) MedicalUIParts.stats().totalBleeding()) + " ml/s");
        y += OVERVIEW_LINE_H;

        // State line, recolored live by health state.
        LabelWidget stateLine = new LabelWidget(OVERVIEW_X, y, () ->
                "State: " + MedicalUIParts.stateName(ClientMedicalCache.state()).getString()) {
            @Override
            public void updateScreen() {
                super.updateScreen();
                setColor(MedicalUIParts.stateColor(ClientMedicalCache.state()));
            }
        };
        root.addWidget(stateLine);
        y += OVERVIEW_LINE_H;

        addLine(root, y, () ->
                UiText.escape("Movement: " + Math.round(MedicalUIParts.stats().movementMultiplier() * 100.0F) + "%"));
        y += OVERVIEW_LINE_H;

        addLine(root, y, () -> {
            DerivedStats st = MedicalUIParts.stats();
            if (st.anyLegFracture() && st.anyArmFracture()) {
                return "Fractures: arm + leg";
            }
            if (st.anyLegFracture()) {
                return "Fractures: leg";
            }
            if (st.anyArmFracture()) {
                return "Fractures: arm";
            }
            return "Fractures: none";
        });
    }

    private static void addLine(WidgetGroup root, int y, Supplier<String> text) {
        root.addWidget(new LabelWidget(OVERVIEW_X, y, text));
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // ------------------------------------------------------------------ wound model

    /**
     * The wound kinds derivable from a synced {@link LimbSummary}, each carrying a square colour and the
     * {@link TreatmentAction}s that address it (surfaced in the tooltip).
     */
    private enum Wound {
        OPEN_WOUND(0xFFB83232, "open_wound",
                TreatmentAction.HEAL_TRAUMA, TreatmentAction.SUTURE_WOUND),
        BLEEDING(0xFFE02020, "bleeding",
                TreatmentAction.REDUCE_BLEEDING, TreatmentAction.SUTURE_WOUND,
                TreatmentAction.BOOST_CLOTTING, TreatmentAction.APPLY_TOURNIQUET),
        FRACTURE(0xFFEDE6D6, "fracture",
                TreatmentAction.STABILIZE_FRACTURE),
        PAIN(0xFFE0A020, "pain",
                TreatmentAction.REDUCE_PAIN, TreatmentAction.NUMB_LIMB);

        private static final Wound[] VALUES = values();

        private final int color;
        private final String key;
        private final TreatmentAction[] treatments;

        Wound(int color, String key, TreatmentAction... treatments) {
            this.color = color;
            this.key = key;
            this.treatments = treatments;
        }

        private boolean present(LimbSummary s) {
            return switch (this) {
                case OPEN_WOUND -> s.healthPercent() < 0.999F;
                case BLEEDING -> s.bleeding() > 0.0F;
                case FRACTURE -> s.fracture();
                case PAIN -> s.pain() > 0.0F;
            };
        }

        /**
         * Tooltip: coloured name (+ severity value), a description of the effect, then the treatments that help.
         * Component tooltips render via vanilla, so bare {@code %} is safe here (no escaping needed).
         */
        private List<Component> tooltip(LimbSummary s) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.wfmedical.wound." + key)
                    .append(severity(s))
                    .withStyle(style -> style.withColor(color & 0xFFFFFF)));
            lines.add(Component.translatable("gui.wfmedical.wound." + key + ".desc"));
            lines.add(Component.translatable("gui.wfmedical.wound.treatments"));
            for (TreatmentAction action : treatments) {
                lines.add(Component.literal(" - ").append(
                        Component.translatable("gui.wfmedical.action." + action.name().toLowerCase(Locale.ROOT))));
            }
            return lines;
        }

        private Component severity(LimbSummary s) {
            return switch (this) {
                case OPEN_WOUND -> Component.literal("  " + Math.round(s.healthPercent() * 100.0F) + "%");
                case BLEEDING -> Component.literal("  " + String.format(Locale.ROOT, "%.1f", s.bleeding()) + " ml/s");
                case PAIN -> Component.literal("  " + Math.round(s.pain() * 100.0F) + "%");
                case FRACTURE -> Component.empty();
            };
        }
    }

    // ------------------------------------------------------------------ in-screen progress bar

    /**
     * Draws the shared medical-action progress bar inside the menu (the HUD overlay is suppressed while a screen
     * is open). The label sits above the bar, so the widget reserves 11px of headroom.
     */
    private static final class ProgressWidget extends Widget {
        private ProgressWidget(int x, int y, int width, int height) {
            super(new Position(x, y), new Size(width, height));
        }

        @Override
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTick);
            Position p = getPosition();
            Size s = getSize();
            ActionProgressOverlay.drawBar(graphics, p.x, p.y + 11, s.width);
        }
    }

    // ------------------------------------------------------------------ self-refreshing grid

    /**
     * A {@link WidgetGroup} that rebuilds its children whenever a signature value changes. The signature is
     * sampled every tick in {@link #updateScreen()}; on a change the group is cleared and the builder repopulates
     * it. Because the group is already initialised and marked client-side by then, re-added widgets are
     * initialised and marked automatically. Used to make the wounds / treatments grids follow the selected limb
     * and physiology updates live.
     */
    private static final class RefreshingGroup extends WidgetGroup {
        private final Supplier<Object> signature;
        private final Consumer<WidgetGroup> builder;
        private Object last;

        private RefreshingGroup(int x, int y, int width, int height,
                                Supplier<Object> signature, Consumer<WidgetGroup> builder) {
            super(x, y, width, height);
            this.signature = signature;
            this.builder = builder;
            builder.accept(this); // initial content; initialised with the group during ui.initWidgets()
            this.last = signature.get();
        }

        @Override
        public void updateScreen() {
            Object current = signature.get();
            if (!Objects.equals(current, last)) {
                last = current;
                clearAllWidgets();
                builder.accept(this);
            }
            super.updateScreen();
        }
    }
}
