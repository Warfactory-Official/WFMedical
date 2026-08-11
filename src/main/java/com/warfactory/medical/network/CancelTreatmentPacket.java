package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record CancelTreatmentPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CancelTreatmentPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "cancel_treatment"));

    public static final StreamCodec<FriendlyByteBuf, CancelTreatmentPacket> STREAM_CODEC =
            CustomPacketPayload.codec(CancelTreatmentPacket::encode, CancelTreatmentPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static CancelTreatmentPacket decode(FriendlyByteBuf buf) {
        return new CancelTreatmentPacket();
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handleServer(ServerPlayer sender) {
        if (sender != null) {
            MedicalActionService.cancel(sender, "interrupted");
        }
    }
}
