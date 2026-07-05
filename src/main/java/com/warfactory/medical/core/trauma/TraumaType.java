package com.warfactory.medical.core.trauma;

import com.warfactory.medical.core.treatment.TreatmentAction;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable, data-driven definition of a kind of injury. Instances are created once (from config /
 * registry) and shared; the per-player mutable state lives in {@link Trauma}.
 */
public final class TraumaType {

    private final String id;
    private final TraumaCategory category;
    private final boolean major;
    private final float severityContribution;
    private final float painPerSeverity;
    private final float bleedingPerSeverity;
    private final float healSpeedPerTick;
    private final boolean canReopen;
    private final boolean permanent;
    private final float movementModifier;
    private final float healthReductionPerSeverity;
    private final float maxSeverity;
    private final boolean mergeable;
    private final Set<String> treatmentActions;

    private TraumaType(Builder b) {
        this.id = b.id;
        this.category = b.category;
        this.major = b.major;
        this.severityContribution = b.severityContribution;
        this.painPerSeverity = b.painPerSeverity;
        this.bleedingPerSeverity = b.bleedingPerSeverity;
        this.healSpeedPerTick = b.healSpeedPerTick;
        this.canReopen = b.canReopen;
        this.permanent = b.permanent;
        this.movementModifier = b.movementModifier;
        this.healthReductionPerSeverity = b.healthReductionPerSeverity;
        this.maxSeverity = b.maxSeverity;
        this.mergeable = b.mergeable;
        this.treatmentActions = b.treatmentActions.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(b.treatmentActions));
    }

    public static Builder builder(String id, TraumaCategory category) {
        return new Builder(id, category);
    }

    public String getId() {
        return id;
    }

    public TraumaCategory getCategory() {
        return category;
    }

    public boolean isMajor() {
        return major;
    }

    public float getSeverityContribution() {
        return severityContribution;
    }

    public float getPainPerSeverity() {
        return painPerSeverity;
    }

    /**
     * Bleeding rate in ml/tick per unit severity.
     */
    public float getBleedingPerSeverity() {
        return bleedingPerSeverity;
    }

    public float getHealSpeedPerTick() {
        return healSpeedPerTick;
    }

    public boolean canReopen() {
        return canReopen;
    }

    public boolean isPermanent() {
        return permanent;
    }

    /**
     * Movement speed multiplier contributed while active (1.0 = no effect).
     */
    public float getMovementModifier() {
        return movementModifier;
    }

    /**
     * Max-health points removed per unit severity (only for major trauma).
     */
    public float getHealthReductionPerSeverity() {
        return healthReductionPerSeverity;
    }

    public float getMaxSeverity() {
        return maxSeverity;
    }

    public boolean isMergeable() {
        return mergeable;
    }

    /**
     * {@link TreatmentAction#name()} strings this trauma responds to.
     */
    public Set<String> getTreatmentActions() {
        return treatmentActions;
    }

    public boolean respondsTo(TreatmentAction action) {
        return action != null && treatmentActions.contains(action.name());
    }

    /**
     * Fluent builder; all optional fields default to sensible no-op values.
     */
    public static final class Builder {
        private final String id;
        private final TraumaCategory category;
        private final Set<String> treatmentActions = new HashSet<>();
        private boolean major;
        private float severityContribution = 1.0F;
        private float painPerSeverity;
        private float bleedingPerSeverity;
        private float healSpeedPerTick;
        private boolean canReopen;
        private boolean permanent;
        private float movementModifier = 1.0F;
        private float healthReductionPerSeverity;
        private float maxSeverity = 1.0F;
        private boolean mergeable = true;

        private Builder(String id, TraumaCategory category) {
            this.id = id;
            this.category = category;
            this.major = category.isMajorByDefault();
        }

        public Builder major(boolean v) {
            this.major = v;
            return this;
        }

        public Builder severityContribution(float v) {
            this.severityContribution = v;
            return this;
        }

        public Builder painPerSeverity(float v) {
            this.painPerSeverity = v;
            return this;
        }

        public Builder bleedingPerSeverity(float v) {
            this.bleedingPerSeverity = v;
            return this;
        }

        public Builder healSpeedPerTick(float v) {
            this.healSpeedPerTick = v;
            return this;
        }

        public Builder canReopen(boolean v) {
            this.canReopen = v;
            return this;
        }

        public Builder permanent(boolean v) {
            this.permanent = v;
            return this;
        }

        public Builder movementModifier(float v) {
            this.movementModifier = v;
            return this;
        }

        public Builder healthReductionPerSeverity(float v) {
            this.healthReductionPerSeverity = v;
            return this;
        }

        public Builder maxSeverity(float v) {
            this.maxSeverity = v;
            return this;
        }

        public Builder mergeable(boolean v) {
            this.mergeable = v;
            return this;
        }

        public Builder treatment(TreatmentAction action) {
            this.treatmentActions.add(action.name());
            return this;
        }

        public Builder treatments(TreatmentAction... actions) {
            for (TreatmentAction a : actions) {
                this.treatmentActions.add(a.name());
            }
            return this;
        }

        public TraumaType build() {
            return new TraumaType(this);
        }
    }
}
