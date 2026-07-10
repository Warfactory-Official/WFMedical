package com.warfactory.medical.item;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.substance.Substance;
import com.warfactory.medical.core.substance.SubstanceRegistry;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.treatment.Treatment;
import com.warfactory.medical.core.treatment.TreatmentAction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Registers the medical treatment items. Treatments are inlined here as safety-net defaults mirroring the
 * bundled TOML so items are always functional regardless of config load order.
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WFMedical.MOD_ID);
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
    // Local anesthetic: a locally-APPLIED medicine – injected into a single limb (SPEAR/needle pose), unlike
    // the swallowed general painkiller (EAT). Numbs only the aimed limb.
    public static final RegistryObject<Item> LOCAL_ANESTHETIC = medical("local_anesthetic",
            new Treatment(TreatmentAction.NUMB_LIMB,
                    cats(), 0.9F, 0.0D, 50, false), UseAnim.SPEAR);
    // Hemostatic: boosts natural blood clotting for a while (raises the self-clot severity threshold + speed).
    public static final RegistryObject<Item> HEMOSTATIC = medical("hemostatic",
            new Treatment(TreatmentAction.BOOST_CLOTTING,
                    cats(), 0.6F, 0.0D, 50, false), true);
    // Tourniquet: a removable per-limb bleed constrictor (arm/leg). Slows that limb's bleeding while on, but
    // does NOT treat the wound (unlike a bandage). Applied/removed instantly from the interaction menu.
    public static final RegistryObject<Item> TOURNIQUET = medical("tourniquet",
            new Treatment(TreatmentAction.APPLY_TOURNIQUET, cats(), 0.0F, 0.0D, 20, false));
    public static final RegistryObject<Item> MEDKIT = medical("medkit",
            new Treatment(TreatmentAction.HEAL_TRAUMA,
                    cats(), 1.0F, 250.0D, 160, true));
    public static final RegistryObject<Item> BURN_OINTMENT = medical("burn_ointment",
            new Treatment(TreatmentAction.TREAT_BURN,
                    cats(TraumaCategory.BURN, TraumaCategory.CHEMICAL_BURN), 0.8F, 0.0D, 80, false));
    public static final RegistryObject<Item> ANTIRAD_SHOT = medical("antirad_shot",
            new Treatment(TreatmentAction.TREAT_RADIATION,
                    cats(TraumaCategory.RADIATION_BURN), 1.0F, 0.0D, 40, true), true);
    // Injectable substances – hardcoded defaults so items are always functional regardless of config load order.
    public static final RegistryObject<Item> MORPHINE_SYRINGE =
            injectable("morphine_syringe", SubstanceRegistry.defaultMorphine());
    public static final RegistryObject<Item> NALOXONE_SYRINGE =
            injectable("naloxone_syringe", SubstanceRegistry.defaultNaloxone());
    // Combat Stimulant I: heavily risky, high-dose stimulant – anesthesia + speed + jump-clear + strong clotting.
    public static final RegistryObject<Item> COMBAT_STIMULANT_I =
            injectable("combat_stimulant_i", SubstanceRegistry.defaultCombatStimulant());

    private ModItems() {
    }

    private static RegistryObject<Item> medical(String name, Treatment treatment) {
        return medical(name, treatment, false);
    }

    private static RegistryObject<Item> medical(String name, Treatment treatment, boolean eatAnim) {
        return ITEMS.register(name,
                () -> new MedicalItem(new Item.Properties().stacksTo(16), treatment, eatAnim));
    }

    private static RegistryObject<Item> medical(String name, Treatment treatment, UseAnim useAnim) {
        return ITEMS.register(name,
                () -> new MedicalItem(new Item.Properties().stacksTo(16), treatment, useAnim));
    }

    private static RegistryObject<Item> injectable(String name, Substance substance) {
        return ITEMS.register(name,
                () -> new InjectableItem(new Item.Properties().stacksTo(16), substance));
    }

    private static Set<TraumaCategory> cats(TraumaCategory... c) {
        return c.length == 0 ? Collections.emptySet() : EnumSet.copyOf(java.util.Arrays.asList(c));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
