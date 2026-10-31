package com.warfactory.medical;

import com.mojang.logging.LogUtils;
import com.warfactory.medical.body.BodySystem;
import com.warfactory.medical.damage.DamageSystem;
import com.warfactory.medical.damage.gametest.WfLibRounds;
import com.warfactory.medical.fx.FxSystem;
import com.warfactory.medical.persistence.PersistenceSystem;
import com.warfactory.medical.stress.StressSystem;
import com.warfactory.medical.substance.SubstanceSystem;
import com.warfactory.medical.treatment.TreatmentSystem;
import com.wf.wflib.core.api.WFSystems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;

@Mod(WFMedical.MOD_ID)
public final class WFMedical {

    public static final String MOD_ID = "wfmedical";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Registration order: body before damage before persistence keeps same-priority hurt listeners in order. */
    public static final List<MedicalSystem> SYSTEMS = List.of(
            new BodySystem(),
            new DamageSystem(),
            new TreatmentSystem(),
            new SubstanceSystem(),
            new StressSystem(),
            new FxSystem(),
            new PersistenceSystem());

    public WFMedical(IEventBus modBus, ModContainer modContainer) {
        WfLibRounds.request();
        WFSystems.define(MOD_ID, modBus, modContainer, SYSTEMS);
    }
}
