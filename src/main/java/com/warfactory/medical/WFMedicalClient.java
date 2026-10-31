package com.warfactory.medical;

import com.wf.wflib.core.api.WFSystems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = WFMedical.MOD_ID, dist = Dist.CLIENT)
public final class WFMedicalClient {

    public WFMedicalClient(IEventBus modBus) {
        WFSystems.startClient(WFMedical.MOD_ID, modBus);
    }
}
