package com.warfactory.medical.item;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.substance.Substance;
import com.warfactory.medical.core.substance.SubstanceRegistry;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.treatment.Treatment;
import com.warfactory.medical.core.treatment.TreatmentAction;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Registers the medical treatment items. Each item carries a {@link Treatment} describing what it does.
 *
 * <p>Treatments are constructed inline here to mirror the bundled {@code wfmedical_definitions.toml}
 * defaults so the items are always functional regardless of config load ordering. The data-driven map in
 * {@link com.warfactory.medical.config.MedicalDefinitions} remains the source of truth for damage-side
 * behaviour; these defaults are the safety net referenced by the item spec.</p>
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WFMedical.MOD_ID);

    private ModItems() {
    }

    private static RegistryObject<Item> medical(String name, Treatment treatment) {
        return medical(name, treatment, false);
    }

    private static RegistryObject<Item> medical(String name, Treatment treatment, boolean eatAnim) {
        return ITEMS.register(name,
                () -> new MedicalItem(new Item.Properties().stacksTo(16), treatment, eatAnim));
    }

    private static RegistryObject<Item> injectable(String name, Substance substance) {
        return ITEMS.register(name,
                () -> new InjectableItem(new Item.Properties().stacksTo(16), substance));
    }

    private static Set<TraumaCategory> cats(TraumaCategory... c) {
        return c.length == 0 ? Collections.emptySet() : EnumSet.copyOf(java.util.Arrays.asList(c));
    }

    public static final RegistryObject<Item> BANDAGE = medical("bandage",
            new Treatment(TreatmentAction.REDUCE_BLEEDING,
                    cats(TraumaCategory.LACERATION, TraumaCategory.PUNCTURE), 0.5F, 0.0D, 40, false));

    public static final RegistryObject<Item> SPLINT = medical("splint",
            new Treatment(TreatmentAction.STABILIZE_FRACTURE,
                    cats(TraumaCategory.FRACTURE), 1.0F, 0.0D, 60, false));

    public static final RegistryObject<Item> SUTURE_KIT = medical("suture_kit",
            new Treatment(TreatmentAction.SUTURE_WOUND,
                    cats(TraumaCategory.LACERATION, TraumaCategory.PUNCTURE), 1.0F, 0.0D, 100, false));

    public static final RegistryObject<Item> BLOOD_BAG = medical("blood_bag",
            new Treatment(TreatmentAction.RESTORE_BLOOD,
                    cats(), 0.0F, 1000.0D, 120, false));

    public static final RegistryObject<Item> PAINKILLERS = medical("painkillers",
            new Treatment(TreatmentAction.REDUCE_PAIN,
                    cats(), 0.5F, 0.0D, 30, false), true);

    public static final RegistryObject<Item> TOURNIQUET = medical("tourniquet",
            new Treatment(TreatmentAction.REDUCE_BLEEDING,
                    cats(TraumaCategory.LACERATION, TraumaCategory.PUNCTURE, TraumaCategory.INTERNAL_BLEEDING),
                    0.9F, 0.0D, 60, false));

    public static final RegistryObject<Item> MEDKIT = medical("medkit",
            new Treatment(TreatmentAction.HEAL_TRAUMA,
                    cats(), 1.0F, 250.0D, 160, true));

    public static final RegistryObject<Item> BURN_OINTMENT = medical("burn_ointment",
            new Treatment(TreatmentAction.TREAT_BURN,
                    cats(TraumaCategory.BURN, TraumaCategory.CHEMICAL_BURN), 0.8F, 0.0D, 80, false));

    public static final RegistryObject<Item> ANTIRAD_SHOT = medical("antirad_shot",
            new Treatment(TreatmentAction.TREAT_RADIATION,
                    cats(TraumaCategory.RADIATION_BURN), 1.0F, 0.0D, 40, true), true);

    // Injectable/opioid substances. Constructed from the SubstanceRegistry hardcoded defaults so the items
    // are always functional regardless of config load ordering (mirrors how treatments are inlined above).
    public static final RegistryObject<Item> MORPHINE_SYRINGE =
            injectable("morphine_syringe", SubstanceRegistry.defaultMorphine());

    public static final RegistryObject<Item> NALOXONE_SYRINGE =
            injectable("naloxone_syringe", SubstanceRegistry.defaultNaloxone());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
