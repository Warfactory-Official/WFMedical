package com.warfactory.medical.core.treatment;

public enum TreatmentAction {
    REDUCE_BLEEDING(false),
    SUTURE_WOUND(false),
    STABILIZE_FRACTURE(false),
    RESTORE_BLOOD(true),
    REDUCE_PAIN(true),
    NUMB_LIMB(false),
    BOOST_CLOTTING(true),
    APPLY_TOURNIQUET(false),
    HEAL_TRAUMA(false),
    TREAT_BURN(false),
    TREAT_RADIATION(false),
    /** Manual resuscitation of a downed casualty. Whole-body, needs no item. */
    RESUSCITATE(true);

    private final boolean global;

    TreatmentAction(boolean global) {
        this.global = global;
    }

    public boolean isGlobal() {
        return global;
    }

    public boolean isLocalized() {
        return !global;
    }
}
