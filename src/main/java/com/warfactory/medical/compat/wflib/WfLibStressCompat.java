package com.warfactory.medical.compat.wflib;

/** Suppression (WFLib near-miss rounds) raises stress, WFLib cigarettes lower it. */
public final class WfLibStressCompat {

    private WfLibStressCompat() {
    }

    /** After {@link WfLibArmorCompat#init()}. */
    public static void init() {
        if (WfLibArmorCompat.isLoaded()) {
            StressBridge.register();
        }
    }
}
