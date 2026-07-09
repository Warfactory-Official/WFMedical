package com.warfactory.medical.network;

import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * REQUEST for a treatment target's per-limb summary, client -> server. Sent when a medic right-clicks ANOTHER
 * entity with a localized treatment: the medic's client does not have that target's medical state, so it asks
 * the server, which validates reach, gathers the target's {@link MedicalSyncPacket.LimbSummary}s and replies
 * with a {@link TreatmentTargetInfoPacket}. The client then opens the limb wheel (or auto-selects a single
 * damaged limb). Pure request; it never mutates state.
 */
public record TreatmentTargetRequestPacket(int targetEntityId, ResourceLocation itemId) {

    public static TreatmentTargetRequestPacket decode(FriendlyByteBuf buf) {
        int targetEntityId = buf.readVarInt();
        ResourceLocation itemId = buf.readResourceLocation();
        return new TreatmentTargetRequestPacket(targetEntityId, itemId);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
        buf.writeResourceLocation(itemId);
    }

    public void handleServer(ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        MedicalActionService.requestTargetInfo(sender, targetEntityId, itemId);
    }
}
