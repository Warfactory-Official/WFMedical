package com.warfactory.medical.client.screen;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.warfactory.medical.client.ClientTourniquetTracker;
import com.warfactory.medical.client.overlay.ActionProgressOverlay;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaResponse;
import com.warfactory.medical.core.trauma.TraumaType;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.item.MedicalItem;
import com.warfactory.medical.item.ModItems;
import com.warfactory.medical.network.ClientMedicalCache;
import com.warfactory.medical.network.MedicalSyncPacket;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import com.warfactory.medical.network.MedicalSyncPacket.WoundView;
import com.warfactory.medical.network.TargetSheetInfoPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MedInteractionScreen {

    private static final int ROOT_W = 453;
    private static final int ROOT_H = 152;
    private static final int ROOT_BG = 0x44222222;

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
    private static final int TQ_REMOVE_BG_COLOR = 0xD0B02020;

    private record LimbTile(LimbType limb, int x, int y, int w, int h) {
    }

    private static final List<LimbTile> BODY_TILES = List.of(
            new LimbTile(LimbType.HEAD, 216, 22, 20, 20),
            new LimbTile(LimbType.LEFT_ARM, 204, 45, 10, 34),
            new LimbTile(LimbType.TORSO, 216, 45, 20, 33),
            new LimbTile(LimbType.RIGHT_ARM, 238, 45, 10, 33),
            new LimbTile(LimbType.LEFT_LEG, 216, 81, 9, 38),
            new LimbTile(LimbType.RIGHT_LEG, 227, 81, 9, 38));

    private static final int STATUS_CENTER_X = 226;
    private static final int STATUS_Y = 124;

    private static final int OVERVIEW_X = 302;
    private static final int OVERVIEW_Y = 22;
    private static final int OVERVIEW_LINE_H = 12;

    private static volatile int targetId = -1;
    private static volatile MedicalSyncPacket targetSnapshot;
    private static volatile int pendingOpenTarget = -1;

    private MedInteractionScreen() {
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

    public static void onTargetSheetInfo(TargetSheetInfoPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int id = packet.targetEntityId();
        targetSnapshot = packet.snapshot();
        ClientTourniquetTracker.set(id, packet.tourniquetMask());
        if (targetId == id) {
            return;
        }
        if (id != pendingOpenTarget) {
            return;
        }
        open(id);
    }

    public static void open() {
        open(-1);
    }

    public static void open(int targetEntityId) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        targetId = targetEntityId;
        pendingOpenTarget = -1;
        if (targetEntityId < 0) {
            targetSnapshot = null;
        }

        UIElement root = new UIElement();
        // Size only, and deliberately NOT position: absolute -- ModularUI centres the root on screen
        // only when its position type is not ABSOLUTE, otherwise it honours the root's own taffy
        // location (which for an absolute root pinned at 0,0 means the top-left corner).
        root.layout(layout -> layout.width(ROOT_W).height(ROOT_H));
        root.style(style -> style.background(new ColorRectTexture(ROOT_BG)));

        addHeaders(root);
        addBodyDiagram(root);
        addStatusReadout(root);
        addExaminationGrid(root);
        addTreatmentGrid(root);
        addOverview(root);

        ClientUIOpener.openClientUI(ModularUI.of(UI.of(root), player));
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
        return new LimbSummary(limb, 1.0F, 0.0F, 0.0F, false, List.of());
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


    private static void addHeaders(UIElement root) {
        root.addChild(MedUi.label(LEFT_X, 5, "EXAMINATION"));
        root.addChild(MedUi.label(LEFT_X, TREAT_LABEL_Y, "TREATMENT"));
        Label statusHeader = MedUi.label(208, 5, targetId < 0 ? "STATUS" : targetName().getString());
        if (targetId >= 0) {
            statusHeader.textStyle(style -> style.textColor(0xFFE0A020));
        }
        root.addChild(statusHeader);
        root.addChild(MedUi.label(340, 5, "OVERVIEW"));
    }


    private static void addBodyDiagram(UIElement root) {
        ClientPlayerSkins.Skin skin = ClientPlayerSkins.forEntity(targetId);
        for (LimbTile tile : BODY_TILES) {
            MedicalUIParts.addLimbTile(root, tile.limb(), tile.x(), tile.y(), tile.w(), tile.h(),
                    MedInteractionScreen::sheetLimb, skin);
        }
    }


    private static void addExaminationGrid(UIElement root) {
        root.addChild(new RefreshingGroup(LEFT_X, WOUND_Y, 185, 30,
                MedInteractionScreen::examinationSignature, MedInteractionScreen::buildWounds));
    }

    private static Object examinationSignature() {
        LimbType limb = MedicalUIParts.selectedLimb();
        if (limb == null) {
            return "none";
        }
        LimbSummary s = sheetLimb(limb);
        StringBuilder sb = new StringBuilder();
        sb.append(targetId).append('|').append(limb).append('|')
                .append(s.healthPercent()).append('|').append(s.pain());
        for (WoundView w : s.wounds()) {
            sb.append('|').append(w.typeId()).append(':').append(w.severity()).append(':').append(w.flags());
        }
        return sb.toString();
    }

    private static void buildWounds(UIElement group) {
        LimbType limb = MedicalUIParts.selectedLimb();
        if (limb == null) {
            group.addChild(MedUi.label(0, 8, Component.translatable("gui.wfmedical.wound.no_limb").getString()));
            return;
        }
        List<WoundView> wounds = sheetLimb(limb).wounds();
        if (wounds.isEmpty()) {
            group.addChild(MedUi.label(0, 8, Component.translatable("gui.wfmedical.wound.none").getString()));
            return;
        }
        int col = 0;
        for (WoundView w : wounds) {
            int x = col * (WOUND_CELL + WOUND_GAP);
            group.addChild(MedUi.image(x, 0, WOUND_CELL, WOUND_CELL,
                    SDFRectTexture.of(woundColor(w)).setRadius(3), woundTooltip(w)));
            col++;
        }
    }

    private static TraumaType woundType(WoundView w) {
        TraumaRegistry reg = TraumaRegistry.active();
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


    private static void addTreatmentGrid(UIElement root) {
        root.addChild(new RefreshingGroup(LEFT_X, GRID_Y, 185, 95,
                MedInteractionScreen::treatmentSignature, MedInteractionScreen::buildTreatments));
    }

    private static Object treatmentSignature() {
        LimbType limb = MedicalUIParts.selectedLimb();
        StringBuilder sb = new StringBuilder();
        sb.append(targetId).append('|').append(ClientMedicalCache.hasActiveTreatment()).append('|')
                .append(limb).append('|').append(tourniquetApplied(limb)).append('|');
        for (ItemStack stack : MedicalUIParts.availableMedicalItems()) {
            sb.append(stack.getItem().getDescriptionId()).append(',');
        }
        return sb.toString();
    }

    private static void buildTreatments(UIElement group) {
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
                continue;
            }
            addTreatmentButton(group, stack, cellX(idx), cellY(idx));
            idx++;
        }
        if (idx == 0) {
            group.addChild(MedUi.label(0, 8, Component.translatable("gui.wfmedical.radial.no_items").getString()));
        }
    }

    private static int cellX(int idx) {
        return (idx % GRID_COLS) * (GRID_CELL + GRID_GAP);
    }

    private static int cellY(int idx) {
        return (idx / GRID_COLS) * (GRID_CELL + GRID_GAP);
    }

    private static void buildActiveTreatment(UIElement group) {
        group.addChild(new ProgressElement(0, 0, 165, 20));
        String cancel = Component.translatable("gui.wfmedical.treat.interrupt").getString();
        // LDLib2 has no ResourceBorderTexture.BUTTON_COMMON; its Button already paints the stock
        // LDLib button look (Sprites.RECT_RD and friends) and hosts the label itself.
        group.addChild(MedUi.textButton(43, 27, 80, 16, cancel, MedicalUIParts::requestCancelTreatment));
    }

    private static void addTreatmentButton(UIElement group, ItemStack stack, int x, int y) {
        GuiTextureGroup face = new GuiTextureGroup(
                SDFRectTexture.of(GRID_BG_COLOR).setRadius(GRID_RADIUS),
                new ItemStackTexture(stack));
        GuiTextureGroup hover = new GuiTextureGroup(face,
                SDFRectTexture.of(0x00000000).setRadius(GRID_RADIUS)
                        .setBorderColor(GRID_HOVER_COLOR).setStroke(2));
        group.addChild(MedUi.iconButton(x, y, GRID_CELL, GRID_CELL, face, hover,
                itemTooltip(stack),
                () -> MedicalUIParts.requestAction(stack, MedicalUIParts.selectedLimb(), targetId)));
    }

    private static void addTourniquetRemoveButton(UIElement group, int x, int y) {
        GuiTextureGroup face = new GuiTextureGroup(
                SDFRectTexture.of(TQ_REMOVE_BG_COLOR).setRadius(GRID_RADIUS),
                new ItemStackTexture(new ItemStack(ModItems.TOURNIQUET.get())));
        GuiTextureGroup hover = new GuiTextureGroup(face,
                SDFRectTexture.of(0x00000000).setRadius(GRID_RADIUS)
                        .setBorderColor(GRID_HOVER_COLOR).setStroke(2));
        group.addChild(MedUi.iconButton(x, y, GRID_CELL, GRID_CELL, face, hover,
                List.of(Component.translatable("gui.wfmedical.tourniquet.remove")),
                () -> MedicalUIParts.requestRemoveTourniquet(MedicalUIParts.selectedLimb(), targetId)));
    }

    private static List<Component> itemTooltip(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        return stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
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


    private static void addStatusReadout(UIElement root) {
        root.addChild(MedUi.centeredLabel(STATUS_CENTER_X, STATUS_Y,
                MedInteractionScreen::statusText, MedInteractionScreen::statusColor));
    }

    private static String statusText() {
        LimbType limb = MedicalUIParts.selectedLimb();
        if (limb == null) {
            return MedicalUIParts.stateName(sheetState()).getString();
        }
        LimbSummary s = sheetLimb(limb);
        String line = MedicalUIParts.limbName(limb).getString()
                + "  " + Math.round(s.healthPercent() * 100.0F) + "%";
        if (s.fracture()) {
            line += "  †";
        }
        // No UiText.escape here: MedUi labels render via Component.literal, which does no format
        // substitution, so a doubled %% would show up verbatim. (The HUD overlays still need it --
        // they go through TextTexture -> LocalizationUtils.format -> I18n.)
        return line;
    }

    private static int statusColor() {
        LimbType limb = MedicalUIParts.selectedLimb();
        if (limb == null) {
            return MedicalUIParts.stateColor(sheetState());
        }
        return MedicalUIParts.limbColor(sheetLimb(limb).healthPercent());
    }


    private static void addOverview(UIElement root) {
        int y = OVERVIEW_Y;
        Player player = Minecraft.getInstance().player;

        addLine(root, y, () -> {
            if (targetId < 0) {
                return "Health: " + Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth());
            }
            DerivedStats st = sheetStats();
            return "Health: " + Math.round(st.effectiveCurrentHealth()) + "/" + Math.round(st.effectiveMaxHealth());
        });
        y += OVERVIEW_LINE_H;

        addLine(root, y, () -> {
            MedicalSyncPacket snap = sheetSnapshot();
            double blood = snap == null ? 0.0 : snap.bloodMl();
            double maxBlood = snap == null ? 0.0 : snap.maxBloodMl();
            return "Blood: " + Math.round(blood) + "/" + Math.round(maxBlood) + " ml";
        });
        y += OVERVIEW_LINE_H;

        addLine(root, y, () ->
                "Pain: " + Math.round(sheetStats().totalPain() * 100.0F) + "%");
        y += OVERVIEW_LINE_H;

        addLine(root, y, () ->
                "Bleeding: " + fmt((float) sheetStats().totalBleeding()) + " ml/s");
        y += OVERVIEW_LINE_H;

        root.addChild(MedUi.label(OVERVIEW_X, y,
                () -> "State: " + MedicalUIParts.stateName(sheetState()).getString(),
                () -> MedicalUIParts.stateColor(sheetState())));
        y += OVERVIEW_LINE_H;

        addLine(root, y, () ->
                "Movement: " + Math.round(sheetStats().movementMultiplier() * 100.0F) + "%");
        y += OVERVIEW_LINE_H;

        addLine(root, y, () -> {
            DerivedStats st = sheetStats();
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

    private static void addLine(UIElement root, int y, Supplier<String> text) {
        root.addChild(MedUi.label(OVERVIEW_X, y, text, null));
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }


    /** Hosts the shared HUD treatment-progress bar inside the sheet. */
    private static final class ProgressElement extends UIElement {

        private ProgressElement(int x, int y, int width, int height) {
            MedUi.at(this, x, y, width, height);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            super.drawBackgroundAdditional(guiContext);
            ActionProgressOverlay.drawBar(guiContext.graphics,
                    Math.round(getPositionX()), Math.round(getPositionY()) + 11,
                    Math.round(getSizeWidth()), guiContext.partialTick);
        }
    }

    /**
     * A container that rebuilds its children whenever a cheap signature of the underlying state changes.
     *
     * <p>LDLib 1.x drove this from {@code WidgetGroup.updateScreen()}; the LDLib2 equivalent is
     * {@link #screenTick()}, which ModularUI pumps once per client tick. The rebuild must happen there
     * and not from a draw hook: layout is solved before the draw pass, so children added mid-draw have
     * no computed rect yet and paint at a stale one for a frame -- visible as a flash on every change.
     */
    private static final class RefreshingGroup extends UIElement {

        private final Supplier<Object> signature;
        private final Consumer<UIElement> builder;
        private Object last;

        private RefreshingGroup(int x, int y, int width, int height,
                                Supplier<Object> signature, Consumer<UIElement> builder) {
            this.signature = signature;
            this.builder = builder;
            MedUi.at(this, x, y, width, height);
            builder.accept(this);
            this.last = signature.get();
        }

        @Override
        public void screenTick() {
            super.screenTick();
            Object current = signature.get();
            if (!Objects.equals(current, last)) {
                last = current;
                clearAllChildren();
                builder.accept(this);
            }
        }
    }
}
