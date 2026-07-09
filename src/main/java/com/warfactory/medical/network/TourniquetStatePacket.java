package com.warfactory.medical.network;

import com.warfactory.medical.client.ClientTourniquetTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * WORN-TOURNIQUET state broadcast, server -> client. Carries a player entity's per-limb tourniquet mask
 * (bit {@code 1 << LimbType.ordinal()}) so observers can render the worn tourniquet model on the correct
 * arm/leg. Fans out to {@link net.minecraftforge.network.PacketDistributor#TRACKING_ENTITY_AND_SELF} on each
 * apply/remove edge, and to a single late viewer on start-tracking catch-up &mdash; exactly like
 * {@link DownedStatePacket}. Pure presentation state; never mutates medical data.
 *
 * <p>Side-safety: {@link #handleClient()} isolates the client mutation behind {@link DistExecutor} so this
 * class never classloads the client-only {@code ClientTourniquetTracker} on a dedicated server.</p>
 */
public record TourniquetStatePacket(int entityId, int mask) {

    public static TourniquetStatePacket decode(FriendlyByteBuf buf) {
        return new TourniquetStatePacket(buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(mask);
    }

    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientTourniquetTracker.set(entityId, mask));
    }
}
