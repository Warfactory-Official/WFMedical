package com.warfactory.medical.core.treatment;

/**
 * The distinct effects a medical item can apply to trauma / physiology.
 * Trauma types declare which of these they respond to via {@code treatmentActions}.
 */
public enum TreatmentAction {
    REDUCE_BLEEDING,
    SUTURE_WOUND,
    STABILIZE_FRACTURE,
    RESTORE_BLOOD,
    REDUCE_PAIN,
    NUMB_LIMB,
    HEAL_TRAUMA,
    TREAT_BURN,
    TREAT_RADIATION
}
