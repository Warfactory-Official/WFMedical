package com.warfactory.medical.substance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubstanceTest {

    private static Substance of(float painSuppression, float doseLoad, int unconsciousTicks,
                                int useDurationTicks, float clotting, float stimulant, int effectTicks) {
        return new Substance("id", "mod:item", painSuppression, doseLoad, 1.0F, unconsciousTicks,
                0.0F, false, 0.0F, useDurationTicks, 0.0D, clotting, stimulant, effectTicks);
    }

    @Test
    void theUnitRangeFieldsAreClampedAtConstruction() {
        Substance s = of(9.0F, 1.0F, 10, 10, 9.0F, 9.0F, 10);
        assertEquals(1.0F, s.painSuppression());
        assertEquals(1.0F, s.clottingBoost());
        assertEquals(1.0F, s.stimulantStrength());

        Substance neg = of(-9.0F, -1.0F, -10, 10, -9.0F, -9.0F, -10);
        assertEquals(0.0F, neg.painSuppression());
        assertEquals(0.0F, neg.doseLoad());
        assertEquals(0.0F, neg.clottingBoost());
        assertEquals(0.0F, neg.stimulantStrength());
        assertEquals(0, neg.unconsciousTicks());
        assertEquals(0, neg.effectTicks());
    }

    @Test
    void aZeroUseDurationIsRaisedToOneTick() {
        // A zero-tick use would divide by zero in the progress bar and complete instantly.
        assertEquals(1, of(0.0F, 0.0F, 0, 0, 0.0F, 0.0F, 0).useDurationTicks());
        assertEquals(1, of(0.0F, 0.0F, 0, -50, 0.0F, 0.0F, 0).useDurationTicks());
    }

    @Test
    void doseLoadIsUnboundedAboveBecauseOverdoseIsMeasuredAgainstAThreshold() {
        assertEquals(5.0F, of(0.0F, 5.0F, 0, 10, 0.0F, 0.0F, 0).doseLoad());
    }

    @Test
    void theShippedDrugsHaveTheRolesTheEngineExpects() {
        Substance morphine = Substances.defaultMorphine();
        assertEquals(1.0F, morphine.painSuppression(), "morphine is the full analgesic");
        assertTrue(morphine.doseLoad() > 0.0F, "and it must accumulate towards an overdose");
        assertFalse(morphine.antidote());
        assertTrue(morphine.lethalThreshold() > morphine.overdoseThreshold(),
                "an overdose has to be survivable before it is lethal");

        Substance naloxone = Substances.defaultNaloxone();
        assertTrue(naloxone.antidote());
        assertTrue(naloxone.reversalAmount() > 0.0F, "an antidote that reverses nothing is inert");
        assertEquals(0.0F, naloxone.doseLoad(), "the antidote must not itself contribute to the overdose");

        Substance stim = Substances.defaultCombatStimulant();
        assertTrue(stim.stimulantStrength() > 0.0F);
        assertTrue(stim.effectTicks() > 0, "a timed buff needs a duration");
        assertTrue(stim.clottingBoost() > 0.0F);
    }

    @Test
    void theLiveRegistryIsKeyedByItemIdBecauseThatIsWhatTheUseEventHas() {
        assertNotNull(Substances.SUBSTANCES.get(Substances.MORPHINE));
        assertSame(Substances.SUBSTANCES.get(Substances.MORPHINE), Substances.get("wfmedical:morphine_syringe"));
        assertNull(Substances.get("mod:other"));
        assertNull(Substances.get(null), "the lookup is fed a possibly-absent item id");
    }

    @Test
    void theDefaultsAreTheThreeShippedSyringes() {
        assertEquals(3, SubstanceDefinitions.defaultSubstances().size());
    }
}
