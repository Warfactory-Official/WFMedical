package com.warfactory.medical.gametest;

import com.mojang.authlib.GameProfile;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.damage.HitGeometry;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Hit classification with the victim <em>rotated</em>.
 *
 * <p>Every pre-existing rig test stands the victim at yaw 0, which is the one orientation in which a
 * world-to-local rotation error is invisible: at yaw 0 the rotation is the identity, so a flipped sign,
 * a swapped sin/cos or a missing yaw term all still produce the right answer. The symptom in a real
 * game is the worst kind -- shots register on the wrong limb only for players who happen to be facing a
 * particular way, which reads as "hit detection is flaky" and is close to unchaseable by hand.
 *
 * <p>The rig is built in the victim's local frame (feet origin, +Z forward, +X to the victim's right)
 * and the incoming ray is rotated into it, so every assertion here is phrased in local terms and
 * projected out to world space through the victim's actual yaw. If the projection here and the
 * rotation inside HitGeometry ever disagree, these fail.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class RigYawGameTest {

    private static final String TEMPLATE = "empty";

    /** Yaws to sweep. Includes the axis-aligned quarters and two off-axis angles. */
    private static final float[] YAWS = {0.0F, 45.0F, 90.0F, 135.0F, 180.0F, -45.0F, -90.0F, 200.0F};

    private static final double HEAD_Y = 1.62;
    private static final double TORSO_Y = 0.90;
    private static final double LEG_Y = 0.35;
    private static final double ARM_Y = 1.06;

    private static FakePlayer playerAtYaw(GameTestHelper helper, float yaw) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "wfmed_yaw");
        FakePlayer p = new FakePlayer(helper.getLevel(), profile);
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        p.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, yaw, 0.0F);
        // The rig poses off the BODY rotation, so setting yRot alone would leave the body at 0 and make
        // the whole sweep a no-op that quietly passes.
        p.setYRot(yaw);
        p.setXRot(0.0F);
        p.setYHeadRot(yaw);
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;
        p.yHeadRot = yaw;
        p.yHeadRotO = yaw;
        p.tickCount = 0;
        p.setPose(Pose.STANDING);
        return p;
    }

    /** Minecraft yaw: 0 faces +Z, and yaw increases clockwise seen from above. */
    private static Vec3 forward(float yawDeg) {
        double y = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(y), 0.0, Math.cos(y));
    }

    /** The victim's own right-hand direction: forward x up, so (-cos, 0, -sin). At yaw 0 this is -X. */
    private static Vec3 right(float yawDeg) {
        double y = Math.toRadians(yawDeg);
        return new Vec3(-Math.cos(y), 0.0, -Math.sin(y));
    }

    /** Local (right, up, forward) offset from the victim's feet, projected into world space. */
    private static Vec3 local(LivingEntity v, float yaw, double r, double up, double fwd) {
        Vec3 feet = v.position();
        Vec3 rt = right(yaw);
        Vec3 fw = forward(yaw);
        return new Vec3(
                feet.x + rt.x * r + fw.x * fwd,
                feet.y + up,
                feet.z + rt.z * r + fw.z * fwd);
    }

    private static void expect(GameTestHelper h, LivingEntity v, float yaw,
                               double r, double up, double fromFwd, double toFwd, LimbType want) {
        Vec3 from = local(v, yaw, r, up, fromFwd);
        Vec3 to = local(v, yaw, r, up, toFwd);
        LimbType got = HitGeometry.classifyRay(v, from, to);
        h.assertTrue(got == want,
                "at yaw " + yaw + ", local(r=" + r + ",up=" + up + ") expected " + want + " but got " + got);
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void frontalHeadAtEveryYaw(GameTestHelper helper) {
        for (float yaw : YAWS) {
            FakePlayer v = playerAtYaw(helper, yaw);
            expect(helper, v, yaw, 0.0, HEAD_Y, 2.0, -2.0, LimbType.HEAD);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void frontalTorsoAtEveryYaw(GameTestHelper helper) {
        for (float yaw : YAWS) {
            FakePlayer v = playerAtYaw(helper, yaw);
            expect(helper, v, yaw, 0.0, TORSO_Y, 2.0, -2.0, LimbType.TORSO);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void shotFromBehindStillHitsTheHead(GameTestHelper helper) {
        // Same limb, opposite approach. A rotation error that happens to be a 180-degree flip survives
        // the frontal sweep above but not this one paired with it.
        for (float yaw : YAWS) {
            FakePlayer v = playerAtYaw(helper, yaw);
            expect(helper, v, yaw, 0.0, HEAD_Y, -2.0, 2.0, LimbType.HEAD);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void leftAndRightLegsAreNotMirroredAtAnyYaw(GameTestHelper helper) {
        // The mirror bug: a sign slip on the local X axis swaps left and right. It is invisible at yaw 0
        // if the test only ever checks one side, so both sides are checked at every yaw.
        for (float yaw : YAWS) {
            FakePlayer v = playerAtYaw(helper, yaw);
            expect(helper, v, yaw, 0.113, LEG_Y, 2.0, -2.0, LimbType.RIGHT_LEG);
            expect(helper, v, yaw, -0.113, LEG_Y, 2.0, -2.0, LimbType.LEFT_LEG);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void sideOnArmsHitTheNearArmAtEveryYaw(GameTestHelper helper) {
        // Fired along the victim's own left-right axis rather than front-back, so this exercises the
        // other half of the rotation independently of the forward projection.
        for (float yaw : YAWS) {
            FakePlayer v = playerAtYaw(helper, yaw);
            Vec3 feet = v.position();
            Vec3 rt = right(yaw);
            Vec3 fromRight = new Vec3(feet.x + rt.x * 2.0, feet.y + ARM_Y, feet.z + rt.z * 2.0);
            Vec3 toLeft = new Vec3(feet.x - rt.x * 2.0, feet.y + ARM_Y, feet.z - rt.z * 2.0);
            LimbType got = HitGeometry.classifyRay(v, fromRight, toLeft);
            helper.assertTrue(got == LimbType.RIGHT_ARM,
                    "at yaw " + yaw + " a ray entering from the victim's right must hit RIGHT_ARM, got " + got);

            LimbType mirrored = HitGeometry.classifyRay(v, toLeft, fromRight);
            helper.assertTrue(mirrored == LimbType.LEFT_ARM,
                    "at yaw " + yaw + " the same ray reversed must hit LEFT_ARM, got " + mirrored);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void classificationIsActuallyYawSensitive(GameTestHelper helper) {
        // Guards the whole sweep from passing vacuously -- if yaw were ignored end to end, every test
        // above would still pass, because the local frame and the projection would both be the identity.
        //
        // Note where the yaw actually enters: the rig OBBs are built in the victim's LOCAL frame and are
        // byte-identical at every yaw (their axes do not move), so asserting on the boxes proves nothing.
        // It is HitGeometry that rotates the incoming ray into that frame. So the honest check is that a
        // ray held FIXED in world space classifies differently as the victim turns underneath it.
        FakePlayer at0 = playerAtYaw(helper, 0.0F);
        FakePlayer at180 = playerAtYaw(helper, 180.0F);

        // Facing +Z (yaw 0), world +X is on the victim's left; turned around (yaw 180) it is their right.
        LimbType from0 = worldRayFromPlusX(at0);
        LimbType from180 = worldRayFromPlusX(at180);

        helper.assertTrue(from0 == LimbType.LEFT_ARM,
                "facing +Z, a ray arriving from world +X enters the victim's left; got " + from0);
        helper.assertTrue(from180 == LimbType.RIGHT_ARM,
                "turned 180 degrees, the same world ray enters the victim's right; got " + from180);
        helper.succeed();
    }

    /** One fixed world-space ray, arriving from world +X at arm height, aimed through the victim. */
    private static LimbType worldRayFromPlusX(LivingEntity v) {
        Vec3 feet = v.position();
        return HitGeometry.classifyRay(v,
                new Vec3(feet.x + 2.0, feet.y + ARM_Y, feet.z),
                new Vec3(feet.x - 2.0, feet.y + ARM_Y, feet.z));
    }
}
