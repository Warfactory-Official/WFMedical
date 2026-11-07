package com.warfactory.medical.ragdoll;

import com.warfactory.medical.MedicalSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.BodyHooks;
import com.wf.wflib.ragdoll.api.Ragdolls;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

import java.util.Set;

/**
 * A downed (unconscious) patient goes limp under WFLib's ragdoll: the simulation, its wire and its drawing live in
 * WFLib ({@link Ragdolls}); this system only says when. Medical state and inventory stay on the player, there is no
 * proxy entity.
 */
public final class RagdollSystem implements MedicalSystem {
    @Override public String id() { return "ragdoll"; }
    @Override public Set<String> requires() { return Set.of("body", "wflib:ragdoll"); }

    @Override public void register(IEventBus modBus, ModContainer container) {
        BodyHooks.DOWNED.set(WFMedical.MOD_ID, new BodyHooks.DownedChange() {
            /** Stand up where the body lies before the posture lock goes: released with the entity at the body's low
             *  point the lock reads no room and forces prone (WFLib docs/ragdoll.md, Traps). */
            @Override public void before(ServerPlayer player, boolean downed) {
                if (!downed) {
                    Ragdolls.set(player, false);
                }
            }

            @Override public void after(ServerPlayer player, boolean downed) {
                if (downed) {
                    Ragdolls.set(player, true);
                }
            }
        });
    }
}
