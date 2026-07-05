package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.MedicalProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Server-authoritative sync channel for the medical system. Only S2C packets are registered: clients
 * never create or remove trauma, they only receive {@link MedicalSyncPacket} full snapshots and
 * {@link TraumaDeltaPacket} deltas.
 */
public final class MedicalNetworking {

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WFMedical.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static boolean registered;

    private MedicalNetworking() {
    }

    /** Register the S2C packets. Idempotent; call once from mod construction. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CHANNEL.messageBuilder(MedicalSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MedicalSyncPacket::encode)
                .decoder(MedicalSyncPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleClient();
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(TraumaDeltaPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TraumaDeltaPacket::encode)
                .decoder(TraumaDeltaPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleClient();
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    /** Send a full authoritative snapshot to one player. */
    public static void sendFull(ServerPlayer player, MedicalProfile profile) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), MedicalSyncPacket.fromProfile(profile));
    }

    /** Send an incremental trauma change to one player. */
    public static void sendDelta(ServerPlayer player, TraumaDeltaPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
