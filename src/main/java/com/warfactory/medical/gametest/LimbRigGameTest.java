package com.warfactory.medical.gametest;

import com.mojang.authlib.GameProfile;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.damage.HitGeometry;
import com.warfactory.medical.core.damage.rig.HumanoidRig;
import com.warfactory.medical.core.damage.rig.Obb;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class LimbRigGameTest {

    private static final String TEMPLATE = "empty";


    private static FakePlayer newPlayer(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "wfmed_rig");
        FakePlayer p = new FakePlayer(helper.getLevel(), profile);
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        p.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
        p.setYRot(0.0F);
        p.setXRot(0.0F);
        p.setYHeadRot(0.0F);
        p.yBodyRot = 0.0F;
        p.yBodyRotO = 0.0F;
        p.yHeadRot = 0.0F;
        p.yHeadRotO = 0.0F;
        p.tickCount = 0;
        p.setPose(Pose.STANDING);
        return p;
    }

    private static ArmorStand newStand(GameTestHelper helper) {
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
        stand.setNoGravity(true);
        stand.setYRot(0.0F);
        stand.yBodyRot = 0.0F;
        stand.yBodyRotO = 0.0F;
        return stand;
    }


    private static Vec3 localToWorld(LivingEntity v, double lx, double ly, double lz) {
        Vec3 feet = v.position();
        return new Vec3(feet.x - lx, feet.y + ly, feet.z + lz);
    }

    private static void expectFrontal(GameTestHelper h, LivingEntity v, double lx, double ly, LimbType want) {
        expectRay(h, v, localToWorld(v, lx, ly, 2.0), localToWorld(v, lx, ly, -2.0), want);
    }

    private static void expectRay(GameTestHelper h, LivingEntity v, Vec3 from, Vec3 to, LimbType want) {
        LimbType got = HitGeometry.classifyRay(v, from, to);
        h.assertTrue(got == want, "expected " + want + " but got " + got);
    }


    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void neutralFrontalHead(GameTestHelper helper) {
        FakePlayer v = newPlayer(helper);
        expectFrontal(helper, v, 0.0, 1.62, LimbType.HEAD);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void neutralFrontalTorso(GameTestHelper helper) {
        FakePlayer v = newPlayer(helper);
        expectFrontal(helper, v, 0.0, 0.90, LimbType.TORSO);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void neutralFrontalRightLeg(GameTestHelper helper) {
        FakePlayer v = newPlayer(helper);
        expectFrontal(helper, v, 0.113, 0.35, LimbType.RIGHT_LEG);
        helper.succeed();
    }


    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void sideOnRightArm(GameTestHelper helper) {
        FakePlayer v = newPlayer(helper);
        expectRay(helper, v,
                localToWorld(v, 2.0, 1.06, 0.0),
                localToWorld(v, -2.0, 1.06, 0.0),
                LimbType.RIGHT_ARM);
        helper.succeed();
    }


    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aimingBowArmForwardOfChest(GameTestHelper helper) {
        FakePlayer neutral = newPlayer(helper);
        FakePlayer aiming = newPlayer(helper);
        aiming.setItemInHand(InteractionHand.MAIN_HAND, Items.BOW.getDefaultInstance());
        aiming.startUsingItem(InteractionHand.MAIN_HAND);

        HumanoidRig.LocalRig rig = HumanoidRig.compute(aiming);
        HumanoidArm main = aiming.getMainArm();
        Obb mainArm = (main == HumanoidArm.RIGHT) ? rig.rightArm : rig.leftArm;
        helper.assertTrue(mainArm.center().z > 0.0,
                "aiming main-hand arm OBB must be in front of the body (local z>0); got z=" + mainArm.center().z);
        helper.assertTrue(mainArm.center().z > rig.torso.center().z,
                "aiming main-hand arm OBB must sit forward of the torso OBB");

        double lx = 0.20;
        double ly = 1.29;
        expectFrontal(helper, neutral, lx, ly, LimbType.TORSO);
        expectFrontal(helper, aiming, lx, ly, LimbType.RIGHT_ARM);
        helper.succeed();
    }


    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void crouchDropsHead(GameTestHelper helper) {
        FakePlayer standing = newPlayer(helper);
        FakePlayer crouched = newPlayer(helper);
        crouched.setPose(Pose.CROUCHING);

        double standHeadY = HumanoidRig.compute(standing).head.center().y;
        double crouchHeadY = HumanoidRig.compute(crouched).head.center().y;
        helper.assertTrue(crouchHeadY < standHeadY,
                "crouch head OBB must drop: crouched y=" + crouchHeadY + " vs standing y=" + standHeadY);
        helper.succeed();
    }


    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void bandingFallbackReturnsLimb(GameTestHelper helper) {
        ArmorStand v = newStand(helper);
        AABB box = v.getBoundingBox();
        double midY = box.getYsize() * 0.5;
        LimbType got = HitGeometry.classifyRay(v,
                localToWorld(v, 0.0, midY, 2.0),
                localToWorld(v, 0.0, midY, -2.0));
        helper.assertTrue(got != null, "banding fallback must return a non-null limb without throwing");
        helper.assertTrue(got == LimbType.TORSO, "centred mid frontal ray must band to TORSO; got " + got);
        helper.succeed();
    }
}
