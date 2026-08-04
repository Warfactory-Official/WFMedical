package com.warfactory.medical.datagen;

import com.warfactory.medical.WFMedical;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
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
