package com.warfactory.medical.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Suppressors are global and append-only: one test, ordered steps. */
class MedicalSwayTest {

    @Test
    void suppressorsCompose() {
        assertFalse(MedicalSway.suppressed(null));
        MedicalSway.suppress(p -> false);
        assertFalse(MedicalSway.suppressed(null));
        MedicalSway.suppress(p -> p == null);
        assertTrue(MedicalSway.suppressed(null), "second mod's suppressor was shadowed");
        MedicalSway.suppress(p -> false);
        assertTrue(MedicalSway.suppressed(null), "a later false must not override an earlier true");
    }
}
