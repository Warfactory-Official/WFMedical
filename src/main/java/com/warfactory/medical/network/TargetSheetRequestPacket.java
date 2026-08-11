package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record TargetSheetRequestPacket(int targetEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TargetSheetRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "target_sheet_request"));

    public static final StreamCodec<FriendlyByteBuf, TargetSheetRequestPacket> STREAM_CODEC =
            CustomPacketPayload.codec(TargetSheetRequestPacket::encode, TargetSheetRequestPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static TargetSheetRequestPacket decode(FriendlyByteBuf buf) {
        return new TargetSheetRequestPacket(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
    }

    public void handleServer(ServerPlayer sender) {
        if (sender != null) {
            MedicalActionService.requestTargetSheet(sender, targetEntityId);
        }
    }
}
