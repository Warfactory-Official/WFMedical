package com.warfactory.medical.client.overlay;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.TransformTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A directional progress bar: draws {@code emptyBarArea} over the full rect, then {@code filledBarArea}
 * over the leading {@code progress} fraction of it.
 *
 * <p>LDLib2 dropped LDLib 1.x's {@code ProgressTexture} (its replacement, {@code ProgressBar}, is a
 * {@code UIElement} and so only usable inside a ModularUI, not for direct HUD drawing). This is the same
 * fill maths as the original, reduced to what the HUD overlays actually use.
 *
 * <p>One deliberate difference: the original called {@code drawSubArea} to sample the matching sub-region
 * of the fill texture, which LDLib2's {@link IGuiTexture} no longer exposes. Here the fill texture is
 * simply drawn into the clipped rect. For the solid {@code ColorRectTexture} fills the overlays use, the
 * two are pixel-identical; a texture with real UVs would stretch rather than clip.
 */
public class ProgressTexture extends TransformTexture {

    private final IGuiTexture emptyBarArea;
    private final IGuiTexture filledBarArea;

    private FillDirection fillDirection = FillDirection.LEFT_TO_RIGHT;
    private double progress;

    public ProgressTexture(IGuiTexture emptyBarArea, IGuiTexture filledBarArea) {
        this.emptyBarArea = emptyBarArea;
        this.filledBarArea = filledBarArea;
    }

    public ProgressTexture setFillDirection(FillDirection fillDirection) {
        this.fillDirection = fillDirection;
        return this;
    }

    public void setProgress(double progress) {
        this.progress = Mth.clamp(progress, 0.0, 1.0);
    }

    public double getProgress() {
        return progress;
    }

    @Override
    public ProgressTexture copy() {
        ProgressTexture copied = new ProgressTexture(emptyBarArea, filledBarArea).setFillDirection(fillDirection);
        copied.setProgress(progress);
        copied.copyTransform(this);
        return copied;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY, float x, float y,
                                float width, float height, float partialTicks) {
        if (emptyBarArea != null) {
            emptyBarArea.draw(graphics, mouseX, mouseY, x, y, width, height, partialTicks);
        }
        if (filledBarArea == null) {
            return;
        }
        float drawnU = (float) fillDirection.drawnU(progress);
        float drawnV = (float) fillDirection.drawnV(progress);
        float drawnWidth = (float) fillDirection.drawnWidth(progress);
        float drawnHeight = (float) fillDirection.drawnHeight(progress);
        filledBarArea.draw(graphics, mouseX, mouseY,
                x + drawnU * width, y + drawnV * height,
                width * drawnWidth, height * drawnHeight, partialTicks);
    }

    public enum FillDirection {
        LEFT_TO_RIGHT {
            @Override
            public double drawnHeight(double progress) {
                return 1.0;
            }
        },
        RIGHT_TO_LEFT {
            @Override
            public double drawnU(double progress) {
                return 1.0 - progress;
            }

            @Override
            public double drawnHeight(double progress) {
                return 1.0;
            }
        },
        UP_TO_DOWN {
            @Override
            public double drawnWidth(double progress) {
                return 1.0;
            }
        },
        DOWN_TO_UP {
            @Override
            public double drawnV(double progress) {
                return 1.0 - progress;
            }

            @Override
            public double drawnWidth(double progress) {
                return 1.0;
            }
        },
        ALWAYS_FULL {
            @Override
            public double drawnWidth(double progress) {
                return 1.0;
            }

            @Override
            public double drawnHeight(double progress) {
                return 1.0;
            }
        };

        public double drawnU(double progress) {
            return 0.0;
        }

        public double drawnV(double progress) {
            return 0.0;
        }

        public double drawnWidth(double progress) {
            return progress;
        }

        public double drawnHeight(double progress) {
            return progress;
        }
    }
}
