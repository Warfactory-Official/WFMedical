package com.warfactory.medical.item;

import com.warfactory.medical.WFMedical;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The single "Warfactory Medical" creative tab. Its display list is populated from every entry in
 * {@link ModItems}, so new items appear automatically. Icon is the medkit.
 */
public final class ModCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WFMedical.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MEDICAL = TABS.register("medical",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + WFMedical.MOD_ID + ".medical"))
                    .icon(() -> new ItemStack(ModItems.MEDKIT.get()))
                    .displayItems((params, output) -> {
                        for (RegistryObject<net.minecraft.world.item.Item> item : ModItems.ITEMS.getEntries()) {
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
