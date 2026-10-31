package com.warfactory.medical.body.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.BodyHooks;
import com.warfactory.medical.body.DerivedStats;
import com.wf.wflib.ui.api.UiHud;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Client half of {@code body}. */
public final class BodyClient {

    private BodyClient() {
    }

    public static void register(IEventBus modBus) {
        BodyHooks.CLIENT.set(WFMedical.MOD_ID, new BodyHooks.ClientView() {
            @Override
            public DerivedStats localStats() {
                return ClientMedicalCache.stats();
            }

            @Override
            public boolean isDowned(int entityId) {
                return ClientDownedTracker.isDowned(entityId);
            }
        });
        NeoForge.EVENT_BUS.register(BodyClientEvents.class);
        NeoForge.EVENT_BUS.register(DamageOutlineOverlay.class);
        modBus.addListener(RegisterKeyMappingsEvent.class, BodyKeys::register);
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(HealthBarOverlay::registerSignals));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> {
            UiHud.layer(event, ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "health"), UiHud.Position.ABOVE,
                    VanillaGuiLayers.PLAYER_HEALTH, HealthBarOverlay.INSTANCE);
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "damage_outline"),
                    DamageOutlineOverlay.INSTANCE);
            event.registerAboveAll(PainVignetteOverlay.OVERLAY_ID, PainVignetteOverlay.INSTANCE);
            event.registerAboveAll(UnconsciousOverlay.OVERLAY_ID, UnconsciousOverlay.INSTANCE);
            event.registerAbove(UnconsciousOverlay.OVERLAY_ID, GiveUpOverlay.OVERLAY_ID, GiveUpOverlay.INSTANCE);
        });
    }
}
