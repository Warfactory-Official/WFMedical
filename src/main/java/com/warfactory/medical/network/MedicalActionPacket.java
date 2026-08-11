package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record MedicalActionPacket(ResourceLocation itemId, LimbType limb, int targetEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MedicalActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "medical_action"));

    public static final StreamCodec<FriendlyByteBuf, MedicalActionPacket> STREAM_CODEC =
            CustomPacketPayload.codec(MedicalActionPacket::encode, MedicalActionPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public MedicalActionPacket(ResourceLocation itemId, LimbType limb) {
        this(itemId, limb, -1);
    }

    public static MedicalActionPacket decode(FriendlyByteBuf buf) {
        ResourceLocation itemId = buf.readResourceLocation();
        LimbType limb = buf.readBoolean() ? buf.readEnum(LimbType.class) : null;
        int targetEntityId = buf.readVarInt();
        return new MedicalActionPacket(itemId, limb, targetEntityId);
    }

    @Override
    public LimbType limb() {
        return limb;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(itemId);
        boolean hasLimb = limb != null;
        buf.writeBoolean(hasLimb);
        if (hasLimb) {
            buf.writeEnum(limb);
        }
        buf.writeVarInt(targetEntityId);
    }

    public void handleServer(ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        MedicalActionService.start(sender, itemId, limb, targetEntityId);
    }
}
