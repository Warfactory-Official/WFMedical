package com.warfactory.medical.client.screen;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The handful of LDLib 1.x widgets this mod's screens actually used, rebuilt on LDLib2's {@link UIElement}.
 *
 * <p>LDLib2 replaced the absolute-positioned {@code Widget} tree with a Taffy-laid-out element tree and
 * dropped {@code LabelWidget}/{@code ImageWidget}/{@code ButtonWidget} along with the per-frame
 * {@code updateScreen()} hook they refreshed themselves from. The screens here were all authored against
 * fixed pixel coordinates, so everything is pinned with {@code position: absolute} to keep the existing
 * layout exactly, and the live text/colour suppliers are re-read from {@code screenTick()} -- LDLib2's
 * once-per-client-tick hook, and the direct counterpart of the old {@code updateScreen()}. Note these
 * updates must not run from a draw hook: text width feeds {@code widthFitContent} and the centred label
 * rewrites its own {@code left}, and layout is solved before the draw pass, so a mid-draw change paints
 * one frame at the stale rect.
 */
public final class MedUi {

    private MedUi() {
    }

    /** Pins an element at a fixed pixel rect, matching the old {@code new Widget(x, y, w, h)}. */
    public static <T extends UIElement> T at(T element, float x, float y, float width, float height) {
        element.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(x).top(y).width(width).height(height));
        return element;
    }

    /** Pins an element at a fixed position, leaving its size to fit its content. */
    public static <T extends UIElement> T at(T element, float x, float y) {
        element.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(x).top(y).widthFitContent().heightFitContent());
        return element;
    }

    public static Label label(float x, float y, String text) {
        Label label = new Label();
        label.setValue(Component.literal(text));
        return at(label, x, y);
    }

    /**
     * A label whose text (and optionally colour) is re-read each client tick, standing in for LDLib 1.x's
     * supplier-backed {@code LabelWidget} plus its {@code updateScreen()} colour override.
     */
    public static Label label(float x, float y, Supplier<String> text, IntSupplier color) {
        Label label = new Label() {
            @Override
            public void screenTick() {
                super.screenTick();
                setValue(Component.literal(text.get()));
                if (color != null) {
                    textStyle(style -> style.textColor(color.getAsInt()));
                }
            }
        };
        label.setValue(Component.literal(text.get()));
        if (color != null) {
            label.textStyle(style -> style.textColor(color.getAsInt()));
        }
        return at(label, x, y);
    }

    /**
     * A supplier-backed label horizontally centred on {@code centerX}, replacing the old
     * {@code setSelfPosition} recentring done from {@code updateScreen()}.
     */
    public static Label centeredLabel(float centerX, float y, Supplier<String> text, IntSupplier color) {
        Label label = new Label() {
            @Override
            public void screenTick() {
                super.screenTick();
                String current = text.get();
                setValue(Component.literal(current));
                if (color != null) {
                    textStyle(style -> style.textColor(color.getAsInt()));
                }
                float width = Minecraft.getInstance().font.width(current);
                layout(layout -> layout.left(centerX - width / 2.0F));
            }
        };
        label.setValue(Component.literal(text.get()));
        if (color != null) {
            label.textStyle(style -> style.textColor(color.getAsInt()));
        }
        return at(label, centerX, y);
    }

    /** A static image box with an optional tooltip -- the old {@code ImageWidget}. */
    public static UIElement image(float x, float y, float width, float height,
                                  IGuiTexture texture, List<Component> tooltips) {
        UIElement element = new UIElement();
        element.style(style -> {
            style.background(texture);
            if (tooltips != null && !tooltips.isEmpty()) {
                style.tooltips(Tooltips.of(tooltips));
            }
        });
        return at(element, x, y, width, height);
    }

    /**
     * An icon button -- the old {@code ButtonWidget} with a face texture, a hover border and a tooltip.
     * The face/hover textures are installed as the button's own base/hover backgrounds so LDLib2 does the
     * state swapping, and the text slot is switched off since these buttons are icon-only.
     */
    public static Button iconButton(float x, float y, float width, float height,
                                    IGuiTexture face, IGuiTexture hover,
                                    List<Component> tooltips, Runnable onClick) {
        Button button = new Button();
        button.noText();
        button.buttonStyle(style -> style.baseTexture(face).hoverTexture(hover).pressedTexture(hover));
        if (tooltips != null && !tooltips.isEmpty()) {
            button.style(style -> style.tooltips(Tooltips.of(tooltips)));
        }
        button.setOnClick(event -> onClick.run());
        return at(button, x, y, width, height);
    }

    /** A plain text button using LDLib2's stock button look. */
    public static Button textButton(float x, float y, float width, float height,
                                    String text, Runnable onClick) {
        Button button = new Button();
        button.setText(text);
        button.setOnClick(event -> onClick.run());
        return at(button, x, y, width, height);
    }
}
