package com.warfactory.medical.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MedicalClientConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue DAMAGE_OUTLINE_ENABLED;
    private static final ModConfigSpec.EnumValue<HudAnchor> DAMAGE_OUTLINE_ANCHOR;
    private static final ModConfigSpec.IntValue DAMAGE_OUTLINE_OFFSET_X;
    private static final ModConfigSpec.IntValue DAMAGE_OUTLINE_OFFSET_Y;
    private static final ModConfigSpec.DoubleValue DAMAGE_OUTLINE_SCALE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("On-damage body-outline overlay: a limb-health-coloured silhouette that flashes up when you "
                        + "take damage, then eases out.")
                .push("damageOutline");
        DAMAGE_OUTLINE_ENABLED = b
                .comment("Master toggle for the on-damage body-outline overlay.")
                .define("enabled", true);
        DAMAGE_OUTLINE_ANCHOR = b
                .comment("Screen edge/corner the outline is anchored to before applying the pixel offsets.")
                .defineEnum("anchor", HudAnchor.MIDDLE_LEFT);
        DAMAGE_OUTLINE_OFFSET_X = b
                .comment("Pixel offset from the anchor along X (positive = right).")
                .defineInRange("offsetX", 10, -4096, 4096);
        DAMAGE_OUTLINE_OFFSET_Y = b
                .comment("Pixel offset from the anchor along Y (positive = down).")
                .defineInRange("offsetY", 0, -4096, 4096);
        DAMAGE_OUTLINE_SCALE = b
                .comment("Size multiplier for the body outline (1.0 = the base ~40x64px silhouette).")
                .defineInRange("scale", 1.0D, 0.25D, 8.0D);
        b.pop();

        SPEC = b.build();
    }

    private MedicalClientConfig() {
    }

    public static boolean damageOutlineEnabled() {
        return DAMAGE_OUTLINE_ENABLED.get();
    }

    public static HudAnchor damageOutlineAnchor() {
        return DAMAGE_OUTLINE_ANCHOR.get();
    }

    public static int damageOutlineOffsetX() {
        return DAMAGE_OUTLINE_OFFSET_X.get();
    }

    public static int damageOutlineOffsetY() {
        return DAMAGE_OUTLINE_OFFSET_Y.get();
    }

    public static float damageOutlineScale() {
        return DAMAGE_OUTLINE_SCALE.get().floatValue();
    }

    public enum HudAnchor {
        TOP_LEFT(0.0F, 0.0F),
        TOP_CENTER(0.5F, 0.0F),
        TOP_RIGHT(1.0F, 0.0F),
        MIDDLE_LEFT(0.0F, 0.5F),
        CENTER(0.5F, 0.5F),
        MIDDLE_RIGHT(1.0F, 0.5F),
        BOTTOM_LEFT(0.0F, 1.0F),
        BOTTOM_CENTER(0.5F, 1.0F),
        BOTTOM_RIGHT(1.0F, 1.0F);

        public final float hx;
        public final float vy;

        HudAnchor(float hx, float vy) {
            this.hx = hx;
            this.vy = vy;
        }
    }
}
