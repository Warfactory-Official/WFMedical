package com.warfactory.medical.ragdoll;

import de.sarocesch.sarosragdoll.ragdoll.physics.EnumRagdollBodyPart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagdollPhysicsStateTest {
    private static ListTag pose() {
        ListTag pose = new ListTag();
        for (EnumRagdollBodyPart part : EnumRagdollBodyPart.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Part", part.name());
            tag.putDouble("X", 10);
            tag.putDouble("Y", -59);
            tag.putDouble("Z", 12);
            tag.putFloat("QX", 0);
            tag.putFloat("QY", 0);
            tag.putFloat("QZ", 0);
            tag.putFloat("QW", 1);
            pose.add(tag);
        }
        return pose;
    }

    @Test void acceptsCompletePoseNearItsSavedEntity() {
        assertTrue(RagdollPhysicsState.valid(pose(), 10, -60, 12));
    }

    @Test void rejectsMissingOrDuplicateLimbs() {
        ListTag pose = pose();
        pose.removeLast();
        assertFalse(RagdollPhysicsState.valid(pose, 10, -60, 12));
        pose.add(pose.getCompound(0).copy());
        assertFalse(RagdollPhysicsState.valid(pose, 10, -60, 12));
    }

    @Test void rejectsNonFiniteOrRemotePositions() {
        ListTag pose = pose();
        pose.getCompound(0).putDouble("X", Double.NaN);
        assertFalse(RagdollPhysicsState.valid(pose, 10, -60, 12));
        pose.getCompound(0).putDouble("X", 100);
        assertFalse(RagdollPhysicsState.valid(pose, 10, -60, 12));
    }

    @Test void rejectsMissingDegenerateOrNonFiniteRotations() {
        ListTag pose = pose();
        pose.getCompound(0).remove("QW");
        assertFalse(RagdollPhysicsState.valid(pose, 10, -60, 12));
        pose.getCompound(0).putFloat("QW", 0);
        assertFalse(RagdollPhysicsState.valid(pose, 10, -60, 12));
        pose.getCompound(0).putFloat("QW", Float.POSITIVE_INFINITY);
        assertFalse(RagdollPhysicsState.valid(pose, 10, -60, 12));
    }
}
