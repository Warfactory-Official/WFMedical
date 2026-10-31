package com.warfactory.medical.substance;

import com.warfactory.medical.MedicalSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.treatment.ModCreativeTab;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/** Syringes and drug pharmacology; used through treatment actions. */
public final class SubstanceSystem implements MedicalSystem {

    @Override
    public String id() {
        return "substance";
    }

    @Override
    public Set<String> requires() {
        return Set.of("body", "treatment");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        DeferredRegister.Items items = DeferredRegister.createItems(WFMedical.MOD_ID);
        SubstanceContent.register(items);
        items.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey() == ModCreativeTab.MEDICAL) {
                SubstanceContent.ITEMS.forEach(item -> event.accept(item.get()));
            }
        });
        SubstanceDefinitions.load(FMLPaths.CONFIGDIR.get())
                .forEach(substance -> Substances.SUBSTANCES.register(ResourceLocation.parse(substance.itemId()), substance));
        SubstanceCommands.register();
    }
}
