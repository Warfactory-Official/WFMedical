package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.item.MedicalPoses;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** {@link MedicalPoses} kind of player {@code entityId} to its trackers and itself. */
public record TreatmentPosePacket(int entityId, MedicalPoses.Kind kind) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TreatmentPosePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "treatment_pose"));

    public static final StreamCodec<FriendlyByteBuf, TreatmentPosePacket> STREAM_CODEC =
            CustomPacketPayload.codec(TreatmentPosePacket::encode, TreatmentPosePacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static TreatmentPosePacket decode(FriendlyByteBuf buf) {
        return new TreatmentPosePacket(buf.readVarInt(), buf.readEnum(MedicalPoses.Kind.class));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeEnum(kind);
    }

    public void handleClient() {
        MedicalPoses.client(entityId, kind);
    }
}
