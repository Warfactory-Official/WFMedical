package com.warfactory.medical.damage;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A hit's body reaction for player {@code entityId}, to its trackers and itself. {@code front}/{@code side}: unit
 * direction to the attacker in the victim's frame (front + = ahead, side + = its right); {@code strength} in [0, 1].
 */
public record HitReactionPacket(int entityId, Stagger.Zone zone, boolean stagger, float front, float side,
                                float strength) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HitReactionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "hit_reaction"));

    public static final StreamCodec<FriendlyByteBuf, HitReactionPacket> STREAM_CODEC =
            CustomPacketPayload.codec(HitReactionPacket::encode, HitReactionPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static HitReactionPacket decode(FriendlyByteBuf buf) {
        return new HitReactionPacket(buf.readVarInt(), buf.readEnum(Stagger.Zone.class), buf.readBoolean(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeEnum(zone);
        buf.writeBoolean(stagger);
        buf.writeFloat(front);
        buf.writeFloat(side);
        buf.writeFloat(strength);
    }
}
