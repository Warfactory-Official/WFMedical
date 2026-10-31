package com.warfactory.medical.body.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MedicalClientConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue DAMAGE_OUTLINE_ENABLED;
    private static final ModConfigSpec.EnumValue<HudAnchor> DAMAGE_OUTLINE_ANCHOR;
    private static final ModConfigSpec.IntValue DAMAGE_OUTLINE_OFFSET_X;
    private static final ModConfigSpec.IntValue DAMAGE_OUTLINE_OFFSET_Y;
    private static final ModConfigSpec.DoubleValue DAMAGE_OUTLINE_SCALE;
    private static final ModConfigSpec.DoubleValue STRESS_SHAKE_STRENGTH;
    private static final ModConfigSpec.BooleanValue STRESS_VIGNETTE_ENABLED;

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

        b.comment("Stress effects.").push("stress");
        STRESS_SHAKE_STRENGTH = b
                .comment("Multiplier on the weapon shake from stress (aim moves with it). 0 disables.")
                .defineInRange("shakeStrength", 1.0D, 0.0D, 3.0D);
        STRESS_VIGNETTE_ENABLED = b
                .comment("Black vignette from stress, strong from 80%.")
                .define("vignette", true);
        b.pop();

        SPEC = b.build();
    }

    private record Values(boolean damageOutlineEnabled, HudAnchor damageOutlineAnchor, int damageOutlineOffsetX,
                          int damageOutlineOffsetY, float damageOutlineScale, float stressShakeStrength,
                          boolean stressVignetteEnabled) {
    }

    private static volatile Values current;

    private MedicalClientConfig() {
    }

    public static void onConfig(net.neoforged.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            current = new Values(DAMAGE_OUTLINE_ENABLED.get(), DAMAGE_OUTLINE_ANCHOR.get(), DAMAGE_OUTLINE_OFFSET_X.get(),
                    DAMAGE_OUTLINE_OFFSET_Y.get(), DAMAGE_OUTLINE_SCALE.get().floatValue(),
                    STRESS_SHAKE_STRENGTH.get().floatValue(), STRESS_VIGNETTE_ENABLED.get());
        }
    }

    private static Values v() {
        Values v = current;
        if (v == null) {
            throw new IllegalStateException("wfmedical client config not loaded");
        }
        return v;
    }

    public static boolean damageOutlineEnabled() {
        return v().damageOutlineEnabled();
    }

    public static HudAnchor damageOutlineAnchor() {
        return v().damageOutlineAnchor();
    }

    public static int damageOutlineOffsetX() {
        return v().damageOutlineOffsetX();
    }

    public static int damageOutlineOffsetY() {
        return v().damageOutlineOffsetY();
    }

    public static float damageOutlineScale() {
        return v().damageOutlineScale();
    }

    public static float stressShakeStrength() {
        return v().stressShakeStrength();
    }

    public static boolean stressVignetteEnabled() {
        return v().stressVignetteEnabled();
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
