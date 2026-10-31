package com.warfactory.medical.body.trauma;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

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
    private final Map<TreatmentAction, TraumaResponse> responses;

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
        this.responses = b.responses.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new EnumMap<>(b.responses));
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

    public float getMovementModifier() {
        return movementModifier;
    }

    public float getHealthReductionPerSeverity() {
        return healthReductionPerSeverity;
    }

    public float getMaxSeverity() {
        return maxSeverity;
    }

    public boolean isMergeable() {
        return mergeable;
    }

    public Map<TreatmentAction, TraumaResponse> getResponses() {
        return responses;
    }

    public boolean respondsTo(TreatmentAction action) {
        return action != null && responses.containsKey(action);
    }

    /** The declared outcome of applying {@code action} to this trauma, or null if it does not apply. */
    public TraumaResponse response(TreatmentAction action) {
        return action == null ? null : responses.get(action);
    }

    public static final class Builder {
        private final String id;
        private final TraumaCategory category;
        private final Map<TreatmentAction, TraumaResponse> responses = new EnumMap<>(TreatmentAction.class);
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

        /** Register the default response for an action (bare {@code ACTION} entry). */
        public Builder treatment(TreatmentAction action) {
            TraumaResponse resp = TraumaResponse.defaultFor(action);
            if (resp != null) {
                this.responses.put(action, resp);
            }
            return this;
        }

        public Builder treatments(TreatmentAction... actions) {
            for (TreatmentAction a : actions) {
                treatment(a);
            }
            return this;
        }

        /** Register an explicit per-action response (overrides any default for that action). */
        public Builder response(TraumaResponse resp) {
            if (resp != null && resp.action() != null) {
                this.responses.put(resp.action(), resp);
            }
            return this;
        }

        public TraumaType build() {
            return new TraumaType(this);
        }
    }
}
