package com.warfactory.medical.core.treatment;

/**
 * Effects a medical item can apply to trauma / physiology. GLOBAL actions affect the whole body (blood
 * restore, systemic painkiller); LOCALIZED actions target a single limb (wound care, anesthetic).
 */
public enum TreatmentAction {
    REDUCE_BLEEDING(false),
    SUTURE_WOUND(false),
    STABILIZE_FRACTURE(false),
    RESTORE_BLOOD(true),
    /**
     * Systemic painkiller / analgesia — masks pain across the WHOLE body (no limb target).
     */
    REDUCE_PAIN(true),
    /**
     * Local anesthetic — numbs a SINGLE selected limb (must be aimed at a limb).
     */
    NUMB_LIMB(false),
    /**
     * Hemostatic — boosts whole-body natural blood clotting for a while (no limb target).
     */
    BOOST_CLOTTING(true),
    /**
     * Tourniquet — a removable per-limb constrictor (arm/leg only); slows the limb's bleeding without treating the wound.
     */
    APPLY_TOURNIQUET(false),
    HEAL_TRAUMA(false),
    TREAT_BURN(false),
    TREAT_RADIATION(false);

    private final boolean global;

    TreatmentAction(boolean global) {
        this.global = global;
    }

    /**
     * True if this action affects the whole body and ignores limb target.
     */
    public boolean isGlobal() {
        return global;
    }

    /**
     * True if this action is applied to a single limb (wound care, splinting, local anesthetic).
     */
    public boolean isLocalized() {
        return !global;
    }
}
