package com.warfactory.medical.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * REPLY to a {@link TargetSheetRequestPacket}, server -&gt; client. Carries a treatment target's FULL medical
 * {@link MedicalSyncPacket} snapshot (all per-limb summaries + vitals) plus its worn-tourniquet mask, so the
 * requesting medic's client can open the EXAMINATION / TREATMENT sheet bound to that target
 * ({@link #targetEntityId}). Presentation only; every treatment the medic then applies still round-trips as an
 * authoritative {@link MedicalActionPacket} the server re-validates.
 */
public record TargetSheetInfoPacket(int targetEntityId, MedicalSyncPacket snapshot, int tourniquetMask) {

    public static TargetSheetInfoPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        MedicalSyncPacket snap = MedicalSyncPacket.decode(buf);
        int mask = buf.readVarInt();
        return new TargetSheetInfoPacket(id, snap, mask);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
        snapshot.encode(buf);
        buf.writeVarInt(tourniquetMask);
    }

    /**
     * Client-thread handler. Dist-guarded so the client-only interaction screen is never classloaded on a
     * dedicated server (this is a PLAY_TO_CLIENT packet; the server never invokes this method).
     */
    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.warfactory.medical.client.screen.MedInteractionScreen.onTargetSheetInfo(this));
    }
}
