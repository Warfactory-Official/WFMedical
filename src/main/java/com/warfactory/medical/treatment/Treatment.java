package com.warfactory.medical.treatment;

import com.warfactory.medical.body.trauma.TraumaCategory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import com.warfactory.medical.body.trauma.TreatmentAction;

public record Treatment(TreatmentAction action, Set<TraumaCategory> applicableCategories, float magnitude,
                        double bloodRestoreMl, int useDurationTicks, boolean removesTrauma) {

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

    @Override
    public Set<TraumaCategory> applicableCategories() {
        return applicableCategories;
    }

    public boolean appliesTo(TraumaCategory category) {
        return applicableCategories.isEmpty() || applicableCategories.contains(category);
    }
}
