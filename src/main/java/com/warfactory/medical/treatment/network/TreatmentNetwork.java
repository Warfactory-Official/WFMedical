package com.warfactory.medical.treatment.network;

import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.attachment.IMedicalData;
import com.warfactory.medical.body.attachment.MedicalAttachments;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.treatment.MedicalPoses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TreatmentNetwork {

    private TreatmentNetwork() {
    }

    public static int tourniquetMask(MedicalProfile profile) {
        int mask = 0;
        for (LimbType lt : LimbType.VALUES) {
            if (profile.limb(lt).hasTourniquet()) {
                mask |= (1 << lt.ordinal());
            }
        }
        return mask;
    }

    public static void broadcastTourniquets(LivingEntity entity, MedicalProfile profile) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
                new TourniquetStatePacket(entity.getId(), tourniquetMask(profile)));
    }

    public static void sendTourniquetsTo(ServerPlayer viewer, int entityId, int mask) {
        PacketDistributor.sendToPlayer(viewer, new TourniquetStatePacket(entityId, mask));
    }

    /** Also the actor's third-person pose ({@link MedicalPoses}) to it and its trackers. */
    public static void sendActiveTreatment(ServerPlayer player, ActiveTreatmentPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
        IMedicalData data = MedicalAttachments.get(player);
        MedicalPoses.Kind k = packet.active() && data != null
                ? MedicalPoses.kind(data.getProfile().getActiveItemId()) : MedicalPoses.Kind.NONE;
        MedicalPoses.server(player, k);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TreatmentPosePacket(player.getId(), k));
    }

    public static void sendTreatmentPoseTo(ServerPlayer viewer, ServerPlayer target) {
        MedicalPoses.Kind k = MedicalPoses.server(target);
        if (k != MedicalPoses.Kind.NONE) {
            PacketDistributor.sendToPlayer(viewer, new TreatmentPosePacket(target.getId(), k));
        }
    }

    public static void sendTargetInfo(ServerPlayer medic, TreatmentTargetInfoPacket packet) {
        PacketDistributor.sendToPlayer(medic, packet);
    }

    public static void sendTargetSheet(ServerPlayer medic, TargetSheetInfoPacket packet) {
        PacketDistributor.sendToPlayer(medic, packet);
    }

}
