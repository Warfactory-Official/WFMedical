package com.warfactory.medical.core.damage;

import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Escalation is what stops a limb accumulating a hundred scratches instead of one real wound: once the
 * minor load in a family passes 1.0 severity, the scratches are replaced by a single major wound.
 */
class TraumaEscalationTest {

    private TraumaRegistry registry;
    private Limb limb;

    @BeforeEach
    void setUp() {
        registry = Fixtures.registry();
        limb = new Limb(LimbType.TORSO);
    }

    private void add(String id, float severity) {
        limb.addTrauma(new Trauma(registry.getOrThrow(id), LimbType.TORSO, severity, 0L));
    }

    private void escalate() {
        TraumaEscalation.escalate(limb, LimbType.TORSO, registry, 8, 100L);
        limb.rebuildCache();
    }

    private boolean has(String id) {
        return limb.getTraumas().stream().anyMatch(t -> t.getType().getId().equals(id));
    }

    private long countOfCategory(TraumaCategory cat) {
        return limb.getTraumas().stream().filter(t -> t.getType().getCategory() == cat).count();
    }

    @Test
    void belowTheThresholdNothingChanges() {
        add("laceration_small", 0.4F);
        add("laceration_small", 0.3F);
        escalate();
        assertEquals(2, limb.getTraumas().size(), "0.7 of minor load is under the 1.0 escalation line");
        assertFalse(has("laceration_large"));
    }

    @Test
    void enoughSmallCutsBecomeOneLargeOne() {
        add("laceration_small", 0.6F);
        add("laceration_small", 0.6F);
        escalate();
        assertTrue(has("laceration_large"), "the scratches should have opened into a real wound");
        assertEquals(0, countOfCategory(TraumaCategory.LACERATION) - 1,
                "the minor cuts must be consumed, not kept alongside the new wound");
    }

    @Test
    void enoughBruisingBecomesAnInternalBleed() {
        add("bruise", 0.6F);
        add("bruise", 0.6F);
        escalate();
        assertTrue(has("internal_bleeding"), "repeated blunt trauma should go internal");
        assertEquals(0, countOfCategory(TraumaCategory.BRUISE));
    }

    @Test
    void theTwoFamiliesEscalateIndependently() {
        add("laceration_small", 1.2F);
        add("bruise", 0.2F);
        escalate();
        assertTrue(has("laceration_large"));
        assertFalse(has("internal_bleeding"), "the bruise family was nowhere near its threshold");
        assertTrue(has("bruise"), "and its members must survive untouched");
    }

    @Test
    void aBiggerOverflowMakesASevererWound() {
        add("laceration_small", 0.5F);
        add("laceration_small", 0.5F);
        escalate();
        float light = limb.getTraumas().stream()
                .filter(t -> t.getType().getId().equals("laceration_large"))
                .findFirst().orElseThrow().getSeverity();

        setUp();
        for (int i = 0; i < 6; i++) {
            add("laceration_small", 0.9F);
        }
        escalate();
        float heavy = limb.getTraumas().stream()
                .filter(t -> t.getType().getId().equals("laceration_large"))
                .findFirst().orElseThrow().getSeverity();

        assertTrue(heavy > light, light + " -> " + heavy);
        assertTrue(heavy <= 1.0F, "and it is still capped");
    }

    @Test
    void majorWoundsAreNotConsumedByEscalation() {
        add("laceration_large", 0.9F);
        add("laceration_small", 0.6F);
        add("laceration_small", 0.6F);
        escalate();
        // The pre-existing major wound must survive; the two minors merge into it or add beside it.
        assertTrue(has("laceration_large"));
        assertEquals(0, limb.getTraumas().stream()
                .filter(t -> t.getType().getId().equals("laceration_small")).count());
    }

    @Test
    void punctureLoadCountsTowardsTheLacerationFamily() {
        // A minor puncture and a minor cut are the same "open wound" family for escalation purposes.
        registry.register(com.warfactory.medical.core.trauma.TraumaType
                .builder("small_puncture", TraumaCategory.PUNCTURE).major(false).build());
        add("small_puncture", 0.6F);
        add("laceration_small", 0.6F);
        escalate();
        assertTrue(has("laceration_large"));
    }

    @Test
    void nullArgumentsAreIgnoredRatherThanThrowing() {
        TraumaEscalation.escalate(null, LimbType.TORSO, registry, 8, 0L);
        TraumaEscalation.escalate(limb, LimbType.TORSO, null, 8, 0L);
    }

    @Test
    void anEmptyLimbIsLeftAlone() {
        escalate();
        assertTrue(limb.getTraumas().isEmpty());
    }

    @Test
    void escalationRespectsTheTraumaCap() {
        for (int i = 0; i < 10; i++) {
            add("laceration_small", 0.9F);
            add("bruise", 0.9F);
        }
        TraumaEscalation.escalate(limb, LimbType.TORSO, registry, 2, 100L);
        assertTrue(limb.getTraumas().size() <= 2, "cap breached: " + limb.getTraumas().size());
    }
}
