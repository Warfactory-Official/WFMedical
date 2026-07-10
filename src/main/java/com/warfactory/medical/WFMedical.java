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

/**
 * Warfactory Medical mod entry point. Registers items, networking, config, and defers TOML load.
 */
@Mod(WFMedical.MOD_ID)
public final class WFMedical {

    public static final String MOD_ID = "wfmedical";
    public static final String MOD_NAME = "Warfactory Medical";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WFMedical(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        // Registry objects (DeferredRegisters) onto the mod bus.
        ModItems.register(modBus);
        ModCreativeTab.register(modBus);

        // S2C networking channel (idempotent; safe during construction).
        MedicalNetworking.register();

        // COMMON config: numeric engine tunables (TOML).
        context.registerConfig(ModConfig.Type.COMMON, MedicalConfig.SPEC);
        // CLIENT config: per-client HUD preferences (never synced), e.g. the damage-outline position.
        context.registerConfig(ModConfig.Type.CLIENT, MedicalClientConfig.SPEC);

        // Mod-bus lifecycle listeners. NOTE: the medical capability registers itself lazily through the
        // CapabilityManager.get(CapabilityToken) call in MedicalCapabilities (as Forge 1.20.1 requires) –
        // do NOT also call RegisterCapabilitiesEvent.register() for it, that double-registers and crashes.
        modBus.addListener(this::onCommonSetup);
        // Mirror the hitboxDebug flag into the rig-tuning hot-path switch on load and every config reload.
        modBus.addListener(this::onConfigChanged);

        LOGGER.info("[{}] {} constructed", MOD_ID, MOD_NAME);
    }

    /**
     * Keep {@link RigTuning#ACTIVE} in step with {@code hitlocation.hitboxDebug} on config load/reload, so the
     * limb-box tuning path is only ever armed when the test flag is set. Fires for our COMMON spec only.
     */
    private void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == MedicalConfig.SPEC) {
            RigTuning.ACTIVE = MedicalConfig.hitboxDebug();
            // Seed the live per-stance envelope reach from config so tuning starts from the persisted values.
            RigTuning.seedEnvelope(MedicalConfig.envelopeReachSnapshot());
            // R2: load the data-driven limb-box geometry override (or reset to built-in defaults if absent).
            RigSpecIO.reload(FMLPaths.CONFIGDIR.get());
        }
    }

    /**
     * Load TOML definitions (or hardcoded fallback), then activate registries.
     */
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
            }
        });
    }
}
