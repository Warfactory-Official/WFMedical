package com.warfactory.medical.network;

import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * REQUEST to begin a timed treatment, client -> server. The client only ASKS; the server validates and
 * (if valid) starts the active treatment through {@link MedicalActionService#start}. Clients never mutate
 * medical state directly.
 */
public record MedicalActionPacket(ResourceLocation itemId, LimbType limb, int targetEntityId) {

    /**
     * Self-targeted convenience (e.g. the legacy G-key radial): {@code targetEntityId = -1} means "the actor".
     */
    public MedicalActionPacket(ResourceLocation itemId, LimbType limb) {
        this(itemId, limb, -1);
    }

    public static MedicalActionPacket decode(FriendlyByteBuf buf) {
        ResourceLocation itemId = buf.readResourceLocation();
        LimbType limb = buf.readBoolean() ? buf.readEnum(LimbType.class) : null;
        int targetEntityId = buf.readVarInt();
        return new MedicalActionPacket(itemId, limb, targetEntityId);
    }

    /**
     * Targeted limb (nullable = let the server auto-pick).
     */
    @Override
    public LimbType limb() {
        return limb;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(itemId);
        boolean hasLimb = limb != null;
        buf.writeBoolean(hasLimb);
        if (hasLimb) {
            buf.writeEnum(limb);
        }
        buf.writeVarInt(targetEntityId);
    }

    /**
     * Server-thread handler: validate the sender and delegate to the authoritative action service, which
     * resolves the target ({@code -1} = the sender themself) and validates reach before starting anything.
     */
    public void handleServer(ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        MedicalActionService.start(sender, itemId, limb, targetEntityId);
    }
}
