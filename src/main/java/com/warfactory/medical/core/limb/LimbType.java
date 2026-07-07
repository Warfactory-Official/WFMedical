package com.warfactory.medical.core.limb;

/**
 * The six body parts tracked by the medical system.
 *
 * <p>Enum ordering is stable and is the on-disk NBT contract (persisted by {@link #ordinal()} /
 * {@link #name()}); never reorder existing constants.</p>
 */
public enum LimbType {
    HEAD("Head", 0.10F, true, false, false),
    TORSO("Torso", 0.40F, true, false, false),
    LEFT_ARM("Left Arm", 0.12F, false, false, true),
    RIGHT_ARM("Right Arm", 0.12F, false, false, true),
    LEFT_LEG("Left Leg", 0.13F, false, true, false),
    RIGHT_LEG("Right Leg", 0.13F, false, true, false);

    /**
     * Cached copy of {@link #values()}. {@code Enum.values()} clones its backing array on every call, so
     * hot paths (the periodic physiology pass, per-limb iteration) must use this shared, never-mutated
     * array instead to stay allocation-free. Never mutate its contents.
     */
    public static final LimbType[] VALUES = values();
    private final String displayName;
    private final float hitWeight;
    private final boolean vital;
    private final boolean leg;
    private final boolean arm;

    LimbType(String displayName, float hitWeight, boolean vital, boolean leg, boolean arm) {
        this.displayName = displayName;
        this.hitWeight = hitWeight;
        this.vital = vital;
        this.leg = leg;
        this.arm = arm;
    }

    /**
     * Safe NBT lookup by ordinal; returns {@link #TORSO} for out-of-range values.
     */
    public static LimbType byOrdinal(int ordinal) {
        return (ordinal >= 0 && ordinal < VALUES.length) ? VALUES[ordinal] : TORSO;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getHitWeight() {
        return hitWeight;
    }

    public boolean isVital() {
        return vital;
    }

    public boolean isLeg() {
        return leg;
    }

    public boolean isArm() {
        return arm;
    }
}
