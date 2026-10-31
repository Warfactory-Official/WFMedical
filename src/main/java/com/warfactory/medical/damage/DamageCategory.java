package com.warfactory.medical.damage;

import com.norwood.ahf.hit.HitCategory;

public enum DamageCategory {
    BALLISTIC,
    SLASHING,
    BLUNT,
    UNARMED,
    PIERCING,
    FIRE,
    EXPLOSION,
    CHEMICAL,
    RADIATION,
    FALL,
    GENERIC;

    public HitCategory hitCategory() {
        return switch (this) {
            case BALLISTIC -> HitCategory.BALLISTIC;
            case SLASHING -> HitCategory.SLASHING;
            case BLUNT -> HitCategory.BLUNT;
            case UNARMED -> HitCategory.UNARMED;
            case PIERCING -> HitCategory.PIERCING;
            case FIRE -> HitCategory.FIRE;
            case EXPLOSION -> HitCategory.EXPLOSION;
            case CHEMICAL -> HitCategory.CHEMICAL;
            case RADIATION -> HitCategory.RADIATION;
            case FALL -> HitCategory.FALL;
            case GENERIC -> HitCategory.GENERIC;
        };
    }
}
