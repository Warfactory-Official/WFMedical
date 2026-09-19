package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Client request to begin manual resuscitation on a downed casualty. Carries no item: CPR needs none. */
public record ResuscitatePacket(int targetEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ResuscitatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "resuscitate"));

    public static final StreamCodec<FriendlyByteBuf, ResuscitatePacket> STREAM_CODEC =
            CustomPacketPayload.codec(ResuscitatePacket::encode, ResuscitatePacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ResuscitatePacket decode(FriendlyByteBuf buf) {
        return new ResuscitatePacket(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
    }

    public void handleServer(ServerPlayer sender) {
        if (sender != null) {
            MedicalActionService.startResuscitation(sender, targetEntityId);
        }
    }
}
