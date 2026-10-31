package com.warfactory.medical.treatment;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Treatments by medical item RL. */
public final class Treatments {

    public static final WFRegistry<Treatment> TREATMENTS = WFRegistry.create("treatments");

    private Treatments() {
    }

    @Nullable
    public static Treatment get(@Nullable String itemId) {
        ResourceLocation key = itemId == null ? null : ResourceLocation.tryParse(itemId);
        return key == null ? null : TREATMENTS.get(key);
    }
}
