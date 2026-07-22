package com.warfactory.medical.network;

import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * REQUEST to remove a tourniquet from a limb, client -> server. UI-driven (no item is involved); the server
 * validates the sender and delegates to {@link MedicalActionService#removeTourniquet}. The tourniquet may sit
 * on the sender themself ({@code targetEntityId = -1}) or on another player / downed body within reach (the
 * medic flow); the server validates reach either way. Clients never mutate medical state directly.
 */
public record RemoveTourniquetPacket(LimbType limb, int targetEntityId) {

    /**
     * Self-targeted convenience (the interaction sheet's red remove button acts on the sender).
     */
    public RemoveTourniquetPacket(LimbType limb) {
        this(limb, -1);
    }

    public static RemoveTourniquetPacket decode(FriendlyByteBuf buf) {
        LimbType limb = buf.readEnum(LimbType.class);
        int targetEntityId = buf.readVarInt();
        return new RemoveTourniquetPacket(limb, targetEntityId);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(limb);
        buf.writeVarInt(targetEntityId);
    }

    /**
     * Server-thread handler: validate the sender and remove the tourniquet from the requested limb.
     */
    public void handleServer(ServerPlayer sender) {
        if (sender != null && limb != null) {
            MedicalActionService.removeTourniquet(sender, limb, targetEntityId);
        }
    }
}
