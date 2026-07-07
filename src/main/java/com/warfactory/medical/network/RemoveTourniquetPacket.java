package com.warfactory.medical.network;

import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * REQUEST to remove a tourniquet from a limb, client -> server. UI-driven (no item is involved); the server
 * validates the sender and delegates to {@link MedicalActionService#removeTourniquet}. Clients never mutate
 * medical state directly.
 */
public record RemoveTourniquetPacket(LimbType limb) {

    public static RemoveTourniquetPacket decode(FriendlyByteBuf buf) {
        return new RemoveTourniquetPacket(buf.readEnum(LimbType.class));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(limb);
    }

    /**
     * Server-thread handler: validate the sender and remove the tourniquet from the requested limb.
     */
    public void handleServer(ServerPlayer sender) {
        if (sender != null && limb != null) {
            MedicalActionService.removeTourniquet(sender, limb);
        }
    }
}
