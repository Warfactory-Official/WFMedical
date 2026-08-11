package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.warfactory.medical.client.PoseStreamClient;
import com.warfactory.medical.core.damage.HitAuthority;
import net.minecraft.network.FriendlyByteBuf;

public record HitAuthorityPacket(boolean streamPose) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HitAuthorityPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "hit_authority"));

    public static final StreamCodec<FriendlyByteBuf, HitAuthorityPacket> STREAM_CODEC =
            CustomPacketPayload.codec(HitAuthorityPacket::encode, HitAuthorityPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static HitAuthorityPacket decode(FriendlyByteBuf buf) {
        return new HitAuthorityPacket(buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(streamPose);
    }

    public void handleClient() {
        PoseStreamClient.setEnabled(streamPose);
    }
}
