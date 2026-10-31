package com.warfactory.medical.treatment;

import com.warfactory.medical.WFMedical;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.UseAnim;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Medical item holders; bound iff {@code treatment} is on. */
public final class TreatmentContent {

    private static final List<Entry> ENTRIES = new ArrayList<>();
    public static final DeferredItem<Item> BANDAGE = medical("bandage", UseAnim.BOW);
    public static final DeferredItem<Item> SPLINT = medical("splint", UseAnim.BOW);
    public static final DeferredItem<Item> SUTURE_KIT = medical("suture_kit", UseAnim.BOW);
    public static final DeferredItem<Item> BLOOD_BAG = medical("blood_bag", UseAnim.BOW);
    public static final DeferredItem<Item> PAINKILLERS = holder("painkillers",
            () -> new MedicalItem(new Item.Properties().stacksTo(16), id("painkillers"), true).selfOnly());
    public static final DeferredItem<Item> LOCAL_ANESTHETIC = medical("local_anesthetic", UseAnim.SPEAR);
    public static final DeferredItem<Item> HEMOSTATIC = medical("hemostatic", UseAnim.EAT);
    public static final DeferredItem<Item> TOURNIQUET = medical("tourniquet", UseAnim.BOW);
    public static final DeferredItem<Item> MEDKIT = medical("medkit", UseAnim.BOW);
    public static final DeferredItem<Item> BURN_OINTMENT = medical("burn_ointment", UseAnim.BOW);
    public static final DeferredItem<Item> ANTIRAD_SHOT = medical("antirad_shot", UseAnim.EAT);
    public static final List<DeferredItem<Item>> ITEMS =
            Collections.unmodifiableList(ENTRIES.stream().map(Entry::holder).toList());

    private TreatmentContent() {
    }

    private static DeferredItem<Item> medical(String name, UseAnim useAnim) {
        return holder(name, () -> new MedicalItem(new Item.Properties().stacksTo(16), id(name), useAnim));
    }

    private static DeferredItem<Item> holder(String name, Supplier<Item> factory) {
        DeferredItem<Item> holder = DeferredItem.createItem(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, name));
        ENTRIES.add(new Entry(holder, factory));
        return holder;
    }

    private static String id(String name) {
        return WFMedical.MOD_ID + ":" + name;
    }

    static void register(DeferredRegister.Items items) {
        for (Entry e : ENTRIES) {
            items.register(e.holder().getId().getPath(), e.factory());
        }
    }

    private record Entry(DeferredItem<Item> holder, Supplier<Item> factory) {
    }
}
