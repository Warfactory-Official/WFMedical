package com.warfactory.medical.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.warfactory.medical.client.TreatmentInteractions;
import com.warfactory.medical.core.limb.LimbStatus;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * CLIENT-ONLY MineMenu-style limb selection wheel. Each damaged limb is a donut pie-slice tinted by its
 * severity (red→green), carrying a mini body silhouette with that limb lit and injury glyphs (bleed / fracture
 * / pain). The slice under the mouse (by angle from centre, outside the deadzone) highlights and grows; a click
 * applies the held treatment to that limb via {@link TreatmentInteractions#sendAction}. Opens with a short
 * sweep/scale animation. Never mutates medical state – it only sends the authoritative request.
 *
 * <p>Slice geometry: slice {@code i} of {@code n} spans the angular sector starting at {@code -PI/2} (straight
 * up) and sweeping clockwise, so item 0 sits at the top exactly like the legacy radial.</p>
 */
public final class LimbWheelScreen extends Screen {

    private static final float BASE_ANGLE = -Mth.HALF_PI;
    private static final float R_IN = 42.0F;
    private static final float R_OUT = 98.0F;
    private static final float R_MID = (R_IN + R_OUT) / 2.0F;
    private static final float HOVER_GROW = 10.0F;
    private static final float SLICE_PAD = 0.045F;
    private static final long OPEN_MS = 190L;

    private static final int SLICE_BG = 0xB0161B22;
    private static final int SLICE_BG_HOVER = 0xE0232C37;
    private static final int RIM_HOVER = 0xD0FFE070;
    private static final int HUB_BG = 0xE00E1116;

    private final int targetEntityId;
    private final ResourceLocation itemId;
    private final ItemStack itemIcon;
    private final List<Entry> entries = new ArrayList<>();

    private long openStart;
    private int hovered = -1;

    private record Entry(LimbType limb, LimbSummary summary) {
    }

    public LimbWheelScreen(int targetEntityId, ResourceLocation itemId, LimbSummary[] limbs) {
        super(Component.translatable("gui.wfmedical.wheel.title"));
        this.targetEntityId = targetEntityId;
        this.itemId = itemId;
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        this.itemIcon = item == null ? ItemStack.EMPTY : new ItemStack(item);
        if (limbs != null) {
            for (LimbSummary s : limbs) {
                if (LimbStatus.isDamaged(s)) {
                    entries.add(new Entry(s.limb(), s));
                }
            }
        }
    }

    @Override
    protected void init() {
        this.openStart = Util.getMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Close if the wheel became meaningless (no slices, or the item left the medic's hands).
        if (entries.isEmpty() || !stillHoldingItem()) {
            onClose();
            return;
        }
        renderBackground(g);

        float cx = this.width / 2.0F;
        float cy = this.height / 2.0F;
        float open = easeOut(Mth.clamp((Util.getMillis() - openStart) / (float) OPEN_MS, 0.0F, 1.0F));
        int n = entries.size();
        float step = Mth.TWO_PI / n;
        this.hovered = pickHovered(mouseX, mouseY, cx, cy, step, n);

        // Pass 1: slice backgrounds + severity tint + hovered rim.
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            boolean hot = i == hovered;
            float a0 = BASE_ANGLE + i * step + SLICE_PAD;
            float a1 = BASE_ANGLE + (i + 1) * step - SLICE_PAD;
            float rIn = R_IN * open;
            float rOut = (R_OUT + (hot ? HOVER_GROW : 0.0F)) * open;

            fillSector(g, cx, cy, rIn, rOut, a0, a1, hot ? SLICE_BG_HOVER : SLICE_BG);
            int tint = withAlpha(MedicalUIParts.limbColor(e.summary.healthPercent()), hot ? 0.55F : 0.32F);
            fillSector(g, cx, cy, rIn, rOut, a0, a1, tint);
            if (hot) {
                fillSector(g, cx, cy, rOut - 2.5F, rOut, a0, a1, RIM_HOVER);
            }
        }

        // Pass 2: slice contents, faded in over the animation.
        if (open > 0.35F) {
            float contentAlpha = Mth.clamp((open - 0.35F) / 0.5F, 0.0F, 1.0F);
            for (int i = 0; i < n; i++) {
                Entry e = entries.get(i);
                float mid = BASE_ANGLE + (i + 0.5F) * step;
                float px = cx + Mth.cos(mid) * R_MID * open;
                float py = cy + Mth.sin(mid) * R_MID * open;
                drawSliceContent(g, e, px, py, contentAlpha, i == hovered);
            }
        }

        // Centre hub (drawn last, above the ring): held item + target + hovered-limb readout.
        drawHub(g, cx, cy, open);
    }

    private void drawSliceContent(GuiGraphics g, Entry e, float px, float py, float alpha, boolean hot) {
        drawBody(g, px, py - 7.0F, e.limb, alpha, hot);

        int nameColor = withAlpha(hot ? 0xFFFFFFFF : 0xFFDDE3EA, alpha);
        drawScaledCentered(g, MedicalUIParts.limbName(e.limb), px, py + 8.0F, 0.85F, nameColor);

        int pct = Math.round(e.summary.healthPercent() * 100.0F);
        int hpColor = withAlpha(MedicalUIParts.limbColor(e.summary.healthPercent()), alpha);
        drawScaledCentered(g, Component.literal(pct + "%"), px, py + 17.0F, 0.8F, hpColor);

        drawGlyphs(g, e.summary, px, py + 26.0F, alpha);
    }

    /**
     * Mini humanoid silhouette centred at {@code (cx, cy)} with {@code highlight} lit and the rest dimmed.
     * "Left"/"Right" are anatomical, drawn on the viewer's left/right respectively (matching the body diagram).
     */
    private void drawBody(GuiGraphics g, float cxf, float cyf, LimbType highlight, float alpha, boolean hot) {
        int cx = Math.round(cxf);
        int cy = Math.round(cyf);
        int base = withAlpha(0xFF464F5C, alpha);
        int lit = withAlpha(hot ? 0xFFFFE070 : 0xFFF2F6FA, alpha);
        // head
        g.fill(cx - 3, cy - 12, cx + 3, cy - 7, color(highlight, LimbType.HEAD, base, lit));
        // torso
        g.fill(cx - 4, cy - 6, cx + 4, cy + 3, color(highlight, LimbType.TORSO, base, lit));
        // arms
        g.fill(cx - 7, cy - 6, cx - 5, cy + 2, color(highlight, LimbType.LEFT_ARM, base, lit));
        g.fill(cx + 5, cy - 6, cx + 7, cy + 2, color(highlight, LimbType.RIGHT_ARM, base, lit));
        // legs
        g.fill(cx - 4, cy + 3, cx - 1, cy + 11, color(highlight, LimbType.LEFT_LEG, base, lit));
        g.fill(cx + 1, cy + 3, cx + 4, cy + 11, color(highlight, LimbType.RIGHT_LEG, base, lit));
    }

    private static int color(LimbType highlight, LimbType part, int base, int lit) {
        return highlight == part ? lit : base;
    }

    /**
     * Injury glyphs (bleed drop / fracture bone / pain spark) in a centred row under the limb readout.
     */
    private void drawGlyphs(GuiGraphics g, LimbSummary s, float cxf, float yf, float alpha) {
        int count = (s.bleeding() > 0.0F ? 1 : 0) + (s.fracture() ? 1 : 0) + (s.pain() > 0.0F ? 1 : 0);
        if (count == 0) {
            return;
        }
        int cx = Math.round(cxf);
        int y = Math.round(yf);
        int x = cx - (count * 9) / 2;
        if (s.bleeding() > 0.0F) {
            drawDrop(g, x, y, withAlpha(0xFFCC3030, alpha));
            x += 9;
        }
        if (s.fracture()) {
            drawBone(g, x, y, withAlpha(0xFFD8DCE0, alpha));
            x += 9;
        }
        if (s.pain() > 0.0F) {
            drawSpark(g, x, y, withAlpha(0xFFE7C21E, alpha));
        }
    }

    private static void drawDrop(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 2, y, x + 4, y + 2, c);
        g.fill(x + 1, y + 2, x + 5, y + 6, c);
        g.fill(x + 2, y + 6, x + 4, y + 7, c);
    }

    private static void drawBone(GuiGraphics g, int x, int y, int c) {
        g.fill(x, y + 2, x + 7, y + 4, c);
        g.fill(x, y + 1, x + 2, y + 5, c);
        g.fill(x + 5, y + 1, x + 7, y + 5, c);
    }

    private static void drawSpark(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 3, y, x + 4, y + 7, c);
        g.fill(x, y + 3, x + 7, y + 4, c);
    }

    private void drawHub(GuiGraphics g, float cx, float cy, float open) {
        fillSector(g, cx, cy, 0.0F, R_IN * 0.92F * open, 0.0F, Mth.TWO_PI, HUB_BG);
        if (open < 0.5F) {
            return;
        }
        int icx = Math.round(cx);
        int icy = Math.round(cy);
        Component target = targetName();
        g.drawCenteredString(this.font, target, icx, icy - 25, 0xFFFFFFFF);
        if (!itemIcon.isEmpty()) {
            g.renderFakeItem(itemIcon, icx - 8, icy - 14);
        }
        if (hovered >= 0 && hovered < entries.size()) {
            Entry e = entries.get(hovered);
            g.drawCenteredString(this.font, MedicalUIParts.limbName(e.limb), icx, icy + 6,
                    MedicalUIParts.limbColor(e.summary.healthPercent()));
            int pct = Math.round(e.summary.healthPercent() * 100.0F);
            g.drawCenteredString(this.font, Component.literal("HP " + pct + "%"), icx, icy + 16, 0xFFCCCCCC);
        } else {
            g.drawCenteredString(this.font, Component.translatable("gui.wfmedical.wheel.select"),
                    icx, icy + 10, 0xFFAAAAAA);
        }
    }

    private Component targetName() {
        if (targetEntityId < 0) {
            return Component.translatable("gui.wfmedical.wheel.self");
        }
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(targetEntityId);
        return e != null ? e.getName() : Component.translatable("gui.wfmedical.wheel.target");
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        float cx = this.width / 2.0F;
        float cy = this.height / 2.0F;
        int n = entries.size();
        int idx = pickHovered(mx, my, cx, cy, n == 0 ? 1.0F : Mth.TWO_PI / n, n);
        if (idx >= 0) {
            TreatmentInteractions.sendAction(itemId, entries.get(idx).limb(), targetEntityId);
        }
        onClose();
        return true;
    }

    private int pickHovered(double mx, double my, float cx, float cy, float step, int n) {
        if (n <= 0) {
            return -1;
        }
        double dx = mx - cx;
        double dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < R_IN * 0.9F) {
            return -1; // centre deadzone
        }
        double rel = Math.atan2(dy, dx) - BASE_ANGLE;
        rel = ((rel % (2.0 * Math.PI)) + 2.0 * Math.PI) % (2.0 * Math.PI);
        int idx = (int) (rel / step);
        return Math.min(idx, n - 1);
    }

    // ------------------------------------------------------------------ drawing helpers

    /**
     * Filled annular sector (donut wedge) as a triangle strip in the given flat colour, with blending.
     * {@code rIn = 0} degenerates to a filled pie/disc (used for the hub).
     */
    private static void fillSector(GuiGraphics g, float cx, float cy, float rIn, float rOut,
                                   float a0, float a1, int argb) {
        if (rOut <= 0.05F || a1 <= a0) {
            return;
        }
        float alpha = ((argb >>> 24) & 0xFF) / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float gr = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        Matrix4f mat = g.pose().last().pose();
        int seg = Math.max(2, Mth.ceil((a1 - a0) / 0.09F));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            float t = a0 + (a1 - a0) * i / seg;
            float cos = Mth.cos(t);
            float sin = Mth.sin(t);
            buf.vertex(mat, cx + cos * rOut, cy + sin * rOut, 0.0F).color(r, gr, b, alpha).endVertex();
            buf.vertex(mat, cx + cos * rIn, cy + sin * rIn, 0.0F).color(r, gr, b, alpha).endVertex();
        }
        Tesselator.getInstance().end();
        RenderSystem.disableBlend();
    }

    private void drawScaledCentered(GuiGraphics g, Component text, float cx, float cy, float scale, int color) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0.0F);
        ps.scale(scale, scale, 1.0F);
        g.drawCenteredString(this.font, text, 0, -this.font.lineHeight / 2, color);
        ps.popPose();
    }

    private boolean stillHoldingItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        return item != null
                && (mc.player.getMainHandItem().getItem() == item || mc.player.getOffhandItem().getItem() == item);
    }

    private static float easeOut(float t) {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv; // cubic ease-out
    }

    /**
     * Replace the alpha channel of {@code argb} with {@code (originalAlpha * factor)}.
     */
    private static int withAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(factor, 0.0F, 1.0F));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
