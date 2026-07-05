package com.warfactory.medical.client.screen;

import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.item.MedicalItem;
import com.warfactory.medical.network.ClientMedicalCache;
import com.warfactory.medical.network.MedicalActionPacket;
import com.warfactory.medical.network.MedicalNetworking;
import com.warfactory.medical.network.MedicalSyncPacket;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import com.warfactory.medical.network.SetTargetLimbPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared, CLIENT-ONLY widget builders and helpers used by BOTH {@link CharacterSheetUI} and
 * {@link RadialMenuUI}. Everything here reads only the client-synced {@link ClientMedicalCache} snapshot
 * and SENDS request packets; it never mutates medical state (the server is authoritative).
 *
 * <p><b>Public API for the UI agents:</b></p>
 * <ul>
 *   <li>Colors: {@link #limbColor(float)}, {@link #stateColor(HealthState)}.</li>
 *   <li>Selection: {@link #selectedLimb()}, {@link #selectLimb(LimbType)}.</li>
 *   <li>Actions: {@link #requestAction(ItemStack, LimbType)}, {@link #availableMedicalItems()}.</li>
 *   <li>Snapshot reads (never NPE): {@link #stats()}, {@link #limbSummaries()}, {@link #limbSummary(LimbType)}.</li>
 *   <li>Labels: {@link #limbName(LimbType)}, {@link #stateName(HealthState)}.</li>
 *   <li>Widgets: {@link #bodyDiagram(int, int, int, int)}.</li>
 * </ul>
 *
 * <p>All live widgets returned here are marked {@code setClientSideWidget()} so their suppliers re-read the
 * client cache every tick/frame (required for client-only UIs — see the LDLib cheatsheet Q4).</p>
 */
public final class MedicalUIParts {

    /** ARGB opaque-white; used for the selected-limb highlight border. */
    private static final int HIGHLIGHT_COLOR = 0xFFFFFFFF;
    /** Border thickness (px) of the selected-limb highlight. */
    private static final int HIGHLIGHT_BORDER = 2;

    private MedicalUIParts() {
    }

    // ------------------------------------------------------------------ colors

    /**
     * Map a limb health fraction to a tile color along a red -> yellow -> green gradient.
     *
     * @param healthPercent01 limb health in {@code [0,1]} (clamped); 0 = destroyed, 1 = full
     * @return opaque ARGB color (0xFFRRGGBB): red at 0, yellow at 0.5, green at 1
     */
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

    /**
     * Map a high-level {@link HealthState} to a representative ARGB color for badges / borders.
     *
     * @param state the synced health state (nullable -> treated as HEALTHY-ish white)
     * @return opaque ARGB color
     */
    public static int stateColor(HealthState state) {
        if (state == null) {
            return 0xFFFFFFFF;
        }
        return switch (state) {
            case HEALTHY -> 0xFF33CC33;
            case CRITICAL -> 0xFFE0A020;
            case KNOCKED_DOWN -> 0xFFCC3030;
            case DEAD -> 0xFF404040;
        };
    }

    // ------------------------------------------------------------------ selection

    /** @return the UI-local selected limb (nullable), mirroring the server targeting hint. */
    public static LimbType selectedLimb() {
        return ClientMedicalCache.selectedLimb();
    }

    /**
     * Select a limb: updates the client-local highlight AND sends a {@link SetTargetLimbPacket} so the
     * server biases subsequent treatments toward it. Passing {@code null} clears the preference.
     *
     * @param limb the limb to target, or null to clear
     */
    public static void selectLimb(LimbType limb) {
        ClientMedicalCache.setSelectedLimb(limb);
        MedicalNetworking.sendToServer(new SetTargetLimbPacket(limb));
    }

    // ------------------------------------------------------------------ actions

    /**
     * Request the server to begin a treatment with the given medical item, targeting {@code limb}. Sends a
     * {@link MedicalActionPacket} keyed by the stack's registry name; the server validates the request
     * (the client never applies the treatment itself). No-op for empty / non-registered / non-medical
     * stacks.
     *
     * @param medicalItemStack the medical item stack to use (its registry id identifies the treatment)
     * @param limb             the targeted limb, or null to let the server auto-pick
     */
    public static void requestAction(ItemStack medicalItemStack, LimbType limb) {
        if (medicalItemStack == null || medicalItemStack.isEmpty()
                || !(medicalItemStack.getItem() instanceof MedicalItem)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(medicalItemStack.getItem());
        if (id == null) {
            return;
        }
        MedicalNetworking.sendToServer(new MedicalActionPacket(id, limb));
    }

    /**
     * The distinct {@link MedicalItem} stacks currently in the local player's inventory, for building
     * action buttons / radial wedges. Deduplicated by item type; empty stacks are skipped. Returns an
     * empty list when there is no local player.
     *
     * @return a fresh, mutable list of one representative stack per distinct medical item
     */
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

    // ------------------------------------------------------------------ safe snapshot reads

    /** @return the latest synced {@link DerivedStats}; never null (healthy defaults if none received). */
    public static DerivedStats stats() {
        return ClientMedicalCache.stats();
    }

    /**
     * The per-limb summary array from the latest snapshot, indexed to match {@link LimbType#VALUES} order
     * when possible. Falls back to a full array of healthy defaults when no snapshot exists.
     *
     * @return a per-limb summary array of length {@code LimbType.VALUES.length}; never null
     */
    public static LimbSummary[] limbSummaries() {
        LimbType[] all = LimbType.VALUES;
        LimbSummary[] out = new LimbSummary[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = limbSummary(all[i]);
        }
        return out;
    }

    /**
     * The synced summary for one limb, or a healthy default (100%, no bleeding/pain/fracture) when no
     * snapshot has arrived or the limb is missing from it.
     *
     * @param limb the limb to look up (must not be null)
     * @return a never-null {@link LimbSummary} for that limb
     */
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
        return new LimbSummary(limb, 1.0F, 0.0F, 0.0F, false);
    }

    // ------------------------------------------------------------------ labels

    /** @return the display name Component for a limb ({@code gui.wfmedical.limb.<name>}). */
    public static Component limbName(LimbType limb) {
        return Component.translatable("gui.wfmedical.limb." + limb.name().toLowerCase(Locale.ROOT));
    }

    /** @return the display name Component for a health state ({@code gui.wfmedical.state.<name>}). */
    public static Component stateName(HealthState state) {
        HealthState s = state == null ? HealthState.HEALTHY : state;
        return Component.translatable("gui.wfmedical.state." + s.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ body diagram

    /**
     * Build a simple humanoid body diagram from six colored limb tiles (HEAD, TORSO, LEFT_ARM, RIGHT_ARM,
     * LEFT_LEG, RIGHT_LEG) laid out in a body shape within the given rectangle. Each tile:
     * <ul>
     *   <li>is filled with a live color from that limb's synced health% ({@link #limbColor(float)}), read
     *       every frame via a {@code Supplier<IGuiTexture>} (marked client-side);</li>
     *   <li>is clickable to {@link #selectLimb(LimbType)} that limb;</li>
     *   <li>shows a white highlight border while it is the {@link #selectedLimb()};</li>
     *   <li>shows a hover tooltip (limb name, health%, bleeding, pain, fracture), refreshed each tick.</li>
     * </ul>
     * "Left"/"Right" are the anatomical (player-perspective) sides and are drawn on the diagram's left /
     * right respectively. The whole group is marked {@code setClientSideWidget()} so live suppliers update.
     *
     * @param x      group left, relative to the parent it will be added to
     * @param y      group top, relative to the parent
     * @param width  total diagram width in px
     * @param height total diagram height in px
     * @return a {@link WidgetGroup} the caller adds to a {@code ui.mainGroup}
     */
    public static WidgetGroup bodyDiagram(int x, int y, int width, int height) {
        WidgetGroup group = new WidgetGroup(x, y, width, height);

        int colW = Math.max(1, width / 3);
        int rowH = Math.max(1, height / 4);
        int halfCol = Math.max(1, colW / 2);
        int gap = 1;

        // HEAD: top-center.
        addLimbTile(group, LimbType.HEAD, colW + gap, gap, colW - 2 * gap, rowH - 2 * gap);
        // LEFT_ARM: left column, upper body row.
        addLimbTile(group, LimbType.LEFT_ARM, gap, rowH + gap, colW - 2 * gap, rowH - 2 * gap);
        // TORSO: center column, two upper-body rows tall.
        addLimbTile(group, LimbType.TORSO, colW + gap, rowH + gap, colW - 2 * gap, 2 * rowH - 2 * gap);
        // RIGHT_ARM: right column, upper body row.
        addLimbTile(group, LimbType.RIGHT_ARM, 2 * colW + gap, rowH + gap, colW - 2 * gap, rowH - 2 * gap);
        // LEFT_LEG: bottom row, left half under the torso.
        addLimbTile(group, LimbType.LEFT_LEG, colW + gap, 3 * rowH + gap, halfCol - 2 * gap, rowH - 2 * gap);
        // RIGHT_LEG: bottom row, right half under the torso.
        addLimbTile(group, LimbType.RIGHT_LEG, colW + halfCol + gap, 3 * rowH + gap, halfCol - 2 * gap, rowH - 2 * gap);

        group.setClientSideWidget();
        return group;
    }

    /**
     * Add one limb tile (color fill + selection border + transparent click button) to {@code group}.
     * Coordinates are relative to {@code group}.
     */
    private static void addLimbTile(WidgetGroup group, LimbType limb, int tx, int ty, int tw, int th) {
        if (tw <= 0 || th <= 0) {
            return;
        }

        // Live color fill (drawn first, behind).
        ImageWidget fill = new ImageWidget(tx, ty, tw, th,
                (java.util.function.Supplier<IGuiTexture>) () ->
                        new ColorRectTexture(limbColor(limbSummary(limb).healthPercent())));
        fill.setClientSideWidget();
        group.addWidget(fill);

        // Selection highlight border (only visible while this limb is selected).
        ImageWidget border = new ImageWidget(tx, ty, tw, th,
                (java.util.function.Supplier<IGuiTexture>) () ->
                        selectedLimb() == limb
                                ? new ColorBorderTexture(HIGHLIGHT_BORDER, HIGHLIGHT_COLOR)
                                : IGuiTexture.EMPTY);
        border.setClientSideWidget();
        group.addWidget(border);

        // Transparent click target on top (no background); refreshes its tooltip each tick.
        ButtonWidget click = new ButtonWidget(tx, ty, tw, th,
                (ClickData cd) -> selectLimb(limb)) {
            @Override
            public void updateScreen() {
                super.updateScreen();
                setHoverTooltips(limbTooltip(limb));
            }
        };
        click.setHoverTooltips(limbTooltip(limb));
        click.setClientSideWidget();
        group.addWidget(click);
    }

    /** Build the hover tooltip lines for a limb from the current snapshot. */
    private static List<Component> limbTooltip(LimbType limb) {
        LimbSummary s = limbSummary(limb);
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
