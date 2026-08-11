package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.overlay.ActionProgressOverlay;
import com.warfactory.medical.client.overlay.DamageOutlineOverlay;
import com.warfactory.medical.client.overlay.HealthBarOverlay;
import com.warfactory.medical.client.render.TourniquetLayer;
import com.warfactory.medical.compat.playeranim.PlayerAnimHitbox;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = WFMedical.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WFMedicalClient {

    private static boolean overlaysRegistered;

    private WFMedicalClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        MedicalKeyMappings.register(event);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("playeranimator")) {
            event.enqueueWork(PlayerAnimHitbox::register);
        }
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new TourniquetLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        if (overlaysRegistered) {
            return;
        }
        overlaysRegistered = true;
        event.registerAbove(
                VanillaGuiLayers.PLAYER_HEALTH,
                ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "health"),
                HealthBarOverlay.INSTANCE);
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "damage_outline"),
                DamageOutlineOverlay.INSTANCE);
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "action_progress"),
                ActionProgressOverlay.INSTANCE);
    }
}
