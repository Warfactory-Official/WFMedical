package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.warfactory.medical.client.ClientTourniquetTracker;
import net.minecraft.network.FriendlyByteBuf;

public record TourniquetStatePacket(int entityId, int mask) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TourniquetStatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "tourniquet_state"));

    public static final StreamCodec<FriendlyByteBuf, TourniquetStatePacket> STREAM_CODEC =
            CustomPacketPayload.codec(TourniquetStatePacket::encode, TourniquetStatePacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static TourniquetStatePacket decode(FriendlyByteBuf buf) {
        return new TourniquetStatePacket(buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(mask);
    }

    public void handleClient() {
        ClientTourniquetTracker.set(entityId, mask);
    }
}
