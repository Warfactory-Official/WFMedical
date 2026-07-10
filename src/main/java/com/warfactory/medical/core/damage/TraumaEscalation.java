package com.warfactory.medical.core.damage;

import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;

import java.util.List;
import java.util.function.Predicate;

/**
 * "Death by a thousand cuts": accumulated MINOR trauma on a limb coalesces into a real MAJOR wound, so chip
 * damage is never free. Once the stacked minor severity of a family crosses {@link #ESCALATE_THRESHOLD} the
 * scratches/bruises are consumed and merged into a single major injury that bleeds and will not simply heal
 * off – many scratches become a proper laceration, repeated blunt bruising becomes internal bleeding.
 *
 * <p>Run once after each new hit's trauma is merged (see {@code MedicalEventHandler.applyLimbTrauma}); it only
 * ever reads the limb's own trauma list, so it composes cleanly with the per-hit generation + depletion.</p>
 */
public final class TraumaEscalation {

    /**
     * Accumulated minor severity (per family) at which the scratches/bruises coalesce into a major wound.
     */
    private static final float ESCALATE_THRESHOLD = 1.0F;
    /**
     * Severity of the coalesced wound. Deliberately above the bleeding self-heal threshold (default 0.3) so a
     * stacked wound worsens untreated instead of closing on its own.
     */
    private static final float WOUND_BASE_SEVERITY = 0.55F;
    /**
     * How much extra severity the coalesced wound carries per unit of minor load past the threshold.
     */
    private static final float WOUND_OVERFLOW_SCALE = 0.3F;

    private TraumaEscalation() {
    }

    /**
     * Coalesce over-accumulated minor trauma on {@code limb} into major wounds (cut family -&gt; large
     * laceration; blunt family -&gt; internal bleeding). No-op until a family crosses the threshold.
     */
    public static void escalate(Limb limb, LimbType limbType, TraumaRegistry registry, int maxPerLimb, long nowTick) {
        if (limb == null || registry == null) {
            return;
        }
        // Cut family: stacked scratches (minor lacerations / punctures) -> a large, bleeding laceration.
        coalesce(limb, limbType, registry, maxPerLimb, nowTick,
                cat -> cat == TraumaCategory.LACERATION || cat == TraumaCategory.PUNCTURE,
                "laceration_large", TraumaCategory.LACERATION);
        // Blunt family: stacked bruising -> internal bleeding (permanent; will not heal off).
        coalesce(limb, limbType, registry, maxPerLimb, nowTick,
                cat -> cat == TraumaCategory.BRUISE || cat == TraumaCategory.CRUSH_INJURY,
                "internal_bleeding", TraumaCategory.INTERNAL_BLEEDING);
    }

    private static void coalesce(Limb limb, LimbType limbType, TraumaRegistry registry, int maxPerLimb,
                                 long nowTick, Predicate<TraumaCategory> family, String woundId,
                                 TraumaCategory woundCategory) {
        List<Trauma> traumas = limb.getTraumas();
        float load = 0.0F;
        for (int i = 0; i < traumas.size(); i++) {
            Trauma t = traumas.get(i);
            if (t.isMinor() && family.test(t.getType().getCategory())) {
                load += t.getSeverity();
            }
        }
        if (load < ESCALATE_THRESHOLD) {
            return;
        }
        TraumaType wound = registry.get(woundId);
        if (wound == null) {
            wound = registry.firstOfCategory(woundCategory);
        }
        if (wound == null) {
            return; // no wound type available; leave the minor trauma as-is
        }
        // Consume the family's minor trauma; they have become the coalesced wound.
        traumas.removeIf(t -> t.isMinor() && family.test(t.getType().getCategory()));
        float severity = Math.min(WOUND_BASE_SEVERITY + (load - ESCALATE_THRESHOLD) * WOUND_OVERFLOW_SCALE, 1.0F);
        limb.tryMerge(new Trauma(wound, limbType, severity, nowTick), maxPerLimb);
    }
}
