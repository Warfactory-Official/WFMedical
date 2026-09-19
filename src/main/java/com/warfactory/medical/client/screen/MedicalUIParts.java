package com.warfactory.medical.client.screen;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.item.MedicalItem;
import com.warfactory.medical.network.*;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;
import java.util.function.Function;

public final class MedicalUIParts {

    private static final int HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int HIGHLIGHT_BORDER = 2;

    private MedicalUIParts() {
    }


    public static int limbColor(float healthPercent01) {
        float p = healthPercent01;
        if (p < 0.0F) {
            p = 0.0F;
        } else if (p > 1.0F) {
            p = 1.0F;
        }
        int r;
        int g;
        if (p < 0.5F) {
            r = 255;
            g = Math.round(255.0F * (p * 2.0F));
        } else {
            r = Math.round(255.0F * ((1.0F - p) * 2.0F));
            g = 255;
        }
        return 0xFF000000 | (r << 16) | (g << 8);
    }

    public static int stateColor(HealthState state) {
        if (state == null) {
            return 0xFFFFFFFF;
        }
        return switch (state) {
            case HEALTHY -> 0xFF33CC33;
            case CRITICAL -> 0xFFE0A020;
            case UNCONSCIOUS -> 0xFFCC3030;
            case DEAD -> 0xFF404040;
        };
    }


    public static LimbType selectedLimb() {
        return ClientMedicalCache.selectedLimb();
    }

    public static void selectLimb(LimbType limb) {
        ClientMedicalCache.setSelectedLimb(limb);
    }


    public static void requestAction(ItemStack medicalItemStack, LimbType limb) {
        requestAction(medicalItemStack, limb, -1);
    }

    public static void requestAction(ItemStack medicalItemStack, LimbType limb, int targetEntityId) {
        if (medicalItemStack == null || medicalItemStack.isEmpty()
                || !(medicalItemStack.getItem() instanceof MedicalItem)) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(medicalItemStack.getItem());
        if (id == null) {
            return;
        }
        MedicalNetworking.sendToServer(new MedicalActionPacket(id, limb, targetEntityId));
    }

    public static void requestResuscitate(int targetEntityId) {
        if (targetEntityId < 0) {
            return;
        }
        MedicalNetworking.sendToServer(new ResuscitatePacket(targetEntityId));
    }

    public static void requestRemoveTourniquet(LimbType limb) {
        requestRemoveTourniquet(limb, -1);
    }

    public static void requestRemoveTourniquet(LimbType limb, int targetEntityId) {
        if (limb == null) {
            return;
        }
        MedicalNetworking.sendToServer(new RemoveTourniquetPacket(limb, targetEntityId));
    }

    public static void requestCancelTreatment() {
        MedicalNetworking.sendToServer(new CancelTreatmentPacket());
    }

