package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.warfactory.medical.client.ClientDownedTracker;
import com.warfactory.medical.core.MedicalProfile;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;

public record DownedStatePacket(int entityId, boolean downed) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DownedStatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "downed_state"));

    public static final StreamCodec<FriendlyByteBuf, DownedStatePacket> STREAM_CODEC =
            CustomPacketPayload.codec(DownedStatePacket::encode, DownedStatePacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static DownedStatePacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean downed = buf.readBoolean();
        return new DownedStatePacket(entityId, downed);
    }

    @Override
    public int entityId() {
        return entityId;
    }

    @Override
    public boolean downed() {
        return downed;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(downed);
    }

    public void handleClient() {
        ClientDownedTracker.set(entityId, downed);
    }
}
