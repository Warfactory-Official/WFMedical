package com.warfactory.medical.damage;

import com.warfactory.medical.WFMedical;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public class DamageTypes {
    public static final ResourceKey<DamageType> BLEEDING_OUT = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "bleeding_out")
    );

    public static final ResourceKey<DamageType> GIVING_UP = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "giving_up")
    );

    public static final ResourceKey<DamageType> ASPHYXIATION = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "asphyxiation")
    );

    public static final ResourceKey<DamageType> OVERDOSE = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "overdose")
    );
}
