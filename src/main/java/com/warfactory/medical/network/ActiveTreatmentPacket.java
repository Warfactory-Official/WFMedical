package com.warfactory.medical.network;

import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.TreatmentAction;
import net.minecraft.network.FriendlyByteBuf;

/**
 * ACTIVE-TREATMENT state, server -> client. Tells the client that a timed treatment has started (or
 * stopped) so it can draw the action-progress overlay. The server is authoritative; this packet is pure
 * presentation state and never mutates any medical data.
 *
 * <p>When {@link #active()} is {@code false} the remaining fields are meaningless (the overlay hides).
 * The client computes progress from {@code startGameTime} and {@code totalTicks} against its own level
 * game time: {@code elapsed = clientGameTime - startGameTime; fraction = elapsed / totalTicks}.</p>
 */
public record ActiveTreatmentPacket(boolean active, TreatmentAction action, LimbType limb, int totalTicks,
                                    long startGameTime, int targetEntityId) {

    /**
     * Convenience "no active treatment" instance for cancellation / completion.
     */
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

    /**
     * The action being applied (only meaningful while {@link #active()}; may be null otherwise).
     */
    @Override
    public TreatmentAction action() {
        return action;
    }

    /**
     * The targeted limb (nullable even while active — an auto-pick treatment carries no limb).
     */
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
        // Limb may be null (auto-pick); guard it with a present flag.
        boolean hasLimb = limb != null;
        buf.writeBoolean(hasLimb);
        if (hasLimb) {
            buf.writeEnum(limb);
        }
        buf.writeVarInt(totalTicks);
        buf.writeLong(startGameTime);
        buf.writeVarInt(targetEntityId);
    }

    /**
     * Entity id of who is being treated ({@code -1} = the local player themself). Lets the overlay label a
     * treatment aimed at another player / a downed body.
     */
    @Override
    public int targetEntityId() {
        return targetEntityId;
    }

    /**
     * Client-thread handler: store the latest active-treatment state for the overlay.
     */
    public void handleClient() {
        ClientMedicalCache.setActiveTreatment(this);
    }
}
