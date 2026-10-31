package com.warfactory.medical.stress;

import com.warfactory.medical.MedicalSystem;
import com.warfactory.medical.stress.client.StressClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

import java.util.Set;

/** Suppression, psy and pain raise stress; cigarettes lower it. */
public final class StressSystem implements MedicalSystem {

    @Override
    public String id() {
        return "stress";
    }

    @Override
    public Set<String> requires() {
        return Set.of("body");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        StressEvents.register();
        StressCommands.register();
    }

    @Override
    public void registerClient(IEventBus modBus) {
        StressClient.register(modBus);
    }
}
