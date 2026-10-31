package com.warfactory.medical.treatment.client;

import com.norwood.ahf.client.compat.PoseSetLayers;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.treatment.MedicalItem;
import com.warfactory.medical.treatment.MedicalPoses;
import com.wf.wflib.ui.api.UiHud;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.Set;

/** Client half of {@code treatment}. */
public final class TreatmentClient {

    private TreatmentClient() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(MedicalClientEvents.class);
        NeoForge.EVENT_BUS.register(TreatmentInteractions.class);
        NeoForge.EVENT_BUS.register(ClientTourniquetTracker.class);
        NeoForge.EVENT_BUS.register(TourniquetArmRenderer.class);
        NeoForge.EVENT_BUS.register(TreatmentHands.class);
        modBus.addListener(RegisterKeyMappingsEvent.class, MedicalKeyMappings::register);
        modBus.addListener(EntityRenderersEvent.AddLayers.class, event -> {
            for (PlayerSkin.Model skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                    renderer.addLayer(new TourniquetLayer(renderer));
                }
            }
        });
        modBus.addListener(RegisterGuiLayersEvent.class, event -> UiHud.layer(event, rl("action_progress"),
                UiHud.Position.ABOVE_ALL, null, ActionProgressOverlay.INSTANCE));
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            ActionProgressOverlay.registerSignals();
            MedInteractionScreen.registerSignals();
        }));
        modBus.addListener(RegisterClientExtensionsEvent.class, event -> event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return MedicalItemRenderer.get();
            }
        }, BuiltInRegistries.ITEM.stream().filter(MedicalItem.class::isInstance).toArray(Item[]::new)));
        if (ModList.get().isLoaded("playeranimator")) {
            modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> PoseSetLayers.register(
                    rl("treatment"), 87, Set.of(MedicalPoses.MEDKIT, MedicalPoses.BANDAGE))));
        }
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, path);
    }
}
