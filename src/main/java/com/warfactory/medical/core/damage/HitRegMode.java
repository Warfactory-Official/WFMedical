package com.warfactory.medical.core.damage;

/**
 * How incoming attacks are registered against players / persistent bodies, before the medical system
 * classifies the wound. Selected by {@code MedicalConfig.hitRegistrationMode()}.
 */
public enum HitRegMode {
    /** Vanilla: hits clip the tight collision box; the arms (which render outside it) never register. */
    OFF,
    /** The box is widened to the model silhouette so arm / prone hits register (forgiving: gap-shots count). */
    ENVELOPE,
    /** ENVELOPE registration, then a shot that threaded a gap between limbs is rejected via the rig OBBs. */
    PRECISE
}
