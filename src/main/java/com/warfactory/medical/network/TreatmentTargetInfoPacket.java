package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record TreatmentTargetInfoPacket(int targetEntityId, ResourceLocation itemId, LimbSummary[] limbs,
                                        int treatableMask) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TreatmentTargetInfoPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "treatment_target_info"));

    public static final StreamCodec<FriendlyByteBuf, TreatmentTargetInfoPacket> STREAM_CODEC =
            CustomPacketPayload.codec(TreatmentTargetInfoPacket::encode, TreatmentTargetInfoPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static TreatmentTargetInfoPacket decode(FriendlyByteBuf buf) {
        int targetEntityId = buf.readVarInt();
        ResourceLocation itemId = buf.readResourceLocation();
        int count = buf.readVarInt();
        LimbSummary[] limbs = new LimbSummary[count];
        for (int i = 0; i < count; i++) {
            limbs[i] = MedicalSyncPacket.readLimb(buf);
        }
        int treatableMask = buf.readVarInt();
        return new TreatmentTargetInfoPacket(targetEntityId, itemId, limbs, treatableMask);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
        buf.writeResourceLocation(itemId);
        buf.writeVarInt(limbs.length);
        for (LimbSummary s : limbs) {
            MedicalSyncPacket.writeLimb(buf, s);
        }
        buf.writeVarInt(treatableMask);
    }

    public void handleClient() {
        com.warfactory.medical.client.TreatmentInteractions.onTargetInfo(this);
    }
}
