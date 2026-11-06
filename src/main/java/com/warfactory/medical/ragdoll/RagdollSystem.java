package com.warfactory.medical.ragdoll;

import com.warfactory.medical.MedicalSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.BodyHooks;
import com.warfactory.medical.ragdoll.client.RagdollClient;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Set;

/** Optional physics presentation; medical state and the original Corpse inventory remain authoritative. */
public final class RagdollSystem implements MedicalSystem {
    @Override public String id() { return "ragdoll"; }
    @Override public Set<String> requires() { return Set.of("body", "treatment"); }

    @Override public void register(IEventBus modBus, ModContainer container) {
        if (!ModList.get().isLoaded("saros_ragdoll") || !ModList.get().isLoaded("corpse")) return;
        Ragdolls.register(modBus);
        BodyHooks.TREATMENT_TARGET.set(WFMedical.MOD_ID, entity -> {
            if (entity instanceof MedicalRagdollEntity ragdoll) entity = ragdoll.target();
            return entity instanceof LivingEntity living ? living : null;
        });
        NeoForge.EVENT_BUS.register(Ragdolls.class);
    }

    @Override public void registerClient(IEventBus modBus) {
        if (ModList.get().isLoaded("saros_ragdoll") && ModList.get().isLoaded("corpse") && Ragdolls.enabled()) RagdollClient.register(modBus);
    }
}
