package com.warfactory.medical.network;

import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * REQUEST to interrupt the sender's own in-progress treatment, client -> server (the interaction menu's
 * cancel/interrupt button). No payload – it always targets the sender's active treatment. The server validates
 * and delegates to {@link MedicalActionService#cancel}; nothing is applied and no item is consumed.
 */
public record CancelTreatmentPacket() {

    public static CancelTreatmentPacket decode(FriendlyByteBuf buf) {
        return new CancelTreatmentPacket();
    }

    public void encode(FriendlyByteBuf buf) {
    }

    /**
     * Server-thread handler: cancel the sender's running treatment, if any.
     */
    public void handleServer(ServerPlayer sender) {
        if (sender != null) {
            MedicalActionService.cancel(sender, "interrupted");
        }
    }
}
