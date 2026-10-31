package com.warfactory.medical.fx;

import com.warfactory.medical.MedicalSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Set;

/** Client-only post-processing: GAMMA screen effects, blood desaturation, opioid haze, pass-out blur. */
public final class FxSystem implements MedicalSystem {

    @Override
    public String id() {
        return "fx";
    }

    @Override
    public Set<String> requires() {
        return Set.of("body", "wflib:xray");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
    }

    @Override
    public void registerClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(ScreenEffects.class);
        NeoForge.EVENT_BUS.register(BloodDesaturationEffect.class);
        NeoForge.EVENT_BUS.register(OpioidHazeEffect.class);
        NeoForge.EVENT_BUS.register(PassoutBlurEffect.class);
    }
}
