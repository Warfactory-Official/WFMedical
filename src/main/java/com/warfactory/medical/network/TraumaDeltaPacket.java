package com.warfactory.medical.network;

import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * DELTA medical update, server -> client. Describes a single incremental change to the trauma set so the
 * client does not need a full {@link MedicalSyncPacket} resent for every hit or treatment.
 *
 * <p>The client is not authoritative: it may simply flag its cache stale / refresh a HUD. The server
 * still owns the truth and periodically reconciles with a full snapshot.</p>
 */
public record TraumaDeltaPacket(Op op, LimbType limb, String traumaTypeId, float severity) {

    public TraumaDeltaPacket(Op op, LimbType limb, String traumaTypeId, float severity) {
        this.op = op;
        this.limb = limb;
        this.traumaTypeId = traumaTypeId == null ? "" : traumaTypeId;
        this.severity = severity;
    }

    public static TraumaDeltaPacket decode(FriendlyByteBuf buf) {
        return new TraumaDeltaPacket(
                buf.readEnum(Op.class),
                buf.readEnum(LimbType.class),
                buf.readUtf(),
                buf.readFloat());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(op);
        buf.writeEnum(limb);
        buf.writeUtf(traumaTypeId);
        buf.writeFloat(severity);
    }

    /**
     * Client-thread handler: mark the cached snapshot stale so consumers know a change occurred.
     */
    public void handleClient() {
        ClientMedicalCache.markStale();
    }

    /**
     * The kind of change this delta describes.
     */
    public enum Op {
        ADDED,
        REMOVED,
        CHANGED,
        TREATED
    }
}
