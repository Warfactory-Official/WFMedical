package com.warfactory.medical.damage;

import com.warfactory.medical.body.limb.Limb;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.body.trauma.Trauma;
import com.warfactory.medical.body.trauma.TraumaCategory;
import com.warfactory.medical.body.trauma.TraumaTypes;
import com.warfactory.medical.body.trauma.TraumaType;

import java.util.List;
import java.util.function.Predicate;

public final class TraumaEscalation {

    private static final float ESCALATE_THRESHOLD = 1.0F;
    private static final float WOUND_BASE_SEVERITY = 0.55F;
    private static final float WOUND_OVERFLOW_SCALE = 0.3F;

    private TraumaEscalation() {
    }

    public static void escalate(Limb limb, LimbType limbType, TraumaTypes.Lookup registry, int maxPerLimb, long nowTick) {
        if (limb == null || registry == null) {
            return;
        }
        coalesce(limb, limbType, registry, maxPerLimb, nowTick,
                cat -> cat == TraumaCategory.LACERATION || cat == TraumaCategory.PUNCTURE,
                "laceration_large", TraumaCategory.LACERATION);
        coalesce(limb, limbType, registry, maxPerLimb, nowTick,
                cat -> cat == TraumaCategory.BRUISE || cat == TraumaCategory.CRUSH_INJURY,
                "internal_bleeding", TraumaCategory.INTERNAL_BLEEDING);
    }

    private static void coalesce(Limb limb, LimbType limbType, TraumaTypes.Lookup registry, int maxPerLimb,
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
            return;
        }
        traumas.removeIf(t -> t.isMinor() && family.test(t.getType().getCategory()));
        float severity = Math.min(WOUND_BASE_SEVERITY + (load - ESCALATE_THRESHOLD) * WOUND_OVERFLOW_SCALE, 1.0F);
        limb.tryMerge(new Trauma(wound, limbType, severity, nowTick), maxPerLimb);
    }
}
