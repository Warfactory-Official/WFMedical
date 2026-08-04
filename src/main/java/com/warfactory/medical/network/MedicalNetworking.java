package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MedicalNetworking {

    private static final String PROTOCOL = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WFMedical.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);
    private static boolean registered;

    private MedicalNetworking() {
    }

    public static void register() {
        
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
