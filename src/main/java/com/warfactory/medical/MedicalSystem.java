package com.warfactory.medical;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.core.api.WFSystems;

/** A WFMedical system: on/off in {@code wfmedical-systems.toml}. */
public interface MedicalSystem extends WFSystem {

    @Override
    default boolean enabled() {
        return WFSystems.enabled(WFMedical.MOD_ID + ":" + id());
    }
}
