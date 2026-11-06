package com.warfactory.medical.ragdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.sarocesch.sarosragdoll.ragdoll.physics.EnumRagdollBodyPart;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MedicalRagdollRendererTest {
    @Test
    void nativePartPoseMatchesSaroPhysicsForArmorAndHeldItems() {
        PlayerModel<LivingEntity> model = new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE, false)
                .getRoot().bake(64, 64), false);
        ModelPart[] bones = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        float[][] offsets = {{0, .25F, 0}, {0, -.375F, 0}, {.369F, -.375F, 0}, {-.369F, -.375F, 0},
                {.125F, -1.125F, 0}, {-.1F, -1.125F, 0}};
        for (Quaternionf rotation : new Quaternionf[]{new Quaternionf(), new Quaternionf().rotateXYZ(.7F, -.4F, 2.1F),
                new Quaternionf().rotateXYZ(1.4F, 1.5706F, -.8F),
                new Quaternionf().rotationZYX(.3F, (float) Math.PI / 2, .7F),
                new Quaternionf().rotationZYX(.3F, (float) -Math.PI / 2, .7F)}) {
            Quaternionf flipped = new Quaternionf(rotation).rotateX((float) Math.PI);
            for (int i = 0; i < bones.length; i++) {
                ModelPart bone = bones[i];
                PartPose rest = bone.getInitialPose();
                Matrix4f expected = new Matrix4f().translation(1, -2, 3).rotate(flipped)
                        .translate(offsets[i][0], offsets[i][1], offsets[i][2])
                        .translate(rest.x / 16, rest.y / 16, rest.z / 16);
                MedicalRagdollRenderer.poseBone(bone, EnumRagdollBodyPart.values()[i], flipped,
                        1, -2, 3, new Vector3f());
                PoseStack actual = new PoseStack();
                bone.translateAndRotate(actual);
                for (int c = 0; c < 4; c++) {
                    for (int r = 0; r < 4; r++) {
                        assertEquals(expected.get(c, r), actual.last().pose().get(c, r), .0005F,
                                EnumRagdollBodyPart.values()[i] + " matrix " + c + "," + r);
                    }
                }
            }
        }
    }
}
