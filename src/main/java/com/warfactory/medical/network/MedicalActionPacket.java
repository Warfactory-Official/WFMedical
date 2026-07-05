package com.warfactory.medical.network;

import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * REQUEST to begin a timed treatment, client -> server. The client only ASKS; the server validates and
 * (if valid) starts the active treatment through {@link MedicalActionService#start}. Clients never mutate
 * medical state directly.
 */
public final class MedicalActionPacket {

    private final ResourceLocation itemId;
    private final LimbType limb;

    public MedicalActionPacket(ResourceLocation itemId, LimbType limb) {
        this.itemId = itemId;
        this.limb = limb;
    }

    public ResourceLocation itemId() {
        return itemId;
    }

    /** Targeted limb (nullable = let the server auto-pick). */
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
    }

    public static MedicalActionPacket decode(FriendlyByteBuf buf) {
        ResourceLocation itemId = buf.readResourceLocation();
        LimbType limb = buf.readBoolean() ? buf.readEnum(LimbType.class) : null;
        return new MedicalActionPacket(itemId, limb);
    }

    /** Server-thread handler: validate the sender and delegate to the authoritative action service. */
    public void handleServer(ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        MedicalActionService.start(sender, itemId, limb);
    }
}
