package com.warfactory.medical.treatment.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatmentUsePressTest {
    @BeforeEach
    void release() {
        TreatmentInteractions.releaseUsePress();
    }

    @Test
    void holdingUseThroughAnInjectionCannotStartASecondDose() {
        int injections = 0;
        int busyUntil = 0;
        // Vanilla attempts another use every four ticks; the syringe takes forty ticks.
        for (int tick = 0; tick < 160; tick += 4) {
            if (TreatmentInteractions.claimUsePress() && tick >= busyUntil) {
                injections++;
                busyUntil = tick + 40;
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, injections);
    }

    @Test
    void anotherDoseRequiresReleasingAndPressingAgain() {
        assertTrue(TreatmentInteractions.claimUsePress());
        assertFalse(TreatmentInteractions.claimUsePress());
        TreatmentInteractions.releaseUsePress();
        assertTrue(TreatmentInteractions.claimUsePress());
    }

    @Test
    void aPressWhileBusyDoesNotQueueATreatmentForLater() {
        assertTrue(TreatmentInteractions.claimUsePress());
        // The caller rejects this press because another treatment is still active.
        assertFalse(TreatmentInteractions.claimUsePress());
        assertFalse(TreatmentInteractions.claimUsePress());
        TreatmentInteractions.releaseUsePress();
        assertTrue(TreatmentInteractions.claimUsePress());
    }
}
