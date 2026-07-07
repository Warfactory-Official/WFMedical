package com.warfactory.medical.core.damage;

/**
 * High-level classification of an incoming {@link net.minecraft.world.damagesource.DamageSource},
 * used by the damage pipeline to decide which trauma types and armor behaviour apply.
 */
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
    GENERIC
}
