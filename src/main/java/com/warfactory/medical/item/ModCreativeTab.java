package com.warfactory.medical.item;

import com.warfactory.medical.WFMedical;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WFMedical.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MEDICAL = TABS.register("medical",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + WFMedical.MOD_ID + ".medical"))
                    .icon(() -> new ItemStack(ModItems.MEDKIT.get()))
                    .displayItems((params, output) -> {
                        for (DeferredHolder<net.minecraft.world.item.Item, ? extends net.minecraft.world.item.Item> item : ModItems.ITEMS.getEntries()) {
                            output.accept(item.get());
                        }
                    })
                    .build());

    private ModCreativeTab() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
