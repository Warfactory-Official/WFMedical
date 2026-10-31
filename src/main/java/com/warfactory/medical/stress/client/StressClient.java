package com.warfactory.medical.stress.client;

import com.warfactory.medical.WFMedical;
import com.wf.wflib.sway.api.client.CameraSway;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client half of {@code stress}. */
public final class StressClient {

    private StressClient() {
    }

    public static void register(IEventBus modBus) {
        CameraSway.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "stress"), 0,
                new StressShakeHandler());
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(
                StressVignetteOverlay.OVERLAY_ID, StressVignetteOverlay.INSTANCE));
    }
}
