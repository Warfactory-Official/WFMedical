package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** First-person treatment clip of an item: {@code assets/wfmedical/treatment/<name>.json} (tools/treatment). */
public enum TreatmentHandKind {
    BANDAGE("item"),
    TOURNIQUET("item"),
    MEDKIT("item", "lid"),
    INJECTOR("item"),
    SYRINGE("item");

    public final ResourceLocation clip;
    /** Prop tracks; model {@code models/treatment/<name>_<track>.glb}. */
    public final String[] tracks;

    TreatmentHandKind(String... tracks) {
        String name = name().toLowerCase(Locale.ROOT);
        this.clip = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "treatment/" + name + ".json");
        this.tracks = tracks;
    }

    public ResourceLocation model(String track) {
        return ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID,
                "models/treatment/" + name().toLowerCase(Locale.ROOT) + "_" + track + ".glb");
    }

    /** Null => vanilla hands (oral items, foreign items). */
    @Nullable
    public static TreatmentHandKind of(ResourceLocation item) {
        if (!item.getNamespace().equals(WFMedical.MOD_ID)) {
            return null;
        }
        return switch (item.getPath()) {
            case "bandage", "splint", "suture_kit", "blood_bag", "burn_ointment" -> BANDAGE;
            case "tourniquet" -> TOURNIQUET;
            case "medkit" -> MEDKIT;
            case "antirad_shot", "combat_stimulant_i" -> INJECTOR;
            case "morphine_syringe", "naloxone_syringe", "local_anesthetic" -> SYRINGE;
            default -> null;
        };
    }
}
