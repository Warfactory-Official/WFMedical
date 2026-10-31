package com.warfactory.medical.persistence;

import com.norwood.ahf.hook.AhfHooks;
import com.warfactory.medical.MedicalSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.BodyHooks;
import com.warfactory.medical.body.MedicalConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Set;

/** Open Persistence logout bodies carry the profile; no-op without that mod. */
public final class PersistenceSystem implements MedicalSystem {

    @Override
    public String id() {
        return "persistence";
    }

    @Override
    public Set<String> requires() {
        return Set.of("body", "damage");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        if (!OpenPersistenceCompat.isLoaded()) {
            return;
        }
        BodyHooks.PERSISTENT_BODY.set(WFMedical.MOD_ID,
                e -> MedicalConfig.openPersistenceCompat() && OpenPersistenceCompat.isPersistentBody(e));
        NeoForge.EVENT_BUS.register(PersistenceEvents.class);
        modBus.addListener(FMLCommonSetupEvent.class, event ->
                AhfHooks.addEnvelopeTarget(BodyHooks.PERSISTENT_BODY.get()));
    }
}
