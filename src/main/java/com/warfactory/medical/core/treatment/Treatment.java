package com.warfactory.medical.core.treatment;

import com.warfactory.medical.core.trauma.TraumaCategory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable description of what a single medical item does when used. Data-driven: the damage /
 * treatment modules read these fields to decide how to mutate a {@code Trauma} or the blood pool.
 */
public final class Treatment {

    private final TreatmentAction action;
    private final Set<TraumaCategory> applicableCategories;
    private final float magnitude;
    private final double bloodRestoreMl;
    private final int useDurationTicks;
    private final boolean removesTrauma;

    public Treatment(TreatmentAction action,
                     Set<TraumaCategory> applicableCategories,
                     float magnitude,
                     double bloodRestoreMl,
                     int useDurationTicks,
                     boolean removesTrauma) {
        this.action = action;
        this.applicableCategories = applicableCategories == null || applicableCategories.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(applicableCategories));
        this.magnitude = magnitude;
        this.bloodRestoreMl = bloodRestoreMl;
        this.useDurationTicks = useDurationTicks;
        this.removesTrauma = removesTrauma;
    }

    public TreatmentAction getAction() {
        return action;
    }

    /** Categories this treatment is allowed to act on (empty = any category). */
    public Set<TraumaCategory> getApplicableCategories() {
        return applicableCategories;
    }

    public boolean appliesTo(TraumaCategory category) {
        return applicableCategories.isEmpty() || applicableCategories.contains(category);
    }

    public float getMagnitude() {
        return magnitude;
    }

    public double getBloodRestoreMl() {
        return bloodRestoreMl;
    }

    public int getUseDurationTicks() {
        return useDurationTicks;
    }

    public boolean removesTrauma() {
        return removesTrauma;
    }
}
