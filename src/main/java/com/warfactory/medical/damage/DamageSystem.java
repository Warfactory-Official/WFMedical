package com.warfactory.medical.damage;

import com.warfactory.medical.MedicalSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Set;

/** Hits -> wounds: classification, armour hand-off, trauma generation. */
public final class DamageSystem implements MedicalSystem {

    @Override
    public String id() {
        return "damage";
    }

    @Override
    public Set<String> requires() {
        return Set.of("body", "treatment", "wflib:armor");
    }

    @Override
    public void registerClient(IEventBus modBus) {
        com.warfactory.medical.damage.client.HitReactionAnim.register(modBus);
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        ArmorResolution.claimPlayers();
        ExoBridge.register();
        NeoForge.EVENT_BUS.register(DamageEvents.class);
        NeoForge.EVENT_BUS.register(HitReactions.class);
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("damage-1")
                .playToClient(HitReactionPacket.TYPE, HitReactionPacket.STREAM_CODEC,
                        (packet, ctx) -> com.warfactory.medical.damage.client.HitReactionAnim.play(packet)));
        DamageCommands.register();
        if (com.wf.wflib.core.api.WFSystems.enabled("wflib:labs")) {
            com.warfactory.medical.lab.MedicalLabs.register();
        }
    }
}
