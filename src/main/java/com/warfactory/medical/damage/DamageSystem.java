package com.warfactory.medical.damage;

import com.warfactory.medical.MedicalSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Set;

/** Hits -> wounds: classification, armour hand-off, trauma generation. */
public final class DamageSystem implements MedicalSystem {

    @Override
    public String id() {
        return "damage";
    }

    @Override
    public Set<String> requires() {
        return Set.of("body", "wflib:armor");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        ArmorResolution.claimPlayers();
        NeoForge.EVENT_BUS.register(DamageEvents.class);
        DamageCommands.register();
    }
}
