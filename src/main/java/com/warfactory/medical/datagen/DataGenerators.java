package com.warfactory.medical.datagen;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.item.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;

@EventBusSubscriber(modid = WFMedical.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DataGenerators {

    private DataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeClient(),
                new MedicalItemModels(event.getGenerator().getPackOutput(), event.getExistingFileHelper()));
    }

    private static final class MedicalItemModels extends ItemModelProvider {

        MedicalItemModels(PackOutput output, ExistingFileHelper existingFileHelper) {
            super(output, WFMedical.MOD_ID, existingFileHelper);
        }

        @Override
        protected void registerModels() {
            for (DeferredHolder<Item, ? extends Item> item : ModItems.ITEMS.getEntries()) {
                String name = item.getId().getPath();
                if (existingFileHelper.exists(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, name),
                        PackType.CLIENT_RESOURCES, ".obj", "models/item")) {
                    objItem(name);
                } else {
                    basicItem(item.get());
                }
            }
        }

        private void objItem(String name) {
            getBuilder(name)
                    .parent(new ModelFile.UncheckedModelFile("builtin/entity"))
                    .transforms()
                    .transform(ItemDisplayContext.GUI)
                    .rotation(30, 225, 0).scale(0.625F).end()
                    .transform(ItemDisplayContext.GROUND)
                    .translation(0, 3, 0).scale(0.25F).end()
                    .transform(ItemDisplayContext.FIXED)
                    .rotation(0, 180, 0).scale(0.5F).end()
                    .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(75, 45, 0).translation(0, 2.5F, 0).scale(0.375F).end()
                    .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(75, 45, 0).translation(0, 2.5F, 0).scale(0.375F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(0, 45, 0).scale(0.4F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(0, 225, 0).scale(0.4F).end()
                    .end();
        }
    }
}
