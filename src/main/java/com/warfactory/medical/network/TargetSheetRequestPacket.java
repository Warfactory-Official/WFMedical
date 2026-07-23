package com.warfactory.medical.network;

import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * REQUEST for a full medical snapshot of another entity so the medic can open the EXAMINATION / TREATMENT
 * sheet <em>for that entity</em>, client -&gt; server. Sent when a medic presses the open-sheet key while
 * aiming at another player / downed body (the self case opens the sheet directly from the local cache and
 * never sends this). The medic's client does not have the target's medical state, so it asks the server,
 * which validates hands + reach and replies with a {@link TargetSheetInfoPacket} carrying the target's full
 * {@link MedicalSyncPacket} snapshot. Pure request; it never mutates state.
 *
 * <p>Distinct from {@link TreatmentTargetRequestPacket}: that one is item-specific (it carries an item id and
 * returns a per-limb treatable mask for the limb wheel), whereas the sheet needs the target's <em>entire</em>
 * physiology snapshot (all limbs + vitals) to drive the examination grid, overview column and every item
 * button, independent of which item the medic ends up choosing.</p>
 */
public record TargetSheetRequestPacket(int targetEntityId) {

    public static TargetSheetRequestPacket decode(FriendlyByteBuf buf) {
        return new TargetSheetRequestPacket(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
    }

    public void handleServer(ServerPlayer sender) {
        if (sender != null) {
            MedicalActionService.requestTargetSheet(sender, targetEntityId);
        }
    }
}
