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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.UUID;

/**
 * Deterministic GameTests for the Tier 2 rigged-limb-box classifier. Uses {@link FakePlayer} (not
 * ArmorStand) because the {@code victim instanceof Player} gate in classifyRay must be satisfied for Tier 2
 * to engage; an ArmorStand silently falls back to Tier 1 banding. All rotation fields pinned to 0
 * (yBodyRot=0 → victim front=+Z, right=-X). Rig lives in entity-local space (feet origin, X=right, Z=front);
 * with yBodyRot=0 the world↔local map is worldX=feetX-localX, worldY=feetY+localY, worldZ=feetZ+localZ.
 */
@GameTestHolder(WFMedical.MOD_ID)
public class LimbRigGameTest {

    /**
     * Structure template shared by every test (the bundled 3x3 stone floor); resolves {@code wfmedical:empty}.
     */
    private static final String TEMPLATE = "empty";

    // ============================================================================================
    // Victim setup
    // ============================================================================================

    /**
     * All rotation fields pinned to 0; never added to the world so it never ticks or moves.
     */
    private static FakePlayer newPlayer(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "wfmed_rig");
        FakePlayer p = new FakePlayer(helper.getLevel(), profile);
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        p.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
        // Pin the whole rotation frame the pose pipeline consults so the rig is fully determined.
        p.setYRot(0.0F);
        p.setXRot(0.0F);
        p.setYHeadRot(0.0F);
        p.yBodyRot = 0.0F;
        p.yBodyRotO = 0.0F;
        p.yHeadRot = 0.0F;
        p.yHeadRotO = 0.0F;
        // Zero the animation clock so the idle arm-bob term is deterministic.
        p.tickCount = 0;
        p.setPose(Pose.STANDING);
        return p;
    }

    /**
     * A gravity-free {@link ArmorStand} facing +Z with {@code yBodyRot = 0}; a non-player banding victim.
     */
    private static ArmorStand newStand(GameTestHelper helper) {
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
        stand.setNoGravity(true);
        stand.setYRot(0.0F);
        stand.yBodyRot = 0.0F;
        stand.yBodyRotO = 0.0F;
        return stand;
    }

    // ============================================================================================
    // Ray helpers (author in entity-local space, round-trip to world)
    // ============================================================================================

    /**
     * Entity-local (feet origin, X = right, Z = front) -> world, valid because {@code yBodyRot = 0}.
     */
    private static Vec3 localToWorld(LivingEntity v, double lx, double ly, double lz) {
        Vec3 feet = v.position();
        return new Vec3(feet.x - lx, feet.y + ly, feet.z + lz);
    }

    /**
     * A ray that pierces the victim from the front ({@code +Z}) through the local point {@code (lx, ly)}.
     */
    private static void expectFrontal(GameTestHelper h, LivingEntity v, double lx, double ly, LimbType want) {
        expectRay(h, v, localToWorld(v, lx, ly, 2.0), localToWorld(v, lx, ly, -2.0), want);
    }

    private static void expectRay(GameTestHelper h, LivingEntity v, Vec3 from, Vec3 to, LimbType want) {
        LimbType got = HitGeometry.classifyRay(v, from, to);
        h.assertTrue(got == want, "expected " + want + " but got " + got);
    }

    // ============================================================================================
    // (a) Neutral standing: frontal head / torso / leg
    // ============================================================================================

    /**
     * Centred frontal ray high on the rig enters the head OBB.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void neutralFrontalHead(GameTestHelper helper) {
        FakePlayer v = newPlayer(helper);
        expectFrontal(helper, v, 0.0, 1.62, LimbType.HEAD);
        helper.succeed();
    }

    /**
     * Centred frontal ray at chest height enters the torso OBB (arms hang outside, at |x| ~ 0.375).
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void neutralFrontalTorso(GameTestHelper helper) {
        FakePlayer v = newPlayer(helper);
        expectFrontal(helper, v, 0.0, 0.90, LimbType.TORSO);
        helper.succeed();
    }

    /**
     * A low frontal ray offset onto the victim's right leg (local {@code +X}) enters the right-leg OBB.
     * The offset avoids the exact centre, where the two leg OBBs meet and the tie would resolve by
     * iteration order rather than geometry.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void neutralFrontalRightLeg(GameTestHelper helper) {
        FakePlayer v = newPlayer(helper);
        expectFrontal(helper, v, 0.113, 0.35, LimbType.RIGHT_LEG);
        helper.succeed();
    }

    // ============================================================================================
    // (b) Side-on: near-face arm
    // ============================================================================================

    /**
     * A torso-height ray sweeping across the body from the victim's right (local {@code +X}) enters the
     * right-arm OBB first &mdash; the arm sits on the near face, exactly as a side-on shot would strike it.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void sideOnRightArm(GameTestHelper helper) {
        FakePlayer v = newPlayer(helper);
        // Local +X (victim's right) is world -X, so this ray travels world +X into the near arm.
        expectRay(helper, v,
                localToWorld(v, 2.0, 1.06, 0.0),
                localToWorld(v, -2.0, 1.06, 0.0),
                LimbType.RIGHT_ARM);
        helper.succeed();
    }

    // ============================================================================================
    // (c) Aiming a bow: the drawing arm swings forward across the chest
    // ============================================================================================

    /**
     * Force the vanilla bow-draw pose (main-hand holding a bow, actively using it) and prove Tier 2 makes
     * the raised arm real geometry:
     * <ol>
     *   <li>the main-hand arm OBB centre moves <em>in front</em> of the torso (local {@code z > 0});</li>
     *   <li>a frontal upper-chest ray that reads TORSO on a neutral victim reads RIGHT_ARM on the aimer.</li>
     * </ol>
     * The A/B contrast uses the identical ray on two victims, so it isolates the pose change.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aimingBowArmForwardOfChest(GameTestHelper helper) {
        FakePlayer neutral = newPlayer(helper);
        FakePlayer aiming = newPlayer(helper);
        aiming.setItemInHand(InteractionHand.MAIN_HAND, Items.BOW.getDefaultInstance());
        aiming.startUsingItem(InteractionHand.MAIN_HAND);

        // (1) Rig geometry: the drawing (main-hand) arm sits forward of the torso.
        HumanoidRig.LocalRig rig = HumanoidRig.compute(aiming);
        HumanoidArm main = aiming.getMainArm();
        Obb mainArm = (main == HumanoidArm.RIGHT) ? rig.rightArm : rig.leftArm;
        helper.assertTrue(mainArm.center().z > 0.0,
                "aiming main-hand arm OBB must be in front of the body (local z>0); got z=" + mainArm.center().z);
        helper.assertTrue(mainArm.center().z > rig.torso.center().z,
                "aiming main-hand arm OBB must sit forward of the torso OBB");

        // (2) The same frontal upper-chest ray: TORSO when neutral, RIGHT_ARM when the arm is raised.
        double lx = 0.20;   // inside the torso's half-width (~0.254), outside the hanging arm (~0.238)
        double ly = 1.29;   // upper chest, below the head band
        expectFrontal(helper, neutral, lx, ly, LimbType.TORSO);
        expectFrontal(helper, aiming, lx, ly, LimbType.RIGHT_ARM);
        helper.succeed();
    }

    // ============================================================================================
    // (d) Crouch: the head OBB drops
    // ============================================================================================

    /**
     * Crouching lowers the head pivot, so the rig's head OBB centre drops below its standing height.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void crouchDropsHead(GameTestHelper helper) {
        FakePlayer standing = newPlayer(helper);
        FakePlayer crouched = newPlayer(helper);
        crouched.setPose(Pose.CROUCHING);   // isCrouching() reads getPose() == CROUCHING

        double standHeadY = HumanoidRig.compute(standing).head.center().y;
        double crouchHeadY = HumanoidRig.compute(crouched).head.center().y;
        helper.assertTrue(crouchHeadY < standHeadY,
                "crouch head OBB must drop: crouched y=" + crouchHeadY + " vs standing y=" + standHeadY);
        helper.succeed();
    }

    // ============================================================================================
    // (e) Banding fallback still resolves without the rig
    // ============================================================================================

    /**
     * The Tier-1 banding fallback must still return a limb through {@link HitGeometry#classifyRay} without
     * throwing when the rig is not used. A non-player victim ({@link ArmorStand}) deterministically fails
     * the {@code victim instanceof Player} gate in {@code classifyRay} and so takes the <em>identical</em>
     * banded-AABB branch that is also taken when {@code riggedLimbBoxes = false} (the config default is
     * {@code true} in the test server and there is no public setter to flip it from a test &mdash; see the
     * note in the task return). A centred mid-height frontal ray bands to TORSO.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void bandingFallbackReturnsLimb(GameTestHelper helper) {
        ArmorStand v = newStand(helper);
        AABB box = v.getBoundingBox();
        double midY = box.getYsize() * 0.5;   // relY 0.5 -> torso band
        LimbType got = HitGeometry.classifyRay(v,
                localToWorld(v, 0.0, midY, 2.0),
                localToWorld(v, 0.0, midY, -2.0));
        helper.assertTrue(got != null, "banding fallback must return a non-null limb without throwing");
        helper.assertTrue(got == LimbType.TORSO, "centred mid frontal ray must band to TORSO; got " + got);
        helper.succeed();
    }
}
