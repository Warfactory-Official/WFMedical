package com.warfactory.medical.datagen;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.item.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * Data generation entry point. Runs via {@code gradlew runData} and writes into {@code src/generated/resources}
 * (already a resource root). Currently generates a basic {@code item/generated} model for every registered
 * medical item, so each item has a model at runtime (fixes the {@code FileNotFoundException:
 * wfmedical:models/item/<name>.json} that occurs when items ship without models).
 *
 * <p>The generated model references {@code wfmedical:item/<name>}; Forge's model provider validates that the
 * texture exists during the data run, so every item needs {@code assets/wfmedical/textures/item/<name>.png}.</p>
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DataGenerators {

    private DataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeClient(),
                new MedicalItemModels(event.getGenerator().getPackOutput(), event.getExistingFileHelper()));
    }

    /**
     * Generates a flat {@code item/generated} model for each item in {@link ModItems#ITEMS}.
     */
    private static final class MedicalItemModels extends ItemModelProvider {

        MedicalItemModels(PackOutput output, ExistingFileHelper existingFileHelper) {
            super(output, WFMedical.MOD_ID, existingFileHelper);
        }

        @Override
        protected void registerModels() {
            for (RegistryObject<Item> item : ModItems.ITEMS.getEntries()) {
                String name = item.getId().getPath();
                // Items that ship an OBJ get a builtin/entity model so they route through the MedicalItemRenderer
                // BEWLR; the rest keep a flat generated sprite so they are never invisible.
                if (existingFileHelper.exists(new ResourceLocation(WFMedical.MOD_ID, name),
                        PackType.CLIENT_RESOURCES, ".obj", "models/item")) {
                    objItem(name);
                } else {
                    basicItem(item.get());
                }
            }
        }

        /**
         * A {@code builtin/entity} item model (rendered by the BEWLR) with block-item-like display transforms.
         * The transform values are a tunable starting point &mdash; adjust alongside
         * {@code MedicalItemRenderer.BASE_SCALE} to seat each OBJ in the GUI / hand / ground views.
         */
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
