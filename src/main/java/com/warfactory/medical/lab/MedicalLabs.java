package com.warfactory.medical.lab;

import com.wf.wflib.labs.api.LabContent;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabProducers;

import java.util.Set;

/** Registers the WFMedical lab producers; called once, at construction, when wflib's labs system is on. */
public final class MedicalLabs {
    private MedicalLabs() {
    }

    public static void register() {
        LabProducers.register(new WoundsProducer());
        LabProducers.register(new HitZonesProducer());
        LabProducers.register(new ChainProducer());
        LabProducers.register(new HazardsProducer());
        LabContents.register(new LabContent() {
            @Override
            public String id() {
                return "wfmedical:medical";
            }

            @Override
            public Set<String> configFiles() {
                return Set.of("wfmedical-common.toml");
            }
        });
    }
}
