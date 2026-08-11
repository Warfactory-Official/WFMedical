package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.warfactory.medical.server.MedicalEngine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record GiveUpPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GiveUpPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "give_up"));

    public static final StreamCodec<FriendlyByteBuf, GiveUpPacket> STREAM_CODEC =
            CustomPacketPayload.codec(GiveUpPacket::encode, GiveUpPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static GiveUpPacket decode(FriendlyByteBuf buf) {
        return new GiveUpPacket();
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handleServer(ServerPlayer sender) {
        if (sender != null) {
            MedicalEngine.giveUp(sender);
        }
    }
}
