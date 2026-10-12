package com.warfactory.medical.core.limb;

import com.norwood.ahf.part.HitboxPart;

public enum LimbType {
    HEAD("Head", true, false, false),
    TORSO("Torso", true, false, false),
    LEFT_ARM("Left Arm", false, false, true),
    RIGHT_ARM("Right Arm", false, false, true),
    LEFT_LEG("Left Leg", false, true, false),
    RIGHT_LEG("Right Leg", false, true, false);

    public static final LimbType[] VALUES = values();
    private final String displayName;
    private final boolean vital;
    private final boolean leg;
    private final boolean arm;

    LimbType(String displayName, boolean vital, boolean leg, boolean arm) {
        this.displayName = displayName;
        this.vital = vital;
        this.leg = leg;
        this.arm = arm;
    }

    public static LimbType byOrdinal(int ordinal) {
        return (ordinal >= 0 && ordinal < VALUES.length) ? VALUES[ordinal] : TORSO;
    }

    public static LimbType of(HitboxPart part) {
        return switch (part) {
            case HEAD -> HEAD;
            case TORSO -> TORSO;
            case LEFT_ARM -> LEFT_ARM;
            case RIGHT_ARM -> RIGHT_ARM;
            case LEFT_LEG -> LEFT_LEG;
            case RIGHT_LEG -> RIGHT_LEG;
        };
    }

    public HitboxPart part() {
        return switch (this) {
            case HEAD -> HitboxPart.HEAD;
            case TORSO -> HitboxPart.TORSO;
            case LEFT_ARM -> HitboxPart.LEFT_ARM;
            case RIGHT_ARM -> HitboxPart.RIGHT_ARM;
            case LEFT_LEG -> HitboxPart.LEFT_LEG;
            case RIGHT_LEG -> HitboxPart.RIGHT_LEG;
        };
    }

    public String getDisplayName() {
        return displayName;
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
