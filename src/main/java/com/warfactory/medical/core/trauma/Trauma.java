package com.warfactory.medical.core.trauma;

import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.nbt.CompoundTag;

/**
 * A single mutable injury instance attached to a limb. Physiology is derived from these; the object
 * itself only stores state and exposes cheap per-trauma contributions used when rebuilding limb caches.
 */
public final class Trauma {

    private final TraumaType type;
    private final LimbType limb;
    private float severity;
    private boolean treated;
    private boolean sutured;
    private boolean stabilized;
    private long timestamp;
    private float healProgress;

    public Trauma(TraumaType type, LimbType limb, float severity, long timestamp) {
        this.type = type;
        this.limb = limb;
        this.severity = clampSeverity(severity);
        this.timestamp = timestamp;
    }

    /**
     * @return the loaded trauma, or {@code null} if the type is unknown to the registry.
     */
    public static Trauma load(CompoundTag tag, TraumaRegistry registry) {
        String id = tag.getString("Type");
        TraumaType type = registry.get(id);
        if (type == null) {
            return null;
        }
        LimbType limb = LimbType.byOrdinal(tag.getInt("Limb"));
        Trauma t = new Trauma(type, limb, tag.getFloat("Severity"), tag.getLong("Timestamp"));
        t.treated = tag.getBoolean("Treated");
        t.sutured = tag.getBoolean("Sutured");
        t.stabilized = tag.getBoolean("Stabilized");
        t.healProgress = tag.getFloat("HealProgress");
        return t;
    }

    private float clampSeverity(float s) {
        if (s < 0.0F) {
            return 0.0F;
        }
        float max = type.getMaxSeverity();
        return s > max ? max : s;
    }

    public TraumaType getType() {
        return type;
    }

    public LimbType getLimb() {
        return limb;
    }

    public float getSeverity() {
        return severity;
    }

    public void setSeverity(float severity) {
        this.severity = clampSeverity(severity);
    }

    public boolean isTreated() {
        return treated;
    }

    public void setTreated(boolean treated) {
        this.treated = treated;
    }

    public boolean isSutured() {
        return sutured;
    }

    public void setSutured(boolean sutured) {
        this.sutured = sutured;
    }

    public boolean isStabilized() {
        return stabilized;
    }

    public void setStabilized(boolean stabilized) {
        this.stabilized = stabilized;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getHealProgress() {
        return healProgress;
    }

    public void setHealProgress(float healProgress) {
        this.healProgress = healProgress;
    }

    /**
     * Bleeding contribution in ml/tick; a sutured wound does not bleed, a bandaged one bleeds less.
     */
    public float bleeding() {
        if (sutured) {
            return 0.0F;
        }
        float base = type.getBleedingPerSeverity() * severity;
        return treated ? base * 0.25F : base;
    }

    /**
     * Pain contribution; splinting/stabilizing a fracture eases it.
     */
    public float pain() {
        float base = type.getPainPerSeverity() * severity;
        return stabilized ? base * 0.5F : base;
    }

    /**
     * Max-health reduction (only major trauma removes hearts).
     */
    public float healthReduction() {
        return type.isMajor() ? type.getHealthReductionPerSeverity() * severity : 0.0F;
    }

    public boolean isMinor() {
        return !type.isMajor();
    }

    public boolean isFracture() {
        return type.getCategory() == TraumaCategory.FRACTURE;
    }

    public boolean canMergeWith(Trauma other) {
        return other != null
                && this != other
                && type.isMergeable()
                && other.type == this.type
                && other.limb == this.limb
                && !this.sutured
                && !other.sutured
                && this.severity < type.getMaxSeverity();
    }

    /**
     * Fold {@code other} into this one, capping at the type's max severity.
     */
    public void mergeIn(Trauma other) {
        this.severity = clampSeverity(this.severity + other.severity);
        // A merged wound is only as "handled" as its least-treated component.
        this.treated = this.treated && other.treated;
        this.sutured = this.sutured && other.sutured;
        this.stabilized = this.stabilized && other.stabilized;
        this.timestamp = Math.min(this.timestamp, other.timestamp);
        this.healProgress = Math.min(this.healProgress, other.healProgress);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.getId());
        tag.putInt("Limb", limb.ordinal());
        tag.putFloat("Severity", severity);
        tag.putBoolean("Treated", treated);
        tag.putBoolean("Sutured", sutured);
        tag.putBoolean("Stabilized", stabilized);
        tag.putLong("Timestamp", timestamp);
        tag.putFloat("HealProgress", healProgress);
        return tag;
    }
}
