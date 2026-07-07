package com.warfactory.medical.core.treatment;

/**
 * The distinct effects a medical item can apply to trauma / physiology.
 * Trauma types declare which of these they respond to via {@code treatmentActions}.
 *
 * <p>Each action has a {@link #isGlobal() scope}: a GLOBAL action affects the whole body and ignores any
 * limb target (restoring blood, or a systemic painkiller / analgesia), whereas a LOCALIZED action is applied
 * to a single limb or the trauma on it (wound care, splinting, or a local anesthetic). This is the clean line
 * between "generally applied" medicine (painkiller) and "locally applied" medicine (anesthetic).</p>
 */
public enum TreatmentAction {
    REDUCE_BLEEDING(false),
    SUTURE_WOUND(false),
    STABILIZE_FRACTURE(false),
    RESTORE_BLOOD(true),
    /** Systemic painkiller / analgesia — masks pain across the WHOLE body (no limb target). */
    REDUCE_PAIN(true),
    /** Local anesthetic — numbs a SINGLE selected limb (must be aimed at a limb). */
    NUMB_LIMB(false),
    HEAL_TRAUMA(false),
    TREAT_BURN(false),
    TREAT_RADIATION(false);

    private final boolean global;

    TreatmentAction(boolean global) {
        this.global = global;
    }

    /**
     * Whether this action affects the WHOLE body and ignores any limb target (e.g. a systemic painkiller or
     * a blood restore), as opposed to a {@link #isLocalized() localized} action applied to one limb.
     */
    public boolean isGlobal() {
        return global;
    }

    /**
     * Whether this action is applied to a SINGLE limb / its trauma (wound care, splinting, local anesthetic).
     */
    public boolean isLocalized() {
        return !global;
    }
}
