package com.warfactory.medical;

import com.mojang.logging.LogUtils;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Mod(WFMedical.MOD_ID)
public final class WFMedical {

    public static final String MOD_ID = "wfmedical";
    public static final String MOD_NAME = "Warfactory Medical";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WFMedical(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModItems.register(modBus);
        ModCreativeTab.register(modBus);

        MedicalNetworking.register();

        context.registerConfig(ModConfig.Type.COMMON, MedicalConfig.SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, MedicalClientConfig.SPEC);

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
                MinecraftForge.EVENT_BUS.register(com.warfactory.medical.compat.TaczHitMarkerGuard.class);
            }
        });
    }
}
