package com.warfactory.medical;

import com.mojang.logging.LogUtils;
import com.warfactory.medical.network.MedicalNetworking;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(WFMedical.MOD_ID)
public final class WFMedical {

    public static final String MOD_ID = "wfmedical";
    public static final String MOD_NAME = "Warfactory Medical";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WFMedical(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        MedicalNetworking.register();

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onConfigChanged);

        LOGGER.info("[{}] {} constructed", MOD_ID, MOD_NAME);
    }

    private void onConfigChanged(ModConfigEvent event) {
        
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            
        });
    }
}
