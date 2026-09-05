package com.warfactory.medical.core.substance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubstanceTest {

    @AfterEach
    void restoreActive() {
        SubstanceRegistry.setActive(SubstanceRegistry.withDefaults());
    }

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
        Substance morphine = SubstanceRegistry.defaultMorphine();
        assertEquals(1.0F, morphine.painSuppression(), "morphine is the full analgesic");
        assertTrue(morphine.doseLoad() > 0.0F, "and it must accumulate towards an overdose");
        assertFalse(morphine.antidote());
        assertTrue(morphine.lethalThreshold() > morphine.overdoseThreshold(),
                "an overdose has to be survivable before it is lethal");

        Substance naloxone = SubstanceRegistry.defaultNaloxone();
        assertTrue(naloxone.antidote());
        assertTrue(naloxone.reversalAmount() > 0.0F, "an antidote that reverses nothing is inert");
        assertEquals(0.0F, naloxone.doseLoad(), "the antidote must not itself contribute to the overdose");

        Substance stim = SubstanceRegistry.defaultCombatStimulant();
        assertTrue(stim.stimulantStrength() > 0.0F);
        assertTrue(stim.effectTicks() > 0, "a timed buff needs a duration");
        assertTrue(stim.clottingBoost() > 0.0F);
    }

    @Test
    void theRegistryIsKeyedByItemIdBecauseThatIsWhatTheUseEventHas() {
        SubstanceRegistry r = new SubstanceRegistry();
        Substance s = of(0.5F, 0.5F, 100, 20, 0.0F, 0.0F, 0);
        assertSame(s, r.register(s));
        assertSame(s, r.get("mod:item"));
        assertTrue(r.contains("mod:item"));
        assertNull(r.get("mod:other"));
        assertNull(r.get(null), "the lookup is fed a possibly-absent item id");
        assertEquals(1, r.size());
    }

    @Test
    void reRegisteringAnItemIdReplacesTheEntry() {
        SubstanceRegistry r = new SubstanceRegistry();
        r.register(of(0.1F, 0.0F, 0, 20, 0.0F, 0.0F, 0));
        r.register(of(0.9F, 0.0F, 0, 20, 0.0F, 0.0F, 0));
        assertEquals(1, r.size());
        assertEquals(0.9F, r.get("mod:item").painSuppression());
    }

    @Test
    void clearingAndReloadingDefaultsWorks() {
        SubstanceRegistry r = SubstanceRegistry.withDefaults();
        assertEquals(3, r.size());
        r.clear();
        assertEquals(0, r.size());
        r.registerDefaults();
        assertEquals(3, r.size());
    }

    @Test
    void theCollectionViewIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> SubstanceRegistry.withDefaults().all().clear());
    }

    @Test
    void settingANullActiveRegistryRestoresTheDefaultsRatherThanLeavingNull() {
        SubstanceRegistry.setActive(null);
        assertNotNull(SubstanceRegistry.active());
        assertEquals(3, SubstanceRegistry.active().size(),
                "an empty active registry would make every syringe inert");
    }
}
