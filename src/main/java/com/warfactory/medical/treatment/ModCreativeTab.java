package com.warfactory.medical.treatment;

import com.warfactory.medical.WFMedical;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTab {

    public static final ResourceKey<CreativeModeTab> MEDICAL = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "medical"));

    private ModCreativeTab() {
    }

    static void register(IEventBus modBus) {
        DeferredRegister<CreativeModeTab> tabs = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WFMedical.MOD_ID);
        tabs.register(MEDICAL.location().getPath(), () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + WFMedical.MOD_ID + ".medical"))
                .icon(() -> new ItemStack(TreatmentContent.MEDKIT.get()))
                .displayItems((params, output) -> TreatmentContent.ITEMS.forEach(item -> output.accept(item.get())))
                .build());
        tabs.register(modBus);
    }
}
