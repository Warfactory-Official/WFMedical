package com.warfactory.medical.ragdoll;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.warfactory.medical.mixin.ragdoll.RagdollAccess;
import de.sarocesch.sarosragdoll.ragdoll.RagdollEntity;
import de.sarocesch.sarosragdoll.ragdoll.physics.EnumRagdollBodyPart;
import de.sarocesch.sarosragdoll.ragdoll.physics.RagdollPhysics;
import de.sarocesch.sarosragdoll.ragdoll.physics.SynchronizedRigidBodyTransform;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class RagdollPhysicsState {
    private RagdollPhysicsState() {}

    public static ListTag save(RagdollEntity ragdoll) {
        ListTag result = new ListTag();
        RagdollPhysics physics = ((RagdollAccess) ragdoll).wfmedical$physics();
        if (physics == null || !physics.isInWorld()) return result;
        for (EnumRagdollBodyPart part : EnumRagdollBodyPart.values()) {
            PhysicsRigidBody body = physics.getBody(part);
            if (body == null) return new ListTag();
            Vector3f position = body.getPhysicsLocation(new Vector3f());
            Quaternion rotation = body.getPhysicsRotation(new Quaternion());
            CompoundTag tag = new CompoundTag();
            tag.putString("Part", part.name());
            tag.putDouble("X", position.x);
            tag.putDouble("Y", position.y);
            tag.putDouble("Z", position.z);
            tag.putFloat("QX", rotation.getX());
            tag.putFloat("QY", rotation.getY());
            tag.putFloat("QZ", rotation.getZ());
            tag.putFloat("QW", rotation.getW());
            result.add(tag);
        }
        return valid(result, ragdoll.getX(), ragdoll.getY(), ragdoll.getZ()) ? result : new ListTag();
    }

    /** False means the native bodies are not ready or the saved pose is invalid; no body is changed. */
    public static boolean restore(RagdollEntity ragdoll, ListTag saved) {
        RagdollPhysics physics = ((RagdollAccess) ragdoll).wfmedical$physics();
        if (physics == null || !physics.isInWorld()
                || !valid(saved, ragdoll.getX(), ragdoll.getY(), ragdoll.getZ())) return false;
        for (EnumRagdollBodyPart part : EnumRagdollBodyPart.values()) {
            if (physics.getBody(part) == null) return false;
        }
        for (Tag entry : saved) {
            CompoundTag tag = (CompoundTag) entry;
            EnumRagdollBodyPart part = EnumRagdollBodyPart.valueOf(tag.getString("Part"));
            PhysicsRigidBody body = physics.getBody(part);
            body.setPhysicsLocation(new Vector3f((float) tag.getDouble("X"), (float) tag.getDouble("Y"), (float) tag.getDouble("Z")));
            body.setPhysicsRotation(new Quaternion(tag.getFloat("QX"), tag.getFloat("QY"), tag.getFloat("QZ"), tag.getFloat("QW")).normalizeLocal());
            body.setLinearVelocity(Vector3f.ZERO);
            body.setAngularVelocity(Vector3f.ZERO);
            body.activate(true);
            SynchronizedRigidBodyTransform transform = ragdoll.getPartTransform(part);
            if (transform != null) {
                transform.getPhysicTransform().set(body);
                transform.getPrevTransform().set(body);
                transform.getTransform().set(body);
            }
        }
        return true;
    }

    static boolean valid(ListTag saved, double x, double y, double z) {
        if (saved.size() != EnumRagdollBodyPart.values().length
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        Set<String> parts = new HashSet<>();
        for (Tag entry : saved) {
            if (!(entry instanceof CompoundTag tag)) return false;
            String part = tag.getString("Part");
            try {
                EnumRagdollBodyPart.valueOf(part);
            } catch (IllegalArgumentException invalid) {
                return false;
            }
            if (!parts.add(part)) return false;
            for (String key : new String[]{"X", "Y", "Z", "QX", "QY", "QZ", "QW"}) {
                if (!tag.contains(key, Tag.TAG_ANY_NUMERIC) || !Double.isFinite(tag.getDouble(key))) return false;
            }
            double px = tag.getDouble("X"), py = tag.getDouble("Y"), pz = tag.getDouble("Z");
            if (Math.abs(px) > 30_000_000 || Math.abs(py) > 30_000_000 || Math.abs(pz) > 30_000_000
                    || (px - x) * (px - x) + (py - y) * (py - y) + (pz - z) * (pz - z) > 64) return false;
            double qx = tag.getDouble("QX"), qy = tag.getDouble("QY"), qz = tag.getDouble("QZ"), qw = tag.getDouble("QW");
            double norm = qx * qx + qy * qy + qz * qz + qw * qw;
            if (norm < 0.5 || norm > 2) return false;
        }
        return true;
    }
}
