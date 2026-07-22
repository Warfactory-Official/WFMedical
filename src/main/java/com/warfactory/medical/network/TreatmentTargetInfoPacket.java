package com.warfactory.medical.network;

import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * REPLY to a {@link TreatmentTargetRequestPacket}, server -> client. Carries a treatment target's per-limb
 * summaries so the requesting medic's client can build the limb wheel for THAT target ({@code -1} = the
 * requester themself), plus a per-limb {@code treatableMask}
 * ({@code bit = 1 << LimbType.ordinal()}) of the limbs the requested item can actually affect, so the wheel
 * never offers a limb the treatment would silently no-op on. Presentation only; the authoritative apply
 * still happens later via {@link MedicalActionPacket}, which the server re-validates.
 */
public record TreatmentTargetInfoPacket(int targetEntityId, ResourceLocation itemId, LimbSummary[] limbs,
                                        int treatableMask) {

    public static TreatmentTargetInfoPacket decode(FriendlyByteBuf buf) {
        int targetEntityId = buf.readVarInt();
        ResourceLocation itemId = buf.readResourceLocation();
        int count = buf.readVarInt();
        LimbSummary[] limbs = new LimbSummary[count];
        for (int i = 0; i < count; i++) {
            limbs[i] = MedicalSyncPacket.readLimb(buf);
        }
        int treatableMask = buf.readVarInt();
        return new TreatmentTargetInfoPacket(targetEntityId, itemId, limbs, treatableMask);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
        buf.writeResourceLocation(itemId);
        buf.writeVarInt(limbs.length);
        for (LimbSummary s : limbs) {
            MedicalSyncPacket.writeLimb(buf, s);
        }
        buf.writeVarInt(treatableMask);
    }

    /**
     * Client-thread handler. Dist-guarded so the client-only interaction class is never classloaded on a
     * dedicated server (this is a PLAY_TO_CLIENT packet; the server never invokes this method).
     */
    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.warfactory.medical.client.TreatmentInteractions.onTargetInfo(this));
    }
}
