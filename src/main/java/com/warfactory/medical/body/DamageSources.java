package com.warfactory.medical.body;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

public class DamageSources {
    public static DamageSource bleedingOut(Level level) {
        return source(level, DamageTypes.BLEEDING_OUT);
    }

    public static DamageSource givingUp(Level level) {
        return source(level, DamageTypes.GIVING_UP);
    }

    public static DamageSource asphyxiation(Level level) {
        return source(level, DamageTypes.ASPHYXIATION);
    }

    public static DamageSource overdose(Level level) {
        return source(level, DamageTypes.OVERDOSE);
    }

    /** The engine's own kills: the end of a body, not a blow to it, so no armour, wound model or vanilla armour wear answers for them. */
    public static boolean isEngineKill(DamageSource source) {
        return source != null && (source.is(DamageTypes.BLEEDING_OUT) || source.is(DamageTypes.GIVING_UP)
                || source.is(DamageTypes.ASPHYXIATION) || source.is(DamageTypes.OVERDOSE));
    }

    private static DamageSource source(Level level, ResourceKey<DamageType> key) {
        return new DamageSource(
            level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key)
        );
    }
}