    public static List<ItemStack> availableMedicalItems() {
        List<ItemStack> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return out;
        }
        Inventory inv = mc.player.getInventory();
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof MedicalItem && seen.add(stack.getItem())) {
                out.add(stack);
            }
        }
        return out;
    }


    public static DerivedStats stats() {
        return ClientMedicalCache.stats();
    }

    public static LimbSummary[] limbSummaries() {
        LimbType[] all = LimbType.VALUES;
        LimbSummary[] out = new LimbSummary[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = limbSummary(all[i]);
        }
        return out;
    }

    public static LimbSummary limbSummary(LimbType limb) {
        MedicalSyncPacket snap = ClientMedicalCache.get();
        if (snap != null) {
            LimbSummary[] limbs = snap.limbs();
            if (limbs != null) {
                for (LimbSummary s : limbs) {
                    if (s != null && s.limb() == limb) {
                        return s;
                    }
                }
            }
        }
        return new LimbSummary(limb, 1.0F, 0.0F, 0.0F, false, java.util.List.of());
    }


    public static Component limbName(LimbType limb) {
        return Component.translatable("gui.wfmedical.limb." + limb.name().toLowerCase(Locale.ROOT));
    }

    public static Component stateName(HealthState state) {
        HealthState s = state == null ? HealthState.HEALTHY : state;
        return Component.translatable("gui.wfmedical.state." + s.name().toLowerCase(Locale.ROOT));
    }


    public static UIElement bodyDiagram(int x, int y, int width, int height) {
        UIElement group = new UIElement();
        group.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(x).top(y).width(width).height(height));

        int colW = Math.max(1, width / 3);
        int rowH = Math.max(1, height / 4);
        int halfCol = Math.max(1, colW / 2);
        int gap = 1;

        addLimbTile(group, LimbType.HEAD, colW + gap, gap, colW - 2 * gap, rowH - 2 * gap);
        addLimbTile(group, LimbType.LEFT_ARM, gap, rowH + gap, colW - 2 * gap, rowH - 2 * gap);
        addLimbTile(group, LimbType.TORSO, colW + gap, rowH + gap, colW - 2 * gap, 2 * rowH - 2 * gap);
        addLimbTile(group, LimbType.RIGHT_ARM, 2 * colW + gap, rowH + gap, colW - 2 * gap, rowH - 2 * gap);
        addLimbTile(group, LimbType.LEFT_LEG, colW + gap, 3 * rowH + gap, halfCol - 2 * gap, rowH - 2 * gap);
        addLimbTile(group, LimbType.RIGHT_LEG, colW + halfCol + gap, 3 * rowH + gap, halfCol - 2 * gap, rowH - 2 * gap);

        return group;
    }

    public static void addLimbTile(UIElement group, LimbType limb, int tx, int ty, int tw, int th) {
        addLimbTile(group, limb, tx, ty, tw, th, MedicalUIParts::limbSummary);
    }

    public static void addLimbTile(UIElement group, LimbType limb, int tx, int ty, int tw, int th,
                                   Function<LimbType, LimbSummary> source) {
        addLimbTile(group, limb, tx, ty, tw, th, source, ClientPlayerSkins.forEntity(-1));
    }

    public static void addLimbTile(UIElement group, LimbType limb, int tx, int ty, int tw, int th,
                                   Function<LimbType, LimbSummary> source, ClientPlayerSkins.Skin skin) {
        if (tw <= 0 || th <= 0) {
            return;
        }
        group.addChild(new LimbTile(limb, source, skin, tx, ty, tw, th));
    }

    /**
     * One clickable body-part tile: the skin-and-damage fill, a selection border while this limb is the
     * selected one, and a live tooltip.
     *
     * <p>In LDLib 1.x this was three stacked widgets (a static ImageWidget, a supplier-driven ImageWidget
     * for the border, and a ButtonWidget for the click + tooltip). LDLib2's UIElement has no
     * supplier-backed image widget, so the three collapse into one element that paints the border itself
     * and refreshes its tooltip each frame. Same pixels, one element instead of three.
     */
    public static final class LimbTile extends UIElement {

        private static final IGuiTexture SELECTION =
                new ColorBorderTexture(HIGHLIGHT_BORDER, HIGHLIGHT_COLOR);

        private final LimbType limb;
        private final Function<LimbType, LimbSummary> source;

        private LimbTile(LimbType limb, Function<LimbType, LimbSummary> source, ClientPlayerSkins.Skin skin,
                         int tx, int ty, int tw, int th) {
            this.limb = limb;
            this.source = source;
            layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(tx).top(ty).width(tw).height(th));
            style(style -> style
                    .background(ClientPlayerSkins.limbTile(limb, skin, () -> source.apply(limb).healthPercent()))
                    .tooltips(Tooltips.of(limbTooltip(limb, source))));
            addEventListener(UIEvents.MOUSE_DOWN, event -> selectLimb(limb));
        }

        @Override
        public void screenTick() {
            super.screenTick();
            // The old ButtonWidget refreshed its tooltip from updateScreen(); this is the equivalent hook.
            getStyle().tooltips(Tooltips.of(limbTooltip(limb, source)));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            super.drawBackgroundAdditional(guiContext);
            if (selectedLimb() == limb) {
                guiContext.drawTexture(SELECTION, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
            }
        }
    }

    public static List<Component> limbTooltip(LimbType limb) {
        return limbTooltip(limb, MedicalUIParts::limbSummary);
    }

    public static List<Component> limbTooltip(LimbType limb, Function<LimbType, LimbSummary> source) {
        LimbSummary s = source.apply(limb);
        List<Component> lines = new ArrayList<>(4);
        lines.add(limbName(limb));
        lines.add(Component.translatable("gui.wfmedical.health")
                .append(Component.literal(": " + Math.round(s.healthPercent() * 100.0F) + "%")));
        lines.add(Component.translatable("gui.wfmedical.bleeding")
                .append(Component.literal(": " + String.format(Locale.ROOT, "%.1f", s.bleeding()))));
        lines.add(Component.translatable("gui.wfmedical.pain")
                .append(Component.literal(": " + String.format(Locale.ROOT, "%.1f", s.pain()))));
        if (s.fracture()) {
            lines.add(Component.translatable("gui.wfmedical.fracture"));
        }
        return lines;
    }
}
