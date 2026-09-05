package com.warfactory.medical.core.trauma;

import com.warfactory.medical.core.treatment.TreatmentAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraumaResponseTest {

    @Test
    void everyWoundAffectingActionHasADefaultResponse() {
        for (TreatmentAction a : new TreatmentAction[]{
                TreatmentAction.REDUCE_BLEEDING, TreatmentAction.BOOST_CLOTTING, TreatmentAction.SUTURE_WOUND,
                TreatmentAction.STABILIZE_FRACTURE, TreatmentAction.HEAL_TRAUMA, TreatmentAction.TREAT_BURN,
                TreatmentAction.TREAT_RADIATION}) {
            assertNotNull(TraumaResponse.defaultFor(a), "no default response for " + a);
        }
    }

    @Test
    void theActionsWithNoPerWoundEffectHaveNoDefault() {
        // These act on the player, not on a specific wound, so a bare entry in treatmentActions is a no-op.
        assertNull(TraumaResponse.defaultFor(TreatmentAction.RESTORE_BLOOD));
        assertNull(TraumaResponse.defaultFor(TreatmentAction.REDUCE_PAIN));
        assertNull(TraumaResponse.defaultFor(TreatmentAction.NUMB_LIMB));
        assertNull(TraumaResponse.defaultFor(TreatmentAction.APPLY_TOURNIQUET));
        assertNull(TraumaResponse.defaultFor(null));
    }

    @Test
    void aBandageOnlyReducesBleedingWhileASutureClosesTheWound() {
        TraumaResponse bandage = TraumaResponse.defaultFor(TreatmentAction.REDUCE_BLEEDING);
        assertEquals(TraumaResponse.Effect.REDUCE_BLEED, bandage.effect());
        assertEquals(0.25F, bandage.factor());
        assertFalse(bandage.heals(), "a dressing manages the symptom, it does not mend the wound");
        assertFalse(bandage.closes());

        TraumaResponse suture = TraumaResponse.defaultFor(TreatmentAction.SUTURE_WOUND);
        assertEquals(TraumaResponse.Effect.SUTURE, suture.effect());
        assertTrue(suture.heals());
        assertTrue(suture.closes());
    }

    @Test
    void stabilisingAFractureNeitherHealsNorClosesIt() {
        TraumaResponse splint = TraumaResponse.defaultFor(TreatmentAction.STABILIZE_FRACTURE);
        assertEquals(TraumaResponse.Effect.STABILIZE, splint.effect());
        assertFalse(splint.heals());
        assertFalse(splint.closes());
    }

    @Test
    void healingActionsMendButDoNotClose() {
        for (TreatmentAction a : new TreatmentAction[]{
                TreatmentAction.HEAL_TRAUMA, TreatmentAction.TREAT_BURN, TreatmentAction.TREAT_RADIATION}) {
            TraumaResponse r = TraumaResponse.defaultFor(a);
            assertEquals(TraumaResponse.Effect.HEAL, r.effect(), "wrong effect for " + a);
            assertTrue(r.heals());
            assertFalse(r.closes(), a + " must not mark the wound un-reopenable");
        }
    }

    @Test
    void aHemostaticLeavesMoreResidualBleedThanADressing() {
        assertTrue(TraumaResponse.defaultFor(TreatmentAction.BOOST_CLOTTING).factor()
                        > TraumaResponse.defaultFor(TreatmentAction.REDUCE_BLEEDING).factor(),
                "clotting is a weaker bleed control than a direct dressing");
    }

    @Test
    void globalAndLocalisedActionsArePartitioned() {
        for (TreatmentAction a : TreatmentAction.values()) {
            assertEquals(!a.isGlobal(), a.isLocalized(), a + " is both or neither");
        }
        assertTrue(TreatmentAction.RESTORE_BLOOD.isGlobal());
        assertTrue(TreatmentAction.REDUCE_PAIN.isGlobal());
        assertTrue(TreatmentAction.BOOST_CLOTTING.isGlobal());
        assertTrue(TreatmentAction.SUTURE_WOUND.isLocalized());
        assertTrue(TreatmentAction.NUMB_LIMB.isLocalized(),
                "numbing targets one limb even though it is a drug");
    }
}
