package com.warfactory.medical.network;

import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * REQUEST to set the player's targeting hint, client -> server. Records the limb the player selected in
 * the UI so subsequent treatments (both the timed active-treatment flow and vanilla right-click use) bias
 * toward it. The hint is a transient, non-authoritative preference; it never mutates trauma.
 */
public record SetTargetLimbPacket(LimbType limb) {

    public static SetTargetLimbPacket decode(FriendlyByteBuf buf) {
        LimbType limb = buf.readBoolean() ? buf.readEnum(LimbType.class) : null;
        return new SetTargetLimbPacket(limb);
    }

    /**
     * The selected limb, or null to clear the preference.
     */
    @Override
    public LimbType limb() {
        return limb;
    }

    public void encode(FriendlyByteBuf buf) {
        boolean hasLimb = limb != null;
        buf.writeBoolean(hasLimb);
        if (hasLimb) {
            buf.writeEnum(limb);
        }
    }

    /**
     * Server-thread handler: store the targeting hint on the sender's profile.
     */
    public void handleServer(ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        IMedicalData data = MedicalCapabilities.get(sender);
        if (data == null) {
            return;
        }
        data.getProfile().setPreferredLimb(limb);
    }
}
