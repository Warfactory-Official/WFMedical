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
 * Server-authoritative sync channel for the medical system.
 *
 * <p>S2C packets carry authoritative state ({@link MedicalSyncPacket} full snapshots,
 * {@link TraumaDeltaPacket} deltas, {@link ActiveTreatmentPacket} action-progress). C2S packets are pure
 * REQUESTS ({@link MedicalActionPacket} start-a-treatment, {@link SetTargetLimbPacket} set-target-hint):
 * clients never create or remove trauma, the server validates every request before acting.</p>
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

    /** Register all packets (S2C state + C2S requests). Idempotent; call once from mod construction. */
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

        // C2S: request to begin a timed treatment.
        CHANNEL.messageBuilder(MedicalActionPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MedicalActionPacket::encode)
                .decoder(MedicalActionPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleServer(ctx.get().getSender());
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // C2S: set the player's targeting hint.
        CHANNEL.messageBuilder(SetTargetLimbPacket.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetTargetLimbPacket::encode)
                .decoder(SetTargetLimbPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleServer(ctx.get().getSender());
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // S2C: active-treatment progress state for the client overlay.
        CHANNEL.messageBuilder(ActiveTreatmentPacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ActiveTreatmentPacket::encode)
                .decoder(ActiveTreatmentPacket::decode)
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

    /** Send the active-treatment progress state to one player (start / completion / cancellation). */
    public static void sendActiveTreatment(ServerPlayer player, ActiveTreatmentPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Send a client-originated request packet to the server. Used by the client UI for
     * {@link MedicalActionPacket} and {@link SetTargetLimbPacket}; the server validates every request.
     */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
