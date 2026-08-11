package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record RemoveTourniquetPacket(LimbType limb, int targetEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemoveTourniquetPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "remove_tourniquet"));

    public static final StreamCodec<FriendlyByteBuf, RemoveTourniquetPacket> STREAM_CODEC =
            CustomPacketPayload.codec(RemoveTourniquetPacket::encode, RemoveTourniquetPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public RemoveTourniquetPacket(LimbType limb) {
        this(limb, -1);
    }

    public static RemoveTourniquetPacket decode(FriendlyByteBuf buf) {
        LimbType limb = buf.readEnum(LimbType.class);
        int targetEntityId = buf.readVarInt();
        return new RemoveTourniquetPacket(limb, targetEntityId);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(limb);
        buf.writeVarInt(targetEntityId);
    }

    public void handleServer(ServerPlayer sender) {
        if (sender != null && limb != null) {
            MedicalActionService.removeTourniquet(sender, limb, targetEntityId);
        }
    }
}
