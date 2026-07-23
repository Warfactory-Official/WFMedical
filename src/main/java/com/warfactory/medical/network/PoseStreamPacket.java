package com.warfactory.medical.network;

import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.damage.HitAuthority;
import com.warfactory.medical.core.damage.rig.HumanoidRig;
import com.warfactory.medical.core.damage.rig.Obb;
import com.warfactory.medical.core.damage.rig.RigCache;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * POSE stream, client -> server, for {@link HitAuthority#CLIENT_HINT}. The victim's client sends its own
 * posed {@link HumanoidRig.LocalRig} (six entity-local OBBs) so the server can skip the costly rebuild and
 * classify the hit against the supplied pose.
 *
 * <p>The six boxes travel in the fixed {@link HumanoidRig.LocalRig.Slot} order and carry <b>no</b> limb tag
 * &mdash; the server assigns each slot's {@link LimbType} itself, so a tampered client cannot mislabel which
 * box is which. The centre/axes/half it sends are still bounded by {@link RigCache}'s validation, and the
 * ray test remains the server's: the victim only vouches for its own pose, never the hit outcome.</p>
 */
public record PoseStreamPacket(HumanoidRig.LocalRig rig) {

    public static PoseStreamPacket decode(FriendlyByteBuf buf) {
        HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            slot.set(rig, readObb(buf, slot.limb));
        }
        return new PoseStreamPacket(rig);
    }

    public void encode(FriendlyByteBuf buf) {
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            writeObb(buf, slot.get(rig));
        }
    }

    private static void writeObb(FriendlyByteBuf buf, Obb o) {
        writeVec(buf, o.center());
        writeVec(buf, o.axisX());
        writeVec(buf, o.axisY());
        writeVec(buf, o.axisZ());
        writeVec(buf, o.half());
    }

    private static Obb readObb(FriendlyByteBuf buf, LimbType limb) {
        Vec3 center = readVec(buf);
        Vec3 axisX = readVec(buf);
        Vec3 axisY = readVec(buf);
        Vec3 axisZ = readVec(buf);
        Vec3 half = readVec(buf);
        return new Obb(center, axisX, axisY, axisZ, half, limb);
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeFloat((float) v.x);
        buf.writeFloat((float) v.y);
        buf.writeFloat((float) v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    /**
     * Server-thread handler: record the sender's own streamed pose, but only while the server actually wants
     * hints (ignore any unsolicited pose when neither CLIENT_HINT authority nor animatedHitboxes is on).
     */
    public void handleServer(ServerPlayer sender) {
        if (sender == null || !MedicalConfig.useClientPose()) {
            return;
        }
        RigCache.submitHint(sender.getId(), rig, sender.level().getGameTime());
    }
}
