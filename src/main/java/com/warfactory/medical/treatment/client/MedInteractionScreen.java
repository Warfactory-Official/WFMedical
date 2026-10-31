package com.warfactory.medical.treatment.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.DerivedStats;
import com.warfactory.medical.body.HealthState;
import com.warfactory.medical.body.client.ClientMedicalCache;
import com.warfactory.medical.body.client.LimbColors;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.body.network.MedicalSyncPacket;
import com.warfactory.medical.body.network.MedicalSyncPacket.LimbSummary;
import com.warfactory.medical.body.network.MedicalSyncPacket.WoundView;
import com.warfactory.medical.body.trauma.TraumaCategory;
import com.warfactory.medical.body.trauma.TraumaResponse;
import com.warfactory.medical.body.trauma.TraumaType;
import com.warfactory.medical.body.trauma.TraumaTypes;
import com.warfactory.medical.body.trauma.TreatmentAction;
import com.warfactory.medical.treatment.MedicalItem;
import com.warfactory.medical.treatment.TreatmentContent;
import com.warfactory.medical.treatment.network.TargetSheetInfoPacket;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.Ui;
import com.wf.wflib.ui.api.UiContext;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.UiScreen;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.Item;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Rect;
import com.wf.wflib.ui.api.widget.SkinPart;
import com.wf.wflib.ui.api.widget.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Medical sheet: self ({@code targetId < 0}) or a treated player. View state refreshed per client tick. */
public final class MedInteractionScreen {

