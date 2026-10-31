package com.warfactory.medical.body.trauma;

import com.warfactory.medical.body.limb.LimbType;
import net.minecraft.nbt.CompoundTag;

public final class Trauma {

    private final TraumaType type;
    private final LimbType limb;
    private float severity;
    private float baseSeverity;
    /** Multiplier on this wound's bleeding (1 = full, 0 = stopped). Set by bleed-control treatments. */
    private float bleedFactor = 1.0F;
    private boolean treated;
    private boolean stabilized;
    private boolean closed;
    private long timestamp;
    private float healProgress;

    public Trauma(TraumaType type, LimbType limb, float severity, long timestamp) {
        this.type = type;
        this.limb = limb;
        this.severity = clampSeverity(severity);
        this.baseSeverity = this.severity;
        this.timestamp = timestamp;
    }

    public static Trauma load(CompoundTag tag, TraumaTypes.Lookup registry) {
        String id = tag.getString("Type");
        TraumaType type = registry.get(id);
        if (type == null) {
            return null;
        }
        LimbType limb = LimbType.byOrdinal(tag.getInt("Limb"));
        Trauma t = new Trauma(type, limb, tag.getFloat("Severity"), tag.getLong("Timestamp"));
        t.baseSeverity = tag.contains("BaseSeverity") ? t.clampSeverity(tag.getFloat("BaseSeverity")) : t.severity;
        if (tag.contains("BleedFactor")) {
            t.bleedFactor = clampFactor(tag.getFloat("BleedFactor"));
            t.treated = tag.getBoolean("Treated");
            t.stabilized = tag.getBoolean("Stabilized");
            t.closed = tag.getBoolean("Closed");
        } else {
            // Migrate the old treated/sutured/stabilized/bleedStopped flags to the new bleedFactor model.
            boolean oldTreated = tag.getBoolean("Treated");
            boolean oldSutured = tag.getBoolean("Sutured");
            boolean oldBleedStopped = tag.getBoolean("BleedStopped");
            t.stabilized = tag.getBoolean("Stabilized");
            t.closed = oldSutured;
            t.treated = oldTreated || oldSutured;
            t.bleedFactor = (oldSutured || oldBleedStopped) ? 0.0F : (oldTreated ? 0.25F : 1.0F);
        }
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

    private static float clampFactor(float f) {
        if (f < 0.0F) {
            return 0.0F;
        }
        return f > 1.0F ? 1.0F : f;
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

    /** Whether the wound is being properly cared for and will mend on its own (self-repair branch). */
    public boolean isTreated() {
        return treated;
    }

    public void setTreated(boolean treated) {
        this.treated = treated;
    }

    public boolean isStabilized() {
        return stabilized;
    }

    public void setStabilized(boolean stabilized) {
        this.stabilized = stabilized;
    }

    /** Whether the wound has been closed (sutured) so it will not reopen. Implies bleeding stopped. */
    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    /** Current bleed multiplier (1 = full bleed, 0 = fully controlled). */
    public float getBleedFactor() {
        return bleedFactor;
    }

    public void setBleedFactor(float bleedFactor) {
        this.bleedFactor = clampFactor(bleedFactor);
    }

    public boolean isBleedControlledOnly() {
        return bleedFactor < 1.0F && !treated;
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

    public float bleeding() {
        if (bleedFactor <= 0.0F) {
            return 0.0F;
        }
        float bleedSeverity = Math.min(severity, baseSeverity);
        return type.getBleedingPerSeverity() * bleedSeverity * bleedFactor;
    }

    public float pain() {
        float base = type.getPainPerSeverity() * severity;
        return stabilized ? base * 0.5F : base;
    }

    public float healthReduction() {
        return type.isMajor() ? type.getHealthReductionPerSeverity() * severity : 0.0F;
    }

    /**
     * Recoverable current-health loss from a minor injury (e.g. a fall's blunt trauma). Unlike
     * {@link #healthReduction()} (which lowers max health until a major wound is treated), this only
     * lowers current health and comes back on its own as the minor trauma self-heals.
     */
    public float currentHealthReduction() {
        return type.isMajor() ? 0.0F : type.getHealthReductionPerSeverity() * severity;
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
                && !this.closed
                && !other.closed
                && this.severity < type.getMaxSeverity();
    }

    public void mergeIn(Trauma other) {
        this.severity = clampSeverity(this.severity + other.severity);
        this.baseSeverity = clampSeverity(this.baseSeverity + other.baseSeverity);
        // Merging fresh damage in re-opens bleed control to the less-controlled of the two.
        this.bleedFactor = Math.max(this.bleedFactor, other.bleedFactor);
        this.treated = this.treated && other.treated;
        this.stabilized = this.stabilized && other.stabilized;
        this.closed = this.closed && other.closed;
        this.timestamp = Math.min(this.timestamp, other.timestamp);
        this.healProgress = Math.min(this.healProgress, other.healProgress);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.getId());
        tag.putInt("Limb", limb.ordinal());
        tag.putFloat("Severity", severity);
        tag.putFloat("BaseSeverity", baseSeverity);
        tag.putFloat("BleedFactor", bleedFactor);
        tag.putBoolean("Treated", treated);
        tag.putBoolean("Stabilized", stabilized);
        tag.putBoolean("Closed", closed);
        tag.putLong("Timestamp", timestamp);
        tag.putFloat("HealProgress", healProgress);
        return tag;
    }
}
