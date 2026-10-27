package com.warfactory.medical;

import com.mojang.logging.LogUtils;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.norwood.ahf.hook.AhfHooks;
import com.warfactory.medical.item.MedicalPoses;
import com.warfactory.medical.compat.OpenPersistenceCompat;
import com.warfactory.medical.config.MedicalClientConfig;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.config.MedicalDefinitions;
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
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
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

        modBus.addListener(MedicalNetworking::register);
        MedicalPoses.register();

        modContainer.registerConfig(ModConfig.Type.COMMON, MedicalConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, MedicalClientConfig.SPEC);

        modBus.addListener(this::onCommonSetup);

        LOGGER.info("[{}] {} constructed", MOD_ID, MOD_NAME);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Before anything else: tells WFLib, if it is here, that this mod answers for
        // players' armour per limb, so the two do not both reduce the same hit.
        com.warfactory.medical.compat.wflib.WfLibArmorCompat.init();
        if (OpenPersistenceCompat.isLoaded()) {
            AhfHooks.addEnvelopeTarget(e -> MedicalConfig.openPersistenceCompat() && OpenPersistenceCompat.isPersistentBody(e));
        }
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
        });
    }
}
