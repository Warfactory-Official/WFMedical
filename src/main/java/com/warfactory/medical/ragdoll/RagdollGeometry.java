package com.warfactory.medical.ragdoll;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import de.sarocesch.sarosragdoll.ragdoll.RagdollEntity;
import de.sarocesch.sarosragdoll.ragdoll.physics.EnumRagdollBodyPart;
import de.sarocesch.sarosragdoll.ragdoll.physics.SynchronizedRigidBodyTransform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

public final class RagdollGeometry {
    private RagdollGeometry() {}

    public static AABB bounds(RagdollEntity ragdoll) {
        List<AABB> parts = parts(ragdoll);
        AABB bounds = parts.getFirst();
        for (int i = 1; i < parts.size(); i++) bounds = bounds.minmax(parts.get(i));
        return bounds;
    }

    public static boolean canReach(Player player, RagdollEntity ragdoll) {
        if (!player.isAlive() || ragdoll.isRemoved() || player.level() != ragdoll.level()) return false;
        double range = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        if (!Double.isFinite(range) || range <= 0) return false;
        Vec3 eye = player.getEyePosition();
        for (AABB part : parts(ragdoll)) {
            if (!withinRange(part, eye, range)) continue;
            for (Vec3 point : new Vec3[]{nearest(part, eye), part.getCenter()}) {
                if (eye.distanceToSqr(point) <= range * range && player.level().clip(new ClipContext(
                        eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Vec3 recoveryPosition(ServerPlayer player, RagdollEntity ragdoll) {
        if (player.level() != ragdoll.level() || !finite(ragdoll.position())) return player.position();
        Vec3 chest = ragdoll.position();
        Vec3 best = player.position();
        double bestDistance = Double.POSITIVE_INFINITY;
        BlockPos origin = BlockPos.containing(chest);
        var dimensions = player.getDimensions(Pose.STANDING);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -3, -2), origin.offset(2, 2, 2))) {
            if (!player.level().hasChunkAt(pos)) continue;
            for (AABB surface : player.level().getBlockState(pos).getCollisionShape(player.level(), pos).toAabbs()) {
                double x = Mth.clamp(chest.x, pos.getX() + surface.minX + 0.0001, pos.getX() + surface.maxX - 0.0001);
                double z = Mth.clamp(chest.z, pos.getZ() + surface.minZ + 0.0001, pos.getZ() + surface.maxZ - 0.0001);
                Vec3 feet = new Vec3(x, pos.getY() + surface.maxY + 0.0001, z);
                if (Math.abs(feet.y - chest.y) > 2 || (x - chest.x) * (x - chest.x) + (z - chest.z) * (z - chest.z) > 4) continue;
                double distance = feet.distanceToSqr(chest);
                AABB standing = dimensions.makeBoundingBox(feet);
                if (distance < bestDistance && player.level().getWorldBorder().isWithinBounds(standing)
                        && player.level().noCollision(player, standing)) {
                    best = feet;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static List<AABB> parts(RagdollEntity ragdoll) {
        Vec3 chest = finite(ragdoll.position()) ? ragdoll.position() : Vec3.ZERO;
        List<AABB> parts = new ArrayList<>(6);
        for (EnumRagdollBodyPart part : EnumRagdollBodyPart.values()) {
            SynchronizedRigidBodyTransform transform = ragdoll.getPartTransform(part);
            if (transform == null) continue;
            Vector3f position = transform.getTransform().getPosition();
            Vec3 center = new Vec3(position.x, position.y, position.z);
            if (!finite(center) || center.distanceToSqr(chest) > 64) continue;
            Vector3f half = part.getBoxSize();
            Quaternion rotation = transform.getTransform().getRotation();
            AABB box = limbBounds(center, new Vec3(half.x, half.y, half.z),
                    new Quaternionf(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW()));
            if (box != null) parts.add(box);
        }
        if (parts.isEmpty()) parts.add(AABB.ofSize(chest, 1, 1, 1));
        return parts;
    }

    static AABB limbBounds(Vec3 center, Vec3 half, Quaternionf rotation) {
        if (!finite(center) || !finite(half) || half.x <= 0 || half.y <= 0 || half.z <= 0
                || Math.abs(center.x) > 30_000_000 || Math.abs(center.y) > 30_000_000 || Math.abs(center.z) > 30_000_000
                || !Float.isFinite(rotation.x) || !Float.isFinite(rotation.y) || !Float.isFinite(rotation.z) || !Float.isFinite(rotation.w)
                || rotation.lengthSquared() < 0.5 || rotation.lengthSquared() > 2) return null;
        Matrix3f matrix = new Matrix3f().rotation(new Quaternionf(rotation).normalize());
        double hx = Math.abs(matrix.m00()) * half.x + Math.abs(matrix.m10()) * half.y + Math.abs(matrix.m20()) * half.z;
        double hy = Math.abs(matrix.m01()) * half.x + Math.abs(matrix.m11()) * half.y + Math.abs(matrix.m21()) * half.z;
        double hz = Math.abs(matrix.m02()) * half.x + Math.abs(matrix.m12()) * half.y + Math.abs(matrix.m22()) * half.z;
        return new AABB(center.x - hx, center.y - hy, center.z - hz, center.x + hx, center.y + hy, center.z + hz);
    }

    static Vec3 nearest(AABB box, Vec3 point) {
        return new Vec3(Mth.clamp(point.x, box.minX, box.maxX), Mth.clamp(point.y, box.minY, box.maxY), Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    static boolean withinRange(AABB box, Vec3 eye, double range) {
        return Double.isFinite(range) && range >= 0 && finite(eye) && eye.distanceToSqr(nearest(box, eye)) <= range * range;
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
