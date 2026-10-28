package com.warfactory.medical.compat.wflib;

/** Suppression (WFLib near-miss rounds) raises stress, WFLib cigarettes lower it. */
public final class WfLibStressCompat {

    private WfLibStressCompat() {
    }

    /**
     * The share of a stress rise that gets through to {@code player}: psy protection from worn WFLib artefacts (stress
     * is this world's psy) cuts it, a negative one worsens it; 1 without WFLib.
     */
    public static float psyPass(net.minecraft.world.entity.player.Player player) {
        return WfLibArmorCompat.isLoaded() ? StressBridge.psyPass(player) : 1.0F;
    }

    /** After {@link WfLibArmorCompat#init()}. */
    public static void init() {
        if (WfLibArmorCompat.isLoaded()) {
            StressBridge.register();
        }
    }
}
