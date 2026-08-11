package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record TreatmentTargetRequestPacket(int targetEntityId, ResourceLocation itemId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TreatmentTargetRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "treatment_target_request"));

    public static final StreamCodec<FriendlyByteBuf, TreatmentTargetRequestPacket> STREAM_CODEC =
            CustomPacketPayload.codec(TreatmentTargetRequestPacket::encode, TreatmentTargetRequestPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static TreatmentTargetRequestPacket decode(FriendlyByteBuf buf) {
        int targetEntityId = buf.readVarInt();
        ResourceLocation itemId = buf.readResourceLocation();
        return new TreatmentTargetRequestPacket(targetEntityId, itemId);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
        buf.writeResourceLocation(itemId);
    }

    public void handleServer(ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        MedicalActionService.requestTargetInfo(sender, targetEntityId, itemId);
    }
}
