package com.warfactory.medical.substance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** Syringe holders; bound iff {@code substance} is on. */
public final class SubstanceContent {

    public static final DeferredItem<Item> MORPHINE_SYRINGE = DeferredItem.createItem(Substances.MORPHINE);
    public static final DeferredItem<Item> NALOXONE_SYRINGE = DeferredItem.createItem(Substances.NALOXONE);
    public static final DeferredItem<Item> COMBAT_STIMULANT_I = DeferredItem.createItem(Substances.COMBAT_STIMULANT);
    public static final List<DeferredItem<Item>> ITEMS = List.of(MORPHINE_SYRINGE, NALOXONE_SYRINGE, COMBAT_STIMULANT_I);

    private SubstanceContent() {
    }

    static void register(DeferredRegister.Items items) {
        for (DeferredItem<Item> holder : ITEMS) {
            ResourceLocation key = holder.getId();
            items.register(key.getPath(), () -> new InjectableItem(new Item.Properties().stacksTo(16), key));
        }
    }
}
