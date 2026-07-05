package com.warfactory.medical.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * DOWNED-STATE broadcast, server -> client. Tells every tracking client (and the subject itself) that a
 * given player entity has entered or left the "downed" state — passed out from either an opioid overdose
 * blackout or a bleeding-out knockdown (see {@link com.warfactory.medical.core.MedicalProfile#isDowned()}).
 *
 * <p>This is the ONLY channel by which an observer learns a <em>teammate</em> is downed: a player's own
 * {@link MedicalSyncPacket} carries blackout/state for the local HUD, but the downed body pose must render
 * on OTHER clients watching the downed player, so this packet fans out to
 * {@link net.minecraftforge.network.PacketDistributor#TRACKING_ENTITY_AND_SELF} on every edge and to a
 * single late observer (via {@link net.minecraftforge.network.PacketDistributor#PLAYER}) on start-tracking
 * catch-up. It is pure presentation state and never mutates any medical data.</p>
 *
 * <p>Side-safety: {@link #handleClient()} isolates the client mutation behind {@link DistExecutor} so this
 * class never classloads the client-only {@code ClientDownedTracker} on a dedicated server.</p>
 */
public final class DownedStatePacket {

    private final int entityId;
    private final boolean downed;

    public DownedStatePacket(int entityId, boolean downed) {
        this.entityId = entityId;
        this.downed = downed;
    }

    /** The network id of the player entity whose downed state changed. */
    public int entityId() {
        return entityId;
    }

    /** {@code true} if that entity is now downed (blacked out or knocked down), {@code false} otherwise. */
    public boolean downed() {
        return downed;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(downed);
    }

    public static DownedStatePacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean downed = buf.readBoolean();
        return new DownedStatePacket(entityId, downed);
    }

    /**
     * Client-thread handler: record the per-entity downed flag in the client-only tracker. The lookup is
     * isolated behind {@link DistExecutor#unsafeRunWhenOn} so the reference to the client-only
     * {@code ClientDownedTracker} is never classloaded on a dedicated server.
     */
    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.warfactory.medical.client.ClientDownedTracker.set(entityId, downed));
    }
}
