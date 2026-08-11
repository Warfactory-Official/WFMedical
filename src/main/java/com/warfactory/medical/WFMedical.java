package com.warfactory.medical;

import com.mojang.logging.LogUtils;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.compat.TaczCompat;
import com.warfactory.medical.config.MedicalClientConfig;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.config.MedicalDefinitions;
import com.warfactory.medical.core.damage.rig.RigSpecIO;
import com.warfactory.medical.core.damage.rig.RigTuning;
import com.warfactory.medical.core.substance.SubstanceRegistry;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.treatment.Treatment;
import com.warfactory.medical.item.ModCreativeTab;
import com.warfactory.medical.item.ModItems;
import com.warfactory.medical.network.MedicalNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Mod(WFMedical.MOD_ID)
public final class WFMedical {

    public static final String MOD_ID = "wfmedical";
    public static final String MOD_NAME = "Warfactory Medical";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WFMedical(IEventBus modBus, ModContainer modContainer) {
        ModItems.register(modBus);
        ModCreativeTab.register(modBus);
        MedicalAttachments.register(modBus);

        // Packet payloads register from RegisterPayloadHandlersEvent on the mod bus.
        modBus.addListener(MedicalNetworking::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, MedicalConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, MedicalClientConfig.SPEC);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onConfigChanged);

        LOGGER.info("[{}] {} constructed", MOD_ID, MOD_NAME);
    }

    private void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == MedicalConfig.SPEC) {
            RigTuning.ACTIVE = MedicalConfig.hitboxDebug();
            RigTuning.seedEnvelope(MedicalConfig.envelopeReachSnapshot());
            RigSpecIO.reload(FMLPaths.CONFIGDIR.get());
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            TraumaRegistry registry = new TraumaRegistry();
            Map<String, Treatment> itemTreatments = new HashMap<>();
            SubstanceRegistry substances = new SubstanceRegistry();
            try {
                MedicalDefinitions.load(FMLPaths.CONFIGDIR.get(), registry, itemTreatments, substances);
            } catch (Exception e) {
                LOGGER.error("[{}] Failed to load medical definitions; using hardcoded defaults", MOD_ID, e);
                MedicalDefinitions.loadDefaults(registry, itemTreatments, substances);
                TraumaRegistry.setActive(registry);
                SubstanceRegistry.setActive(substances);
            }

            if (TaczCompat.isLoaded()) {
                LOGGER.info("[{}] TACZ detected; gun/bullet damage will map to ballistic trauma", MOD_ID);
                // Gate this registration behind the presence check so the TACZ event classes referenced by
                // TaczHitMarkerGuard are only ever class-loaded when TACZ is actually installed.
                NeoForge.EVENT_BUS.register(com.warfactory.medical.compat.TaczHitMarkerGuard.class);
            }
        });
    }
}
