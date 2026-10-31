package com.warfactory.medical.body.trauma;


/**
 * How a single {@link TraumaType} reacts to one {@link TreatmentAction}. This is the data-driven knob that
 * makes treatment behaviour fully modular per trauma: e.g. one wound's bleeding is fully stopped by a
 * bandage, another is only reduced, another ignores bandages entirely (no response for that action).
 *
 * <p>Parsed from a {@code treatmentActions} entry of the form {@code ACTION[=EFFECT[:factor]]} (a bare
 * {@code ACTION} uses {@link #defaultFor(TreatmentAction)}).
 *
 * @param action the treatment action this response is for
 * @param effect what happens to the wound when that action is applied
 * @param factor auxiliary number (for {@link Effect#REDUCE_BLEED} it is the residual bleed multiplier 0..1)
 */
public record TraumaResponse(TreatmentAction action, Effect effect, float factor) {

    public enum Effect {
        /** Bleeding is fully stopped (bleedFactor -> 0). */
        STOP_BLEED,
        /** Bleeding is reduced to {@code factor} of its base (bleedFactor -> factor). */
        REDUCE_BLEED,
        /** Wound is closed: bleeding stopped, it will mend, and it will not reopen. */
        SUTURE,
        /** A fracture is stabilised so it can knit. */
        STABILIZE,
        /** The wound itself is treated: severity reduced by the item's magnitude (and removed if depleted). */
        HEAL
    }

    /**
     * Whether applying this response marks the wound as "treated", i.e. it starts mending on its own. A plain
     * bleed stop/reduce only manages the symptom (the wound stays until properly treated).
     */
    public boolean heals() {
        return effect == Effect.SUTURE || effect == Effect.HEAL;
    }

    /** Whether applying this response closes the wound so it will not reopen. */
    public boolean closes() {
        return effect == Effect.SUTURE;
    }

    /** The out-of-the-box response for a bare {@code ACTION} entry with no explicit effect. */
    public static TraumaResponse defaultFor(TreatmentAction action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case REDUCE_BLEEDING -> new TraumaResponse(action, Effect.REDUCE_BLEED, 0.25F);
            case BOOST_CLOTTING -> new TraumaResponse(action, Effect.REDUCE_BLEED, 0.3F);
            case SUTURE_WOUND -> new TraumaResponse(action, Effect.SUTURE, 0.0F);
            case STABILIZE_FRACTURE -> new TraumaResponse(action, Effect.STABILIZE, 0.0F);
            case HEAL_TRAUMA, TREAT_BURN, TREAT_RADIATION -> new TraumaResponse(action, Effect.HEAL, 0.0F);
            default -> null;
        };
    }
}
