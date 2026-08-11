package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.TreatmentAction;
import net.minecraft.network.FriendlyByteBuf;

public record ActiveTreatmentPacket(boolean active, TreatmentAction action, LimbType limb, int totalTicks,
                                    long startGameTime, int targetEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ActiveTreatmentPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "active_treatment"));

    public static final StreamCodec<FriendlyByteBuf, ActiveTreatmentPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ActiveTreatmentPacket::encode, ActiveTreatmentPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static ActiveTreatmentPacket inactive() {
        return new ActiveTreatmentPacket(false, null, null, 0, 0L, -1);
    }

    public static ActiveTreatmentPacket decode(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        if (!active) {
            return inactive();
        }
        TreatmentAction action = buf.readEnum(TreatmentAction.class);
        LimbType limb = buf.readBoolean() ? buf.readEnum(LimbType.class) : null;
        int totalTicks = buf.readVarInt();
        long startGameTime = buf.readLong();
        int targetEntityId = buf.readVarInt();
        return new ActiveTreatmentPacket(true, action, limb, totalTicks, startGameTime, targetEntityId);
    }

    @Override
    public TreatmentAction action() {
        return action;
    }

    @Override
    public LimbType limb() {
        return limb;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        if (!active) {
            return;
        }
        buf.writeEnum(action);
        boolean hasLimb = limb != null;
        buf.writeBoolean(hasLimb);
        if (hasLimb) {
            buf.writeEnum(limb);
        }
        buf.writeVarInt(totalTicks);
        buf.writeLong(startGameTime);
        buf.writeVarInt(targetEntityId);
    }

    @Override
    public int targetEntityId() {
        return targetEntityId;
    }

    public void handleClient() {
        ClientMedicalCache.setActiveTreatment(this);
    }
}
