package com.warfactory.medical.client.screen;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.network.ActiveTreatmentPacket;
import com.warfactory.medical.network.ClientMedicalCache;
import com.warfactory.medical.network.MedicalSyncPacket;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * CLIENT-ONLY character sheet, styled after the ARMA 3 ACE3 medical menu. Presents the player's derived
 * medical state as a live, read-only panel and lets the player request treatments on a selected limb.
 *
 * <p>The server is authoritative: this screen only READS the synced {@link ClientMedicalCache} snapshot
 * (via {@link MedicalUIParts}) and SENDS request packets (limb selection / treatment) through
 * {@link MedicalUIParts}. It never mutates medical state.</p>
 *
 * <p><b>Layout (fixed 280x200):</b></p>
 * <ul>
 *   <li>LEFT: the shared {@link MedicalUIParts#bodyDiagram(int, int, int, int)} body chart (6 clickable,
 *       health-colored limb tiles) with a selected-limb detail panel beneath it.</li>
 *   <li>RIGHT (upper): a live vitals panel (health, blood, pain, state, movement, bleeding, fractures).</li>
 *   <li>RIGHT (lower): a "Treatment" grid with one {@link ButtonWidget} per distinct medical item in the
 *       local inventory; clicking treats {@link MedicalUIParts#selectedLimb()} with that item.</li>
 *   <li>An overlaid, scrollable DEBUG panel dumping every synced value; shown only while
 *       {@link ClientMedicalCache#isDebug()} is true.</li>
 * </ul>
 *
 * <p><b>Debug toggling is LIVE</b> (no reopen required): the debug panel is a {@link DebugGroup} whose
 * {@code updateScreen()} runs every client tick (its parent invokes it regardless of the group's own
 * visibility, as long as the group is active) and syncs its visibility to {@code isDebug()}. This works
 * because LDLib's {@code WidgetGroup.updateScreen} recurses into all <em>active</em> children, not only
 * visible ones.</p>
 *
 * <p>Every live widget is client-side: the whole {@code mainGroup} is marked
 * {@link WidgetGroup#setClientSideWidget()} after all widgets are added, so all supplier-driven labels /
 * tiles re-read the cache each tick (required for client-only UIs — see the LDLib cheatsheet Q4).</p>
 */
public final class CharacterSheetUI {

    // ------------------------------------------------------------------ layout constants (px)
    private static final int UI_W = 280;
    private static final int UI_H = 200;

    private static final int BODY_X = 12;
    private static final int BODY_Y = 20;
    private static final int BODY_W = 74;
    private static final int BODY_H = 116;

    private static final int DETAIL_X = 10;
    private static final int DETAIL_Y = 142;
    private static final int DETAIL_LINE_H = 11;

    private static final int VITALS_X = 100;
    private static final int VITALS_Y = 20;
    private static final int VITALS_LINE_H = 12;

    private static final int ACTIONS_X = 100;
    private static final int ACTIONS_Y = 132;
    private static final int ACTION_BTN_W = 84;
    private static final int ACTION_BTN_H = 16;
    private static final int ACTION_GAP = 2;
    private static final int ACTION_COLS = 2;

    /**
     * Opaque dark backdrop for the debug overlay so it fully hides the vitals/actions beneath it.
     */
    private static final int DEBUG_BG = 0xF00A0A0A;
    private static final int DEBUG_LINE_H = 11;

    private CharacterSheetUI() {
    }

    /**
     * Build and open the character sheet on the client. No-op when there is no local player.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        ModularUI ui = new ModularUI(UI_W, UI_H, IUIHolder.EMPTY, player);
        ui.background(ResourceBorderTexture.BORDERED_BACKGROUND);
        WidgetGroup root = ui.mainGroup;

        // --- header ---
        root.addWidget(new LabelWidget(10, 6, "Medical Status"));
        root.addWidget(new LabelWidget(196, 6,
                () -> "Debug: " + (ClientMedicalCache.isDebug() ? "on" : "off")));

        // --- left: body chart + selected-limb detail ---
        root.addWidget(MedicalUIParts.bodyDiagram(BODY_X, BODY_Y, BODY_W, BODY_H));
        addSelectedLimbDetail(root, player);

        // --- right: vitals panel ---
        addVitals(root, player);

        // --- right: treatment action buttons ---
        addTreatmentActions(root);

        // --- overlaid, live-toggled debug panel ---
        root.addWidget(buildDebugGroup(player));

        // Every live widget re-reads its supplier only when marked client-side (client-only UI).
        root.setClientSideWidget();

        ClientUIOpener.openClientUI(ui);
    }

    // ------------------------------------------------------------------ selected-limb detail

    /**
     * Left column beneath the body chart: the currently selected limb's summary, updated live.
     */
    private static void addSelectedLimbDetail(WidgetGroup root, Player player) {
        int y = DETAIL_Y;
        root.addWidget(new LabelWidget(DETAIL_X, y, () -> {
            LimbType limb = MedicalUIParts.selectedLimb();
            return limb == null ? "No limb selected" : MedicalUIParts.limbName(limb).getString();
        }));
        y += DETAIL_LINE_H;
        root.addWidget(new LabelWidget(DETAIL_X, y, () -> {
            LimbType limb = MedicalUIParts.selectedLimb();
            if (limb == null) {
                return "-";
            }
            return "Health: " + Math.round(MedicalUIParts.limbSummary(limb).healthPercent() * 100.0F) + "%";
        }));
        y += DETAIL_LINE_H;
        root.addWidget(new LabelWidget(DETAIL_X, y, () -> {
            LimbType limb = MedicalUIParts.selectedLimb();
            if (limb == null) {
                return "";
            }
            return "Bleeding: " + fmt(MedicalUIParts.limbSummary(limb).bleeding());
        }));
        y += DETAIL_LINE_H;
        root.addWidget(new LabelWidget(DETAIL_X, y, () -> {
            LimbType limb = MedicalUIParts.selectedLimb();
            if (limb == null) {
                return "";
            }
            return "Pain: " + fmt(MedicalUIParts.limbSummary(limb).pain());
        }));
        y += DETAIL_LINE_H;
        root.addWidget(new LabelWidget(DETAIL_X, y, () -> {
            LimbType limb = MedicalUIParts.selectedLimb();
            if (limb != null && MedicalUIParts.limbSummary(limb).fracture()) {
                return "Fractured";
            }
            return "";
        }));
    }

    // ------------------------------------------------------------------ vitals

    /**
     * Right upper column: live top-level vitals. The state line is recolored each tick.
     */
    private static void addVitals(WidgetGroup root, Player player) {
        int y = VITALS_Y;

        root.addWidget(new LabelWidget(VITALS_X, y, () ->
                "Health: " + Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth())));
        y += VITALS_LINE_H;

        root.addWidget(new LabelWidget(VITALS_X, y, () -> {
            MedicalSyncPacket snap = ClientMedicalCache.get();
            double blood = snap == null ? 0.0 : snap.bloodMl();
            double maxBlood = snap == null ? 0.0 : snap.maxBloodMl();
            return "Blood: " + Math.round(blood) + "/" + Math.round(maxBlood) + " ml";
        }));
        y += VITALS_LINE_H;

        root.addWidget(new LabelWidget(VITALS_X, y, () ->
                "Pain: " + Math.round(MedicalUIParts.stats().totalPain() * 100.0F) + "%"));
        y += VITALS_LINE_H;

        // State line: colored live by health state.
        LabelWidget stateLine = new LabelWidget(VITALS_X, y, () ->
                "State: " + MedicalUIParts.stateName(ClientMedicalCache.state()).getString()) {
            @Override
            public void updateScreen() {
                super.updateScreen();
                setColor(MedicalUIParts.stateColor(ClientMedicalCache.state()));
            }
        };
        root.addWidget(stateLine);
        y += VITALS_LINE_H;

        root.addWidget(new LabelWidget(VITALS_X, y, () ->
                "Movement: " + Math.round(MedicalUIParts.stats().movementMultiplier() * 100.0F) + "%"));
        y += VITALS_LINE_H;

        root.addWidget(new LabelWidget(VITALS_X, y, () ->
                "Bleeding: " + fmt((float) MedicalUIParts.stats().totalBleeding()) + " ml/s"));
        y += VITALS_LINE_H;

        root.addWidget(new LabelWidget(VITALS_X, y, () -> {
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
        }));
    }

    // ------------------------------------------------------------------ treatment actions

    /**
     * Right lower grid: one button per distinct medical item currently held. Clicking sends a treatment
     * request for the live {@link MedicalUIParts#selectedLimb()} (null = server auto-pick). The item set is
     * snapshotted at open time; reopen the sheet to pick up inventory changes.
     */
    private static void addTreatmentActions(WidgetGroup root) {
        root.addWidget(new LabelWidget(ACTIONS_X, ACTIONS_Y - 12, "Treatment"));

        List<ItemStack> items = MedicalUIParts.availableMedicalItems();
        if (items.isEmpty()) {
            root.addWidget(new LabelWidget(ACTIONS_X, ACTIONS_Y, "No medical items in inventory"));
            return;
        }

        int col = 0;
        int row = 0;
        for (ItemStack stack : items) {
            int bx = ACTIONS_X + col * (ACTION_BTN_W + ACTION_GAP);
            int by = ACTIONS_Y + row * (ACTION_BTN_H + ACTION_GAP);
            String name = stack.getHoverName().getString();
            ButtonWidget button = new ButtonWidget(bx, by, ACTION_BTN_W, ACTION_BTN_H,
                    new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON, new TextTexture(name)),
                    (ClickData cd) -> MedicalUIParts.requestAction(stack, MedicalUIParts.selectedLimb()));
            root.addWidget(button);

            col++;
            if (col >= ACTION_COLS) {
                col = 0;
                row++;
            }
        }
    }

    // ------------------------------------------------------------------ debug overlay

    /**
     * Build the scrollable debug overlay group; visibility is driven live by {@link ClientMedicalCache#isDebug()}.
     */
    private static DraggableScrollableWidgetGroup buildDebugGroup(Player player) {
        DebugGroup group = new DebugGroup(96, 18, 180, 176);
        group.setBackground(new ColorRectTexture(DEBUG_BG));
        group.setVisible(ClientMedicalCache.isDebug());

        int y = 4;
        addDebugLine(group, y, () -> "== DEBUG (all synced values) ==");
        y += DEBUG_LINE_H;

        addDebugLine(group, y, () -> "effMaxHealth=" + fmt(MedicalUIParts.stats().effectiveMaxHealth()));
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "healthModifier=" + fmt(MedicalUIParts.stats().healthModifier()));
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "effCurHealth=" + fmt(MedicalUIParts.stats().effectiveCurrentHealth()));
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "totalBleeding=" + fmt((float) MedicalUIParts.stats().totalBleeding()));
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "totalPain=" + fmt(MedicalUIParts.stats().totalPain()));
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "moveMult=" + fmt(MedicalUIParts.stats().movementMultiplier()));
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "sprintBlocked=" + MedicalUIParts.stats().sprintBlocked());
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "jumpMult=" + fmt(MedicalUIParts.stats().jumpMultiplier()));
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "state=" + MedicalUIParts.stats().state());
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "anyLegFracture=" + MedicalUIParts.stats().anyLegFracture());
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "anyArmFracture=" + MedicalUIParts.stats().anyArmFracture());
        y += DEBUG_LINE_H;

        addDebugLine(group, y, () -> {
            MedicalSyncPacket snap = ClientMedicalCache.get();
            double blood = snap == null ? 0.0 : snap.bloodMl();
            double maxBlood = snap == null ? 0.0 : snap.maxBloodMl();
            return "blood=" + fmt((float) blood) + "/" + fmt((float) maxBlood) + "ml";
        });
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "painSuppression=" + fmt(ClientMedicalCache.painSuppression()));
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "Drug Load: " + fmt(ClientMedicalCache.drugLoad()));
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "Unconscious: " + ClientMedicalCache.stats().unconscious());
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "highLevelState=" + ClientMedicalCache.state());
        y += DEBUG_LINE_H;
        addDebugLine(group, y, () -> "selectedLimb=" + MedicalUIParts.selectedLimb());
        y += DEBUG_LINE_H;

        addDebugLine(group, y, () -> {
            ActiveTreatmentPacket a = ClientMedicalCache.activeTreatment();
            if (a == null || !a.active()) {
                return "activeTreatment=none";
            }
            return "activeTreatment=" + a.action() + " limb=" + a.limb()
                    + " ticks=" + a.totalTicks() + " start=" + a.startGameTime();
        });
        y += DEBUG_LINE_H;

        for (LimbType limb : LimbType.VALUES) {
            addDebugLine(group, y, () -> {
                LimbSummary s = MedicalUIParts.limbSummary(limb);
                return limb.name() + ": hp=" + Math.round(s.healthPercent() * 100.0F) + "%"
                        + " bl=" + fmt(s.bleeding()) + " pn=" + fmt(s.pain())
                        + " fx=" + s.fracture();
            });
            y += DEBUG_LINE_H;
        }

        return group;
    }

    /**
     * Add one live debug label at the given y inside the scrollable debug group.
     */
    private static void addDebugLine(DraggableScrollableWidgetGroup group, int y, Supplier<String> text) {
        group.addWidget(new LabelWidget(4, y, text));
    }

    /**
     * Compact 2-decimal formatting for the numeric readouts.
     */
    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Scrollable debug overlay whose visibility follows {@link ClientMedicalCache#isDebug()} every tick.
     * Its {@code updateScreen()} runs while the sheet is open regardless of its own current visibility (the
     * parent recurses into all active children), giving a live toggle without reopening the sheet.
     */
    private static final class DebugGroup extends DraggableScrollableWidgetGroup {
        private DebugGroup(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override
        public void updateScreen() {
            super.updateScreen();
            boolean debug = ClientMedicalCache.isDebug();
            if (isVisible() != debug) {
                setVisible(debug);
            }
        }
    }
}