    public static final ResourceLocation LAYOUT_ID = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "sheet");

    private static final MedInteractionScreen VIEW = new MedInteractionScreen();
    static final Subject<MedInteractionScreen> SUBJECT =
            Subject.register("medical_sheet", MedInteractionScreen.class, () -> VIEW);
    static final Subject<Wound> WOUND = Subject.register("medical_wound", Wound.class);
    static final Subject<Cell> CELL = Subject.register("medical_cell", Cell.class);

    private static final int ROOT_W = 453;
    private static final int ROOT_H = 152;
    private static final int LEFT_X = 12;
    private static final int WOUND_Y = 17;
    private static final int TREAT_LABEL_Y = 45;
    private static final int GRID_Y = 57;
    private static final int GRID_CELL = 26;
    private static final int OVERVIEW_X = 302;
    private static final int OVERVIEW_Y = 22;
    private static final int OVERVIEW_LINE_H = 12;
    private static final int OVERVIEW_LINES = 7;
    private static final int TEXT_MAX = 48;
    private static final String WHITE = "#ffffffff";

    private record LimbTile(LimbType limb, SkinPart.Part part, int x, int y, int w, int h) {
    }

    private static final List<LimbTile> BODY_TILES = List.of(
            new LimbTile(LimbType.HEAD, SkinPart.Part.HEAD, 216, 22, 20, 20),
            new LimbTile(LimbType.LEFT_ARM, SkinPart.Part.LEFT_ARM, 204, 45, 10, 34),
            new LimbTile(LimbType.TORSO, SkinPart.Part.TORSO, 216, 45, 20, 33),
            new LimbTile(LimbType.RIGHT_ARM, SkinPart.Part.RIGHT_ARM, 238, 45, 10, 33),
            new LimbTile(LimbType.LEFT_LEG, SkinPart.Part.LEFT_LEG, 216, 81, 9, 38),
            new LimbTile(LimbType.RIGHT_LEG, SkinPart.Part.RIGHT_LEG, 227, 81, 9, 38));

    private static final LimbSummary[] NO_LIMBS = new LimbSummary[LimbType.VALUES.length];

    static {
        for (LimbType limb : LimbType.VALUES) {
            NO_LIMBS[limb.ordinal()] = new LimbSummary(limb, 1.0F, 0.0F, 0.0F, false, List.of());
        }
    }

    private static volatile int targetId = -1;
    private static volatile MedicalSyncPacket targetSnapshot;
    private static volatile int pendingOpenTarget = -1;
    private static UiLayout layout;

    private final float[] limbHealth = new float[LimbType.VALUES.length];
    private final String[] lines = new String[OVERVIEW_LINES];
    private LimbType selected;
    private String title = "";
    private String status = "";
    private int statusColor;
    private int stateColor;
    private boolean treating;
    private Object woundSignature;
    private Object cellSignature;
    private List<Wound> wounds = List.of();
    private List<Cell> cells = List.of();
    private String woundHint = "";
    private String cellHint = "";

    private MedInteractionScreen() {
    }

    /** Wound chip: colour + tooltip lines, built when the examination signature changes. */
    static final class Wound {
        final int color;
        final List<Component> tooltip;

        Wound(int color, List<Component> tooltip) {
            this.color = color;
            this.tooltip = tooltip;
        }
    }

    /** Treatment grid cell; identity stable until the treatment signature changes (input hit key). */
    static final class Cell {
        final int color;
        final ItemStack stack;
        final Runnable action;
        private final List<Component> fixedTooltip;
        private List<Component> itemTooltip;

        Cell(int color, ItemStack stack, List<Component> fixedTooltip, Runnable action) {
            this.color = color;
            this.stack = stack;
            this.fixedTooltip = fixedTooltip;
            this.action = action;
        }

        List<Component> tooltip() {
            if (fixedTooltip != null) {
                return fixedTooltip;
            }
            if (itemTooltip == null) {
                Minecraft mc = Minecraft.getInstance();
                itemTooltip = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                        mc.player, TooltipFlag.Default.NORMAL);
            }
            return itemTooltip;
        }
    }

    static void registerSignals() {
        Signals.num(SUBJECT, "target", v -> targetId);
        Signals.text(SUBJECT, "title", TEXT_MAX, v -> v.title);
        Signals.num(SUBJECT, "title_color", v -> (targetId < 0 ? 0xFFFFFFFF : 0xFFE0A020) & 0xFFFFFFFFL);
        Signals.text(SUBJECT, "status", TEXT_MAX, v -> v.status);
        Signals.num(SUBJECT, "status_color", v -> v.statusColor & 0xFFFFFFFFL);
        Signals.num(SUBJECT, "state_color", v -> v.stateColor & 0xFFFFFFFFL);
        Signals.bool(SUBJECT, "treating", v -> v.treating);
        for (LimbType limb : LimbType.VALUES) {
            String n = "limb." + limb.name().toLowerCase(Locale.ROOT);
            Signals.num(SUBJECT, n + ".health", v -> v.limbHealth[limb.ordinal()]);
            Signals.bool(SUBJECT, n + ".selected", v -> v.selected == limb);
        }
        for (int i = 0; i < OVERVIEW_LINES; i++) {
            int line = i;
            Signals.text(SUBJECT, "line_" + i, TEXT_MAX, v -> v.lines[line]);
        }
        Signals.items(SUBJECT, "wounds", v -> v.wounds, WOUND);
        Signals.text(SUBJECT, "wound_hint", TEXT_MAX, v -> v.woundHint);
        Signals.items(SUBJECT, "cells", v -> v.cells, CELL);
        Signals.text(SUBJECT, "cell_hint", TEXT_MAX, v -> v.cellHint);
        Signals.num(WOUND, "color", w -> w.color & 0xFFFFFFFFL);
        Signals.num(CELL, "color", c -> c.color & 0xFFFFFFFFL);
    }

    public static void markPendingOpen(int targetEntityId) {
        pendingOpenTarget = targetEntityId;
    }

    public static int targetId() {
        return targetId;
    }

    public static void clearTarget() {
        targetId = -1;
        targetSnapshot = null;
        pendingOpenTarget = -1;
    }

    public static boolean isOpen(Minecraft mc) {
        return mc.screen instanceof UiScreen s && s.layout().id().equals(LAYOUT_ID);
    }

    public static void onTargetSheetInfo(TargetSheetInfoPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int id = packet.targetEntityId();
        targetSnapshot = packet.snapshot();
        ClientTourniquetTracker.set(id, packet.tourniquetMask());
        if (targetId == id || id != pendingOpenTarget) {
            return;
        }
        open(id);
    }

    public static void open() {
        open(-1);
    }

    public static void open(int targetEntityId) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        targetId = targetEntityId;
        pendingOpenTarget = -1;
        if (targetEntityId < 0) {
            targetSnapshot = null;
        }
        if (layout == null) {
            layout = new UiLayout(LAYOUT_ID, root(), 0).withDesignSize(ROOT_W, ROOT_H);
        }
        VIEW.woundSignature = null;
        VIEW.cellSignature = null;
        VIEW.refresh();
        UiScreen.open(layout, UiContext.EMPTY, Component.empty(),
                new UiScreen.Options(false, true, VIEW::refresh, null));
    }

    private static WidgetDef root() {
        List<WidgetDef> children = new ArrayList<>();
        children.add(Ui.rect(ROOT_W, ROOT_H, "#44222222"));
        children.add(label(LEFT_X, 5, "EXAMINATION", WHITE));
        children.add(label(LEFT_X, TREAT_LABEL_Y, "TREATMENT", WHITE));
        children.add(label(208, 5, "{medical_sheet.title}", "medical_sheet.title_color"));
        children.add(label(340, 5, "OVERVIEW", WHITE));
        for (LimbTile tile : BODY_TILES) {
            children.add(limbTile(tile));
        }
        children.add(Ui.place("226", "124", Point.TOP,
                Ui.shadowed("{medical_sheet.status}", "medical_sheet.status_color")));
        children.add(Ui.at(Integer.toString(LEFT_X), Integer.toString(WOUND_Y), Ui.stack(
                Ui.repeat("medical_sheet.wounds", false, 4, new Tooltip(item -> ((Wound) item).tooltip,
                        new Rect("24", "24", "medical_wound.color", 3, 0))),
                Ui.at("0", "8", Ui.shadowed("{medical_sheet.wound_hint}", WHITE)))));
        children.add(Ui.at(Integer.toString(LEFT_X), Integer.toString(GRID_Y), Ui.when("medical_sheet.treating",
                Ui.stack(
                        ActionProgressOverlay.bar(165),
                        Ui.at("43", "27", Ui.button(80, 16, "#ff3a3a3a", "#ff505050", "#ff8a8a8a",
                                item -> MedicalUIParts.requestCancelTreatment(),
                                Ui.shadowed("{tr:gui.wfmedical.treat.interrupt}", WHITE)))),
                Ui.stack(
                        Ui.grid("medical_sheet.cells", 6, 5, new Tooltip(item -> ((Cell) item).tooltip(),
                                new Button(item -> ((Cell) item).action.run(), "true", "medical_cell.color",
                                        "medical_cell.color", "medical_cell.color", "#00000000", 0, 6, 0, 0,
                                        GRID_CELL, GRID_CELL, Point.CENTER,
                                        new Item(item -> ((Cell) item).stack, 16, false), "#ffffdd55", 2))),
                        Ui.at("0", "8", Ui.shadowed("{medical_sheet.cell_hint}", WHITE))))));
        for (int i = 0; i < OVERVIEW_LINES; i++) {
            children.add(label(OVERVIEW_X, OVERVIEW_Y + i * OVERVIEW_LINE_H, "{medical_sheet.line_" + i + "}",
                    i == 4 ? "medical_sheet.state_color" : WHITE));
        }
        return Ui.stack(children.toArray(WidgetDef[]::new));
    }

    private static WidgetDef label(int x, int y, String template, String color) {
        return Ui.at(Integer.toString(x), Integer.toString(y), Ui.shadowed(template, color));
    }

    private static WidgetDef limbTile(LimbTile tile) {
        String n = "medical_sheet.limb." + tile.limb().name().toLowerCase(Locale.ROOT);
        String w = Integer.toString(tile.w());
        String h = Integer.toString(tile.h());
        return Ui.at(Integer.toString(tile.x()), Integer.toString(tile.y()),
                new Tooltip(item -> MedicalUIParts.limbTooltip(tile.limb(), MedInteractionScreen::sheetLimb),
                        new Button(item -> MedicalUIParts.selectLimb(tile.limb()), "true", "#00000000", "#00000000",
                                "#00000000", "#00000000", 0, 0, 0, 0, tile.w(), tile.h(), Point.TOP_LEFT,
                                Ui.stack(
                                        new SkinPart(tile.part(), "medical_sheet.target", n + ".health",
                                                tile.w(), tile.h()),
                                        Ui.when(n + ".selected", new Rect(w, h, WHITE, 0, 2))))));
    }

    private void refresh() {
        selected = MedicalUIParts.selectedLimb();
        treating = ClientTreatmentCache.hasActiveTreatment();
        for (LimbType limb : LimbType.VALUES) {
            limbHealth[limb.ordinal()] = sheetLimb(limb).healthPercent();
        }
        title = targetId < 0 ? "STATUS" : targetName().getString();
        HealthState state = sheetState();
        stateColor = LimbColors.stateColor(state);
        if (selected == null) {
            status = MedicalUIParts.stateName(state).getString();
            statusColor = stateColor;
        } else {
            LimbSummary s = sheetLimb(selected);
            status = MedicalUIParts.limbName(selected).getString() + "  " + Math.round(s.healthPercent() * 100.0F)
                    + "%" + (s.fracture() ? "  †" : "");
            statusColor = LimbColors.limbColor(s.healthPercent());
        }
        refreshOverview(state);
        Object ws = examinationSignature();
        if (!Objects.equals(ws, woundSignature)) {
            woundSignature = ws;
            rebuildWounds();
        }
        Object cs = treatmentSignature();
        if (!Objects.equals(cs, cellSignature)) {
            cellSignature = cs;
            rebuildCells();
        }
    }

    private void refreshOverview(HealthState state) {
        DerivedStats st = sheetStats();
        MedicalSyncPacket snap = sheetSnapshot();
        Player player = Minecraft.getInstance().player;
        lines[0] = targetId < 0 && player != null
                ? "Health: " + Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth())
                : "Health: " + Math.round(st.effectiveCurrentHealth()) + "/" + Math.round(st.effectiveMaxHealth());
        lines[1] = "Blood: " + Math.round(snap == null ? 0.0 : snap.bloodMl()) + "/"
                + Math.round(snap == null ? 0.0 : snap.maxBloodMl()) + " ml";
        lines[2] = "Pain: " + Math.round(st.totalPain() * 100.0F) + "%  Stress: " + Math.round(st.stress() * 100.0F) + "%";
        lines[3] = "Bleeding: " + String.format(Locale.ROOT, "%.2f", (float) st.totalBleeding()) + " ml/s";
        lines[4] = "State: " + MedicalUIParts.stateName(state).getString();
        lines[5] = "Movement: " + Math.round(st.movementMultiplier() * 100.0F) + "%";
        lines[6] = st.anyLegFracture() && st.anyArmFracture() ? "Fractures: arm + leg"
                : st.anyLegFracture() ? "Fractures: leg"
                : st.anyArmFracture() ? "Fractures: arm"
                : "Fractures: none";
    }

    private static MedicalSyncPacket sheetSnapshot() {
        return targetId < 0 ? ClientMedicalCache.get() : targetSnapshot;
    }

    private static LimbSummary sheetLimb(LimbType limb) {
        MedicalSyncPacket snap = sheetSnapshot();
        if (snap != null && snap.limbs() != null) {
            for (LimbSummary s : snap.limbs()) {
                if (s != null && s.limb() == limb) {
                    return s;
                }
            }
        }
        return NO_LIMBS[limb.ordinal()];
    }

    private static DerivedStats sheetStats() {
        MedicalSyncPacket snap = sheetSnapshot();
        return snap == null ? DerivedStats.healthy() : snap.stats();
    }

    private static HealthState sheetState() {
        MedicalSyncPacket snap = sheetSnapshot();
        return snap == null ? HealthState.HEALTHY : snap.state();
    }

    private static int subjectId() {
        Player player = Minecraft.getInstance().player;
        return targetId < 0 ? (player == null ? -1 : player.getId()) : targetId;
    }

    private static Component targetName() {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(targetId);
        return e != null ? e.getName() : Component.translatable("gui.wfmedical.wheel.target");
    }

    private Object examinationSignature() {
        if (selected == null) {
            return "none";
        }
        LimbSummary s = sheetLimb(selected);
        StringBuilder sb = new StringBuilder();
        sb.append(targetId).append('|').append(selected).append('|')
                .append(s.healthPercent()).append('|').append(s.pain());
        for (WoundView w : s.wounds()) {
            sb.append('|').append(w.typeId()).append(':').append(w.severity()).append(':').append(w.flags());
        }
        return sb.toString();
    }

    private void rebuildWounds() {
        if (selected == null) {
            wounds = List.of();
            woundHint = Component.translatable("gui.wfmedical.wound.no_limb").getString();
            return;
        }
        List<WoundView> views = sheetLimb(selected).wounds();
        List<Wound> out = new ArrayList<>(views.size());
        for (WoundView w : views) {
            out.add(new Wound(woundColor(w), woundTooltip(w)));
        }
        wounds = out;
        woundHint = out.isEmpty() ? Component.translatable("gui.wfmedical.wound.none").getString() : "";
    }

    private static TraumaType woundType(WoundView w) {
        TraumaTypes.Lookup reg = TraumaTypes.LIVE;
        return reg == null ? null : reg.get(w.typeId());
    }

    private static int woundColor(WoundView w) {
        TraumaType type = woundType(w);
        TraumaCategory cat = type == null ? null : type.getCategory();
        if (cat == TraumaCategory.FRACTURE) {
            return 0xFFEDE6D6;
        }
        if (w.bleeding() || w.bleedControlled()) {
            return 0xFFE02020;
        }
        if (cat == TraumaCategory.BURN || cat == TraumaCategory.CHEMICAL_BURN || cat == TraumaCategory.RADIATION_BURN) {
            return 0xFFE07020;
        }
        if (type != null && !type.isMajor()) {
            return 0xFFC9A24B;
        }
        return 0xFFB83232;
    }

    private static List<Component> woundTooltip(WoundView w) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("wound.wfmedical." + w.typeId())
                .copy()
                .append(Component.literal("  " + w.severity() + "%"))
                .withStyle(style -> style.withColor(woundColor(w) & 0xFFFFFF)));
        lines.add(Component.literal(woundStateText(w)).withStyle(ChatFormatting.GRAY));
        TraumaType type = woundType(w);
        if (type != null && !type.getResponses().isEmpty()) {
            lines.add(Component.translatable("gui.wfmedical.wound.treatments"));
            for (TraumaResponse r : type.getResponses().values()) {
                lines.add(Component.literal(" - ")
                        .append(Component.translatable("gui.wfmedical.action." + r.action().name().toLowerCase(Locale.ROOT)))
                        .append(Component.literal(": " + effectText(r)))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }

    private static String woundStateText(WoundView w) {
        if (w.closed()) {
            return "Sutured shut";
        }
        if (w.stabilized()) {
            return "Splinted";
        }
        if (w.treated()) {
            return "Treated - mending";
        }
        if (w.bleeding() && w.bleedControlled()) {
            return "Bleeding (dressed)";
        }
        if (w.bleeding()) {
            return "Bleeding";
        }
        if (w.bleedControlled()) {
            return "Bleed stopped - wound untreated";
        }
        return "Untreated";
    }

    private static String effectText(TraumaResponse r) {
        return switch (r.effect()) {
            case STOP_BLEED -> "stops bleeding";
            case REDUCE_BLEED -> "slows bleeding to " + Math.round(r.factor() * 100.0F) + "%";
            case SUTURE -> "closes the wound";
            case STABILIZE -> "stabilizes";
            case HEAL -> "heals the wound";
        };
    }

    private Object treatmentSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(targetId).append('|').append(treating).append('|')
                .append(selected).append('|').append(tourniquetApplied(selected)).append('|')
                .append(targetDowned()).append('|');
        for (ItemStack stack : MedicalUIParts.availableMedicalItems()) {
            sb.append(stack.getItem().getDescriptionId()).append(',');
        }
        return sb.toString();
    }

    private void rebuildCells() {
        List<Cell> out = new ArrayList<>();
        if (!treating) {
            int target = targetId;
            boolean tqApplied = tourniquetApplied(selected);
            if (targetDowned()) {
                out.add(new Cell(0xD02060B0, ItemStack.EMPTY,
                        List.of(Component.translatable("gui.wfmedical.resuscitate"),
                                Component.translatable("gui.wfmedical.resuscitate.tooltip")),
                        () -> MedicalUIParts.requestResuscitate(target)));
            }
            if (tqApplied) {
                out.add(new Cell(0xD0B02020, new ItemStack(TreatmentContent.TOURNIQUET.get()),
                        List.of(Component.translatable("gui.wfmedical.tourniquet.remove")),
                        () -> MedicalUIParts.requestRemoveTourniquet(MedicalUIParts.selectedLimb(), target)));
            }
            for (ItemStack stack : MedicalUIParts.availableMedicalItems()) {
                if (tqApplied && isTourniquetItem(stack)) {
                    continue;
                }
                out.add(new Cell(0xC0202020, stack, null,
                        () -> MedicalUIParts.requestAction(stack, MedicalUIParts.selectedLimb(), target)));
            }
        }
        cells = out;
        cellHint = !treating && out.isEmpty() ? Component.translatable("gui.wfmedical.radial.no_items").getString() : "";
    }

    /** Downed casualty: only action with no item. */
    private static boolean targetDowned() {
        if (targetId < 0) {
            return false;
        }
        MedicalSyncPacket snap = sheetSnapshot();
        return snap != null && snap.stats() != null && snap.stats().unconscious();
    }

    private static boolean tourniquetApplied(LimbType limb) {
        if (limb == null || !(limb.isArm() || limb.isLeg())) {
            return false;
        }
        int id = subjectId();
        return id >= 0 && ClientTourniquetTracker.has(id, limb.ordinal());
    }

    private static boolean isTourniquetItem(ItemStack stack) {
        return stack.getItem() instanceof MedicalItem medical
                && medical.getTreatment() != null
                && medical.getTreatment().action() == TreatmentAction.APPLY_TOURNIQUET;
    }
}
