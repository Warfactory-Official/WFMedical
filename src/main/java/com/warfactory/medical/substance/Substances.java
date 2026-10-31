package com.warfactory.medical.substance;

import com.warfactory.medical.WFMedical;
import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Substances by injecting item RL. */
public final class Substances {

    public static final WFRegistry<Substance> SUBSTANCES = WFRegistry.create("substances");

    public static final ResourceLocation MORPHINE = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "morphine_syringe");
    public static final ResourceLocation NALOXONE = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "naloxone_syringe");
    public static final ResourceLocation COMBAT_STIMULANT = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "combat_stimulant_i");

    private Substances() {
    }

    @Nullable
    public static Substance get(@Nullable String itemId) {
        ResourceLocation key = itemId == null ? null : ResourceLocation.tryParse(itemId);
        return key == null ? null : SUBSTANCES.get(key);
    }

    public static Substance defaultMorphine() {
        return new Substance(
                "morphine", MORPHINE.toString(),
                1.0F,
                0.5F,
                1.0F,
                200,
                1.6F,
                false,
                0.0F,
                40,
                0.0D,
                0.0F,
                0.0F,
                0);
    }

    public static Substance defaultNaloxone() {
        return new Substance(
                "naloxone", NALOXONE.toString(),
                0.0F,
                0.0F,
                0.0F,
                0,
                0.0F,
                true,
                3.0F,
                30,
                0.0D,
                0.0F,
                0.0F,
                0);
    }

    public static Substance defaultCombatStimulant() {
        return new Substance(
                "combat_stimulant_i", COMBAT_STIMULANT.toString(),
                0.0F,
                1.4F,
                1.6F,
                200,
                2.6F,
                false,
                0.0F,
                40,
                0.0D,
                1.0F,
                0.97F,
                3600);
    }
}
