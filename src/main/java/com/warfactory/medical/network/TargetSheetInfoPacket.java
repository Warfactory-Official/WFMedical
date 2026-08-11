package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;

public record TargetSheetInfoPacket(int targetEntityId, MedicalSyncPacket snapshot, int tourniquetMask) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TargetSheetInfoPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "target_sheet_info"));

    public static final StreamCodec<FriendlyByteBuf, TargetSheetInfoPacket> STREAM_CODEC =
            CustomPacketPayload.codec(TargetSheetInfoPacket::encode, TargetSheetInfoPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static TargetSheetInfoPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        MedicalSyncPacket snap = MedicalSyncPacket.decode(buf);
        int mask = buf.readVarInt();
        return new TargetSheetInfoPacket(id, snap, mask);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
        snapshot.encode(buf);
        buf.writeVarInt(tourniquetMask);
    }

    public void handleClient() {
        com.warfactory.medical.client.screen.MedInteractionScreen.onTargetSheetInfo(this);
    }
}
