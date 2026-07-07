package com.warfactory.medical.core.damage.rig;

import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.compat.TaczCompat;
import com.warfactory.medical.compat.tacz.TaczArmPose;
import com.warfactory.medical.compat.tacz.TaczGunState;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Server-side replica of vanilla {@code HumanoidModel.setupAnim} + {@code PlayerModel}/renderer arm-pose
 * selection, producing six oriented boxes. Absolute fucking pain in the ass to get to work. FUCK YOU NOTCH
 */
public final class HumanoidRig {

    // --- model<->world transform constants (Recon: LivingEntityRenderer + PlayerRenderer) -----------
    /**
     * Model-unit -> block.
     */
    private static final double UNIT = 1.0 / 16.0;
    /**
     * Player render scale ({@code PlayerRenderer#scale}).
     */
    private static final double MODEL_SCALE = 0.9375;
    /**
     * Vertical re-center (feet-drop + z-fight epsilon) from {@code LivingEntityRenderer#render}.
     */
    private static final double Y_SHIFT = 1.501;

    private static final double DEG2RAD = Math.PI / 180.0;
    private static final double LIMB_FREQ = 0.6662;

    /**
     * The fixed, upright base spec for each limb box (model units), the single source of truth for both the
     * runtime rig and the {@code /wfmedical hitbox export} dump. Indexed by {@link LimbType#ordinal()}; each
     * row is {@code {ox, oy, oz, sx, sy, sz, px, py, pz}} matching the leading {@code Part} ctor args
     * (offset, size, pivot). HEAD and the two arms additionally get {@code +yOff} on {@code oy} while
     * crouching — applied at {@link #part} time, NOT stored here. When {@link RigTuning#ACTIVE} the per-limb
     * {@link RigTuning} deltas are added on top; otherwise these values are used verbatim.
     */
    private static final double[][] BASE = base();

    private static double[][] base() {
        double[][] b = new double[LimbType.VALUES.length][];
        b[LimbType.HEAD.ordinal()] = new double[]{-4, -8, -4, 8, 8, 8, 0, 0, 0};
        b[LimbType.TORSO.ordinal()] = new double[]{-4, 0, -2, 8, 12, 4, 0, 0, 0};
        b[LimbType.LEFT_ARM.ordinal()] = new double[]{-1, -2, -2, 4, 12, 4, 5, 2, 0};
        b[LimbType.RIGHT_ARM.ordinal()] = new double[]{-3, -2, -2, 4, 12, 4, -5, 2, 0};
        b[LimbType.LEFT_LEG.ordinal()] = new double[]{-2, 0, -2, 4, 12, 4, 1.9, 12, 0};
        b[LimbType.RIGHT_LEG.ordinal()] = new double[]{-2, 0, -2, 4, 12, 4, -1.9, 12, 0};
        return b;
    }

    /**
     * The upright base spec for a limb ({@code {ox,oy,oz,sx,sy,sz,px,py,pz}}, model units). Returns a copy;
     * for the debug {@code hitbox show/export} commands. {@link #usesCrouchOffset} reports which limbs also
     * take {@code +yOff} on {@code oy} at pose time.
     */
    public static double[] baseSpec(LimbType limb) {
        return BASE[limb.ordinal()].clone();
    }

    /**
     * True for the limbs whose {@code oy} gains the crouch {@code yOff} at pose time (head + both arms).
     */
    public static boolean usesCrouchOffset(LimbType limb) {
        return limb == LimbType.HEAD || limb.isArm();
    }

    /**
     * Build a {@link Part} from its {@link #BASE} spec, adding the crouch {@code yOff} to {@code oy} and, only
     * when {@link RigTuning#ACTIVE}, the live {@link RigTuning} deltas. The {@code ACTIVE} branch is skipped
     * entirely in normal play, so this is a plain base-spec read with no tuning cost.
     */
    private static Part part(LimbType limb, double yOff) {
        int i = limb.ordinal();
        double[] s = BASE[i];
        double ox = s[0];
        double oy = s[1] + yOff;
        double oz = s[2];
        double sx = s[3];
        double sy = s[4];
        double sz = s[5];
        double px = s[6];
        double py = s[7];
        double pz = s[8];
        if (RigTuning.ACTIVE) {
            double[] d = RigTuning.deltas();
            int b = i * RigTuning.FIELDS;
            ox += d[b];
            oy += d[b + 1];
            oz += d[b + 2];
            sx += d[b + 3];
            sy += d[b + 4];
            sz += d[b + 5];
            px += d[b + 6];
            py += d[b + 7];
            pz += d[b + 8];
        }
        return new Part(ox, oy, oz, sx, sy, sz, px, py, pz, limb);
    }

    // --- DOWNED (unconscious) pose replica: mirror the CLIENT lay-down so the rig matches the rendered body ---
    // Kept in lock-step with DownedPlayerRenderer (the PoseStack transform) and HumanoidModelMixin (the sprawl).
    /**
     * Backward tip about world X to lay the model supine (DownedPlayerRenderer.LAY_DEGREES).
     */
    private static final double DOWNED_LAY_RAD = Math.toRadians(-90.0);
    /**
     * Small fixed yaw so the sprawl is not grid-aligned (DownedPlayerRenderer.STABLE_YAW).
     */
    private static final double DOWNED_STABLE_YAW_RAD = Math.toRadians(8.0);
    /**
     * Upward nudge (blocks) so the supine body rests on the surface (DownedPlayerRenderer.GROUND_LIFT).
     */
    private static final double DOWNED_GROUND_LIFT = 0.1;
    /**
     * Half-range (radians) of the per-limb seeded sprawl (HumanoidModelMixin.TILT_RANGE).
     */
    private static final float DOWNED_TILT_RANGE = 0.28F;

    private HumanoidRig() {
    }

    /**
     * Build the six posed OBBs for {@code victim}. Runs the vanilla pose pipeline then converts each part
     * to an entity-local {@link Obb}, padded by {@link MedicalConfig#limbBoxPadding()}.
     */
    public static LocalRig compute(LivingEntity victim) {

        Part body = part(LimbType.TORSO, 0.0);
        Part rightLeg = part(LimbType.RIGHT_LEG, 0.0);
        Part leftLeg = part(LimbType.LEFT_LEG, 0.0);

        //Crouching does heavily affect y position of arms and head
        double yOff = victim.isCrouching() ? 2.75 : 0;

        Part head = part(LimbType.HEAD, yOff);
        Part rightArm = part(LimbType.RIGHT_ARM, yOff);
        Part leftArm = part(LimbType.LEFT_ARM, yOff);

        setupAnim(victim, head, body, rightArm, leftArm, rightLeg, leftLeg);

        double pad = MedicalConfig.limbBoxPadding();
        LocalRig rig = new LocalRig();
        rig.head = toObb(head, pad);
        rig.torso = toObb(body, pad);
        rig.leftArm = toObb(leftArm, pad);
        rig.rightArm = toObb(rightArm, pad);
        rig.leftLeg = toObb(leftLeg, pad);
        rig.rightLeg = toObb(rightLeg, pad);
        // A downed player is laid supine by the client renderer (a world-space transform applied OUTSIDE the
        // model's own yaw); replicate that instead of the swim/elytra self-tilt. The two are mutually exclusive.
        if (victim instanceof Player p && MedicalState.isDowned(p)) {
            applyDownedLay(victim, rig);
        } else {
            applyPoseTilt(victim, rig);
        }
        return rig;
    }

    public static AABB worldBounds(LivingEntity victim) {
        LocalRig rig = compute(victim);
        Vec3 pos = victim.position();
        double yaw = Math.toRadians(victim.yBodyRot);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);   // front = (-sin, 0, cos)
        double rx = -fz;
        double rz = fx;              // right = (-front.z, 0, front.x)
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (Obb obb : rig.all()) {
            Vec3 c = obb.center();
            Vec3 ax = obb.axisX();
            Vec3 ay = obb.axisY();
            Vec3 az = obb.axisZ();
            double hx = obb.half().x;
            double hy = obb.half().y;
            double hz = obb.half().z;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        double lx = c.x + sx * hx * ax.x + sy * hy * ay.x + sz * hz * az.x;
                        double ly = c.y + sx * hx * ax.y + sy * hy * ay.y + sz * hz * az.y;
                        double lz = c.z + sx * hx * ax.z + sy * hy * ay.z + sz * hz * az.z;
                        double wx = pos.x + lx * rx + lz * fx;
                        double wy = pos.y + ly;
                        double wz = pos.z + lx * rz + lz * fz;
                        if (wx < minX) minX = wx;
                        if (wx > maxX) maxX = wx;
                        if (wy < minY) minY = wy;
                        if (wy > maxY) maxY = wy;
                        if (wz < minZ) minZ = wz;
                        if (wz > maxZ) maxZ = wz;
                    }
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Whole-body pose tilt that {@code HumanoidModel.setupAnim} does NOT apply &mdash; it lives in
     * {@code LivingEntityRenderer/PlayerRenderer.setupRotations}, which pitches the entire rig horizontal for
     * swimming / crawling / elytra flight. Replicated here so the six OBBs lie down with the model instead of
     * floating upright over a prone body. Applied AFTER {@link #toObb} (bones already posed, incl.
     * {@link #swimAnim}), rotating each box about the local +X (right) axis through the feet origin.
     *
     * <p><b>Frame note.</b> Vanilla applies {@code Axis.XP.rotationDegrees(f)} in the pose stack <i>before</i>
     * the {@code scale(-1,-1,1)} flip; that flip conjugates the rotation, so in this class's entity-local
     * frame (feet origin, Y-up, X-right, Z-front) the equivalent tilt is a rotation about {@code +X} by
     * {@code -f}. The visually-swimming {@code translate(0,-1,0.3)} maps to a Y/Z shift by the same
     * conjugation (the frame's Z is flipped). Spin-attack (riptide), sleeping and dying stay unmodelled.</p>
     */
    private static void applyPoseTilt(LivingEntity e, LocalRig rig) {
        double angle;              // radians about local +X (right), right-hand rule
        double shiftY = 0.0;
        double shiftZ = 0.0;
        float swimAmount = e.getSwimAmount(1.0F);
        if (e.isFallFlying()) {
            if (e.isAutoSpinAttack()) {
                return; // riptide spin -- not modelled; the sampler handles it
            }
            double ticks = e.getFallFlyingTicks();
            double t = Mth.clamp(ticks * ticks / 100.0, 0.0, 1.0);
            double tiltDeg = t * (-90.0 - e.getXRot());
            angle = -tiltDeg * DEG2RAD;
            // Elytra also yaws the body toward its movement vector (Axis.YP in setupRotations); omitted -- the
            // forward pitch dominates the silhouette, and the yaw offset only matters while strafing mid-glide.
        } else if (swimAmount > 0.0F) {
            double f2 = e.isInWater() ? (-90.0 - e.getXRot()) : -90.0;
            double f3 = swimAmount * f2;                 // == Mth.lerp(swimAmount, 0, f2)
            angle = -f3 * DEG2RAD;
            if (e.isVisuallySwimming()) {
                double phi = f3 * DEG2RAD;                // vanilla tilt angle (radians)
                double c = Math.cos(phi);
                double s = Math.sin(phi);
                shiftY = -c - 0.3 * s;                    // A^-1 . Rx(phi) . (0,-1,0.3)  -> local Y
                shiftZ = s - 0.3 * c;                     //                              -> local Z
            }
        } else {
            return; // upright -- no setupRotations tilt
        }
        if (angle == 0.0 && shiftY == 0.0 && shiftZ == 0.0) {
            return;
        }
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        rig.head = tilt(rig.head, cos, sin, shiftY, shiftZ);
        rig.torso = tilt(rig.torso, cos, sin, shiftY, shiftZ);
        rig.leftArm = tilt(rig.leftArm, cos, sin, shiftY, shiftZ);
        rig.rightArm = tilt(rig.rightArm, cos, sin, shiftY, shiftZ);
        rig.leftLeg = tilt(rig.leftLeg, cos, sin, shiftY, shiftZ);
        rig.rightLeg = tilt(rig.rightLeg, cos, sin, shiftY, shiftZ);
    }

    /**
     * Rotate an OBB about the local +X (right) axis through the feet origin, then shift its centre in Y/Z.
     */
    private static Obb tilt(Obb o, double cos, double sin, double shiftY, double shiftZ) {
        Vec3 c = rotX(o.center(), cos, sin);
        Vec3 centre = new Vec3(c.x, c.y + shiftY, c.z + shiftZ);
        return new Obb(centre, rotX(o.axisX(), cos, sin), rotX(o.axisY(), cos, sin), rotX(o.axisZ(), cos, sin),
                o.half(), o.limb());
    }

    /**
     * {@code (x, y, z)} rotated about +X by the given cos/sin: {@code (x, y·c - z·s, y·s + z·c)}.
     */
    private static Vec3 rotX(Vec3 v, double cos, double sin) {
        return new Vec3(v.x, v.y * cos - v.z * sin, v.y * sin + v.z * cos);
    }

    private static void downedSprawl(Player p, Part head, Part rightArm, Part leftArm,
                                     Part rightLeg, Part leftLeg) {
        Random rng = new Random(p.getId());
        head.xRot = downedTilt(rng) * 0.5F;
        head.yRot = downedTilt(rng) * 0.5F;
        head.zRot = downedTilt(rng);
        sprawlPart(rightArm, rng);
        sprawlPart(leftArm, rng);
        sprawlPart(rightLeg, rng);
        sprawlPart(leftLeg, rng);
    }

    private static void sprawlPart(Part part, Random rng) {
        part.xRot += downedTilt(rng);
        part.yRot += downedTilt(rng);
        part.zRot += downedTilt(rng);
    }

    private static float downedTilt(Random rng) {
        return (rng.nextFloat() * 2.0F - 1.0F) * DOWNED_TILT_RANGE;
    }

    private static void applyDownedLay(LivingEntity e, LocalRig rig) {
        double yaw = Math.toRadians(e.yBodyRot);
        double fX = -Math.sin(yaw);
        double fZ = Math.cos(yaw);   // front = (-sin, 0, cos)
        double rX = -fZ;
        double rZ = fX;              // right = (-front.z, 0, front.x)
        double cosA = Math.cos(DOWNED_STABLE_YAW_RAD);
        double sinA = Math.sin(DOWNED_STABLE_YAW_RAD);
        double cosL = Math.cos(DOWNED_LAY_RAD);
        double sinL = Math.sin(DOWNED_LAY_RAD);
        rig.head = layObb(rig.head, fX, fZ, rX, rZ, cosA, sinA, cosL, sinL);
        rig.torso = layObb(rig.torso, fX, fZ, rX, rZ, cosA, sinA, cosL, sinL);
        rig.leftArm = layObb(rig.leftArm, fX, fZ, rX, rZ, cosA, sinA, cosL, sinL);
        rig.rightArm = layObb(rig.rightArm, fX, fZ, rX, rZ, cosA, sinA, cosL, sinL);
        rig.leftLeg = layObb(rig.leftLeg, fX, fZ, rX, rZ, cosA, sinA, cosL, sinL);
        rig.rightLeg = layObb(rig.rightLeg, fX, fZ, rX, rZ, cosA, sinA, cosL, sinL);
    }

    private static Obb layObb(Obb o, double fX, double fZ, double rX, double rZ,
                              double cosA, double sinA, double cosL, double sinL) {
        Vec3 c = lay(o.center(), fX, fZ, rX, rZ, cosA, sinA, cosL, sinL, true);
        Vec3 ax = lay(o.axisX(), fX, fZ, rX, rZ, cosA, sinA, cosL, sinL, false);
        Vec3 ay = lay(o.axisY(), fX, fZ, rX, rZ, cosA, sinA, cosL, sinL, false);
        Vec3 az = lay(o.axisZ(), fX, fZ, rX, rZ, cosA, sinA, cosL, sinL, false);
        return new Obb(c, ax, ay, az, o.half(), o.limb());
    }

    private static Vec3 lay(Vec3 v, double fX, double fZ, double rX, double rZ,
                            double cosA, double sinA, double cosL, double sinL, boolean lift) {
        // local -> world offset: right=(rX,0,rZ), up=(0,1,0), front=(fX,0,fZ)
        double ox = v.x * rX + v.z * fX;
        double oy = v.y;
        double oz = v.x * rZ + v.z * fZ;
        // YP(stableYaw) about world Y
        double yx = ox * cosA + oz * sinA;
        double yz = -ox * sinA + oz * cosA;
        // XP(lay) about world X
        double xy = oy * cosL - yz * sinL;
        double xz = oy * sinL + yz * cosL;
        // world offset -> local
        double lx = yx * rX + xz * rZ;
        double ly = xy + (lift ? DOWNED_GROUND_LIFT : 0.0);
        double lz = yx * fX + xz * fZ;
        return new Vec3(lx, ly, lz);
    }

    // Vanilla HumanoidModel.setupAnim
    private static void setupAnim(LivingEntity e, Part head, Part body, Part rightArm, Part leftArm,
                                  Part rightLeg, Part leftLeg) {
        double limbSwing = e.walkAnimation.position();
        double limbSwingAmount = Math.min(e.walkAnimation.speed(), 1.0);
        double ageInTicks = e.tickCount;
        double netHeadYaw = Mth.wrapDegrees(e.getYHeadRot() - e.yBodyRot);
        double headPitch = e.getXRot();
        float swimAmount = e.getSwimAmount(1.0F);
        boolean fallFly = e.getFallFlyingTicks() > 4;
        boolean visualSwim = e.isVisuallySwimming();
        boolean crouching = e.isCrouching();
        boolean riding = e.isPassenger() && e.getVehicle() != null && e.getVehicle().shouldRiderSit();

        // ---- head yaw / pitch ----
        head.yRot = netHeadYaw * DEG2RAD;
        if (fallFly) {
            head.xRot = -Math.PI / 4.0;
        } else if (swimAmount > 0.0F) {
            if (visualSwim) {
                head.xRot = rotlerpRad(swimAmount, head.xRot, -Math.PI / 4.0);
            } else {
                head.xRot = rotlerpRad(swimAmount, head.xRot, headPitch * DEG2RAD);
            }
        } else {
            head.xRot = headPitch * DEG2RAD;
        }

        //body/arms
        body.yRot = 0.0;
        rightArm.z = 0.0;
        rightArm.x = -5.0;
        leftArm.z = 0.0;
        leftArm.x = 5.0;

        //elytra/fall-fly speed factor
        double f = 1.0;
        if (fallFly) {
            f = e.getDeltaMovement().lengthSqr();
            f /= 0.2;
            f *= f * f;
        }
        if (f < 1.0) {
            f = 1.0;
        }

        //limb-swing
        rightArm.xRot = Math.cos(limbSwing * LIMB_FREQ + Math.PI) * 2.0 * limbSwingAmount * 0.5 / f;
        leftArm.xRot = Math.cos(limbSwing * LIMB_FREQ) * 2.0 * limbSwingAmount * 0.5 / f;
        rightArm.zRot = 0.0;
        leftArm.zRot = 0.0;
        rightLeg.xRot = Math.cos(limbSwing * LIMB_FREQ) * 1.4 * limbSwingAmount / f;
        leftLeg.xRot = Math.cos(limbSwing * LIMB_FREQ + Math.PI) * 1.4 * limbSwingAmount / f;
        rightLeg.yRot = 0.005;
        leftLeg.yRot = -0.005;
        rightLeg.zRot = 0.005;
        leftLeg.zRot = -0.005;

        // riding
        if (riding) {
            rightArm.xRot += -Math.PI / 5.0;
            leftArm.xRot += -Math.PI / 5.0;
            rightLeg.xRot = -1.4137167;
            rightLeg.yRot = Math.PI / 10.0;
            rightLeg.zRot = 0.07853982;
            leftLeg.xRot = -1.4137167;
            leftLeg.yRot = -Math.PI / 10.0;
            leftLeg.zRot = -0.07853982;
        }

        rightArm.yRot = 0.0;
        leftArm.yRot = 0.0;

        //item / arm pose
        ArmPose mainPose = getArmPose(e, InteractionHand.MAIN_HAND);
        ArmPose offPose = getArmPose(e, InteractionHand.OFF_HAND);
        if (mainPose.twoHanded) {
            offPose = e.getOffhandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
        }
        HumanoidArm mainArm = e.getMainArm();
        ArmPose rightArmPose = mainArm == HumanoidArm.RIGHT ? mainPose : offPose;
        ArmPose leftArmPose = mainArm == HumanoidArm.RIGHT ? offPose : mainPose;

        boolean flag2 = mainArm == HumanoidArm.RIGHT;
        if (e.isUsingItem()) {
            boolean flag3 = e.getUsedItemHand() == InteractionHand.MAIN_HAND;
            if (flag3 == flag2) {
                poseRightArm(e, rightArmPose, head, rightArm, leftArm, crouching);
            } else {
                poseLeftArm(e, leftArmPose, head, rightArm, leftArm, crouching);
            }
        } else {
            boolean flag4 = flag2 ? leftArmPose.twoHanded : rightArmPose.twoHanded;
            if (flag2 != flag4) {
                poseLeftArm(e, leftArmPose, head, rightArm, leftArm, crouching);
                poseRightArm(e, rightArmPose, head, rightArm, leftArm, crouching);
            } else {
                poseRightArm(e, rightArmPose, head, rightArm, leftArm, crouching);
                poseLeftArm(e, leftArmPose, head, rightArm, leftArm, crouching);
            }
        }

        //attack swing
        setupAttackAnimation(e, head, body, rightArm, leftArm);

        //crouch / stand pivots
        if (crouching) {
            body.xRot = 0.5;
            rightArm.xRot += 0.4;
            leftArm.xRot += 0.4;
            rightLeg.z = 4.0;
            leftLeg.z = 4.0;
            rightLeg.y = 12.2;
            leftLeg.y = 12.2;
            head.y = 4.2;
            body.y = 3.2;
            leftArm.y = 5.2;
            rightArm.y = 5.2;
        } else {
            body.xRot = 0.0;
            rightLeg.z = 0.0;
            leftLeg.z = 0.0;
            rightLeg.y = 12.0;
            leftLeg.y = 12.0;
            head.y = 0.0;
            body.y = 0.0;
            leftArm.y = 2.0;
            rightArm.y = 2.0;
        }

        //idle arm bob (gated on !SPYGLASS)
        if (rightArmPose != ArmPose.SPYGLASS) {
            bobModelPart(rightArm, ageInTicks, 1.0);
        }
        if (leftArmPose != ArmPose.SPYGLASS) {
            bobModelPart(leftArm, ageInTicks, -1.0);
        }

        //swim / crawl
        if (swimAmount > 0.0F) {
            swimAnim(e, head, rightArm, leftArm, rightLeg, leftLeg, limbSwing, swimAmount);
        }

        // Downed sprawl: mirror HumanoidModelMixin's TAIL injection (seeded per-limb jitter + head reset) so the
        // rig's limbs match the rendered unconscious body. Runs on the freshly-posed parts, before toObb.
        if (e instanceof Player p && MedicalState.isDowned(p)) {
            downedSprawl(p, head, rightArm, leftArm, rightLeg, leftLeg);
        }
    }

    private static void poseRightArm(LivingEntity e, ArmPose pose, Part head, Part rightArm, Part leftArm,
                                     boolean crouching) {
        switch (pose) {
            case EMPTY -> rightArm.yRot = 0.0;
            case BLOCK -> {
                rightArm.xRot = rightArm.xRot * 0.5 - 0.9424779;
                rightArm.yRot = -Math.PI / 6.0;
            }
            case ITEM -> {
                rightArm.xRot = rightArm.xRot * 0.5 - Math.PI / 10.0;
                rightArm.yRot = 0.0;
            }
            case SPEAR -> {
                rightArm.xRot = rightArm.xRot * 0.5 - Math.PI;
                rightArm.yRot = 0.0;
            }
            case BOW -> bowPose(head, rightArm, leftArm, true);
            case GUN -> gunPose(e, head, rightArm, leftArm);
            case CROSSBOW_CHARGE -> animateCrossbowCharge(rightArm, leftArm, e, true);
            case CROSSBOW_HOLD -> animateCrossbowHold(rightArm, leftArm, head, true);
            case BRUSH -> {
                rightArm.xRot = rightArm.xRot * 0.5 - Math.PI / 5.0;
                rightArm.yRot = 0.0;
            }
            case SPYGLASS -> {
                rightArm.xRot = Mth.clamp(head.xRot - 1.9198622 - (crouching ? 0.2617994 : 0.0), -2.4, 3.3);
                rightArm.yRot = head.yRot - 0.2617994;
            }
            case TOOT_HORN -> {
                rightArm.xRot = Mth.clamp(head.xRot, -1.2, 1.2) - 1.4835298;
                rightArm.yRot = head.yRot - Math.PI / 6.0;
            }
        }
    }

    private static void poseLeftArm(LivingEntity e, ArmPose pose, Part head, Part rightArm, Part leftArm,
                                    boolean crouching) {
        switch (pose) {
            case EMPTY -> leftArm.yRot = 0.0;
            case BLOCK -> {
                leftArm.xRot = leftArm.xRot * 0.5 - 0.9424779;
                leftArm.yRot = Math.PI / 6.0;
            }
            case ITEM -> {
                leftArm.xRot = leftArm.xRot * 0.5 - Math.PI / 10.0;
                leftArm.yRot = 0.0;
            }
            case SPEAR -> {
                leftArm.xRot = leftArm.xRot * 0.5 - Math.PI;
                leftArm.yRot = 0.0;
            }
            case BOW -> bowPose(head, rightArm, leftArm, false);
            case GUN -> gunPose(e, head, rightArm, leftArm);
            case CROSSBOW_CHARGE -> animateCrossbowCharge(rightArm, leftArm, e, false);
            case CROSSBOW_HOLD -> animateCrossbowHold(rightArm, leftArm, head, false);
            case BRUSH -> {
                leftArm.xRot = leftArm.xRot * 0.5 - Math.PI / 5.0;
                leftArm.yRot = 0.0;
            }
            case SPYGLASS -> {
                leftArm.xRot = Mth.clamp(head.xRot - 1.9198622 - (crouching ? 0.2617994 : 0.0), -2.4, 3.3);
                leftArm.yRot = head.yRot + 0.2617994;
            }
            case TOOT_HORN -> {
                leftArm.xRot = Mth.clamp(head.xRot, -1.2, 1.2) - 1.4835298;
                leftArm.yRot = head.yRot + Math.PI / 6.0;
            }
        }
    }

    /**
     * Vanilla BOW_AND_ARROW arm rotation (raised, forward). Also used verbatim for the GUN approximation:
     * TACZ has its own animations we cannot capture, so a two-handed gun aimer gets both arms mirrored into
     * this raised-forward pose &mdash; a deliberate approximation so gun-holders get forward arm OBBs.
     *
     * <p>Vanilla has two mirrored branches: {@code poseRightArm} (mainRight) puts the +0.4 rad yaw spread
     * on the left arm; {@code poseLeftArm} (!mainRight) puts the -0.4 rad spread on the right arm and leaves
     * the left arm centred. {@code mainRight} selects which branch is replicated.</p>
     */
    private static void bowPose(Part head, Part rightArm, Part leftArm, boolean mainRight) {
        if (mainRight) {
            rightArm.yRot = -0.1 + head.yRot;
            leftArm.yRot = 0.1 + head.yRot + 0.4;
        } else {
            rightArm.yRot = -0.1 + head.yRot - 0.4;
            leftArm.yRot = 0.1 + head.yRot;
        }
        rightArm.xRot = -Math.PI / 2.0 + head.xRot;
        leftArm.xRot = -Math.PI / 2.0 + head.xRot;
    }

    /**
     * TACZ gun hold. TACZ's real third-person arm posing is client-only Java, so this drives the baked
     * {@link TaczArmPose} (relaxed hold &rarr; ADS by the entity's SYNCED aiming progress, read server-side via
     * {@link TaczGunState}). Two-handed: both arms are set, with head-yaw tracking. Config-gated
     * ({@link MedicalConfig#taczArmPose()}); off falls back to the bow-like raised-forward approximation.
     */
    private static void gunPose(LivingEntity e, Part head, Part rightArm, Part leftArm) {
        if (!MedicalConfig.taczArmPose()) {
            bowPose(head, rightArm, leftArm, true);
            return;
        }
        TaczArmPose.Pose p = TaczArmPose.resolve(TaczGunState.aimingProgress(e));
        rightArm.xRot = p.rightX() + head.xRot;
        rightArm.yRot = p.rightY() + head.yRot;
        rightArm.zRot = p.rightZ();
        leftArm.xRot = p.leftX() + head.xRot;
        leftArm.yRot = p.leftY() + head.yRot;
        leftArm.zRot = p.leftZ();
    }

    private static void animateCrossbowHold(Part p1, Part p2, Part head, boolean right) {
        Part a = right ? p1 : p2;
        Part b = right ? p2 : p1;
        a.yRot = (right ? -0.3 : 0.3) + head.yRot;
        b.yRot = (right ? 0.6 : -0.6) + head.yRot;
        a.xRot = -Math.PI / 2.0 + head.xRot + 0.1;
        b.xRot = -1.5 + head.xRot;
    }

    private static void animateCrossbowCharge(Part p1, Part p2, LivingEntity e, boolean right) {
        Part a = right ? p1 : p2;
        Part b = right ? p2 : p1;
        a.yRot = right ? -0.8 : 0.8;
        a.xRot = -0.97079635;
        b.xRot = a.xRot;
        double f = CrossbowItem.getChargeDuration(e.getUseItem());
        double f1 = f <= 0.0 ? 0.0 : Mth.clamp((float) e.getTicksUsingItem(), 0.0F, (float) f);
        double f2 = f <= 0.0 ? 0.0 : f1 / f;
        b.yRot = Mth.lerp(f2, 0.4, 0.85) * (right ? 1 : -1);
        b.xRot = Mth.lerp(f2, b.xRot, -Math.PI / 2.0);
    }

    private static void setupAttackAnimation(LivingEntity e, Part head, Part body, Part rightArm, Part leftArm) {
        double attackTime = e.getAttackAnim(1.0F);
        if (attackTime <= 0.0) {
            return;
        }
        HumanoidArm arm = getAttackArm(e);
        Part modelpart = arm == HumanoidArm.RIGHT ? rightArm : leftArm;
        double f = attackTime;
        body.yRot = Math.sin(Math.sqrt(f) * (Math.PI * 2.0)) * 0.2;
        if (arm == HumanoidArm.LEFT) {
            body.yRot *= -1.0;
        }
        rightArm.z = Math.sin(body.yRot) * 5.0;
        rightArm.x = -Math.cos(body.yRot) * 5.0;
        leftArm.z = -Math.sin(body.yRot) * 5.0;
        leftArm.x = Math.cos(body.yRot) * 5.0;
        rightArm.yRot += body.yRot;
        leftArm.yRot += body.yRot;
        leftArm.xRot += body.yRot;
        f = 1.0 - attackTime;
        f *= f;
        f *= f;
        f = 1.0 - f;
        double f1 = Math.sin(f * Math.PI);
        double f2 = Math.sin(attackTime * Math.PI) * -(head.xRot - 0.7) * 0.75;
        modelpart.xRot -= f1 * 1.2 + f2;
        modelpart.yRot += body.yRot * 2.0;
        modelpart.zRot += Math.sin(attackTime * Math.PI) * -0.4;
    }

    // Swim/crawl arm & leg blend
    private static void swimAnim(LivingEntity e, Part head, Part rightArm, Part leftArm, Part rightLeg,
                                 Part leftLeg, double limbSwing, float swimAmount) {
        double f5 = limbSwing % 26.0;
        HumanoidArm attackArm = getAttackArm(e);
        double attackTime = e.getAttackAnim(1.0F);
        double f1 = attackArm == HumanoidArm.RIGHT && attackTime > 0.0F ? 0.0 : swimAmount;
        double f2 = attackArm == HumanoidArm.LEFT && attackTime > 0.0F ? 0.0 : swimAmount;
        if (!e.isUsingItem()) {
            if (f5 < 14.0) {
                leftArm.xRot = rotlerpRad(f2, leftArm.xRot, 0.0);
                rightArm.xRot = Mth.lerp(f1, rightArm.xRot, 0.0);
                leftArm.yRot = rotlerpRad(f2, leftArm.yRot, Math.PI);
                rightArm.yRot = Mth.lerp(f1, rightArm.yRot, Math.PI);
                leftArm.zRot = rotlerpRad(f2, leftArm.zRot,
                        Math.PI + 1.8707964 * quadraticArmUpdate(f5) / quadraticArmUpdate(14.0));
                rightArm.zRot = Mth.lerp(f1, rightArm.zRot,
                        Math.PI - 1.8707964 * quadraticArmUpdate(f5) / quadraticArmUpdate(14.0));
            } else if (f5 < 22.0) {
                double f6 = (f5 - 14.0) / 8.0;
                leftArm.xRot = rotlerpRad(f2, leftArm.xRot, (Math.PI / 2.0) * f6);
                rightArm.xRot = Mth.lerp(f1, rightArm.xRot, (Math.PI / 2.0) * f6);
                leftArm.yRot = rotlerpRad(f2, leftArm.yRot, Math.PI);
                rightArm.yRot = Mth.lerp(f1, rightArm.yRot, Math.PI);
                leftArm.zRot = rotlerpRad(f2, leftArm.zRot, 5.012389 - 1.8707964 * f6);
                rightArm.zRot = Mth.lerp(f1, rightArm.zRot, 1.2707963 + 1.8707964 * f6);
            } else if (f5 < 26.0) {
                double f3 = (f5 - 22.0) / 4.0;
                leftArm.xRot = rotlerpRad(f2, leftArm.xRot, (Math.PI / 2.0) - (Math.PI / 2.0) * f3);
                rightArm.xRot = Mth.lerp(f1, rightArm.xRot, (Math.PI / 2.0) - (Math.PI / 2.0) * f3);
                leftArm.yRot = rotlerpRad(f2, leftArm.yRot, Math.PI);
                rightArm.yRot = Mth.lerp(f1, rightArm.yRot, Math.PI);
                leftArm.zRot = rotlerpRad(f2, leftArm.zRot, Math.PI);
                rightArm.zRot = Mth.lerp(f1, rightArm.zRot, Math.PI);
            }
        }
        leftLeg.xRot = Mth.lerp(swimAmount, leftLeg.xRot, 0.3 * Math.cos(limbSwing * 0.33333334 + Math.PI));
        rightLeg.xRot = Mth.lerp(swimAmount, rightLeg.xRot, 0.3 * Math.cos(limbSwing * 0.33333334));
    }

    private static void bobModelPart(Part part, double ageInTicks, double sign) {
        part.zRot += sign * (Math.cos(ageInTicks * 0.09) * 0.05 + 0.05);
        part.xRot += sign * Math.sin(ageInTicks * 0.067) * 0.05;
    }

    private static ArmPose getArmPose(LivingEntity e, InteractionHand hand) {
        ItemStack stack = e.getItemInHand(hand);
        if (stack.isEmpty()) {
            return ArmPose.EMPTY;
        }
        // TACZ approximation: a held gun forces the raised-forward two-handed pose (no vanilla use-anim).
        if (TaczCompat.isHeldGun(stack)) {
            return ArmPose.GUN;
        }
        if (e.getUsedItemHand() == hand && e.getUseItemRemainingTicks() > 0) {
            UseAnim anim = stack.getUseAnimation();
            switch (anim) {
                case BLOCK:
                    return ArmPose.BLOCK;
                case BOW:
                    return ArmPose.BOW;
                case SPEAR:
                    return ArmPose.SPEAR;
                case CROSSBOW:
                    if (hand == e.getUsedItemHand()) {
                        return ArmPose.CROSSBOW_CHARGE;
                    }
                    break;
                case SPYGLASS:
                    return ArmPose.SPYGLASS;
                case TOOT_HORN:
                    return ArmPose.TOOT_HORN;
                case BRUSH:
                    return ArmPose.BRUSH;
                default:
                    break;
            }
        } else if (!e.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return ArmPose.CROSSBOW_HOLD;
        }
        return ArmPose.ITEM;
    }

    private static HumanoidArm getAttackArm(LivingEntity e) {
        HumanoidArm main = e.getMainArm();
        return e.swingingArm == InteractionHand.MAIN_HAND ? main : main.getOpposite();
    }

    /**
     * {@code from + pct * wrap(to - from into [-PI,PI))} &mdash; vanilla {@code rotlerpRad}.
     */
    private static double rotlerpRad(double pct, double from, double to) {
        double delta = to - from;
        while (delta >= Math.PI) {
            delta -= 2.0 * Math.PI;
        }
        while (delta < -Math.PI) {
            delta += 2.0 * Math.PI;
        }
        return from + pct * delta;
    }

    // ================================================================================================
    // Arm-pose selection (PlayerRenderer#getArmPose replica; Forge client hook omitted)
    // ================================================================================================

    private static double quadraticArmUpdate(double x) {
        return -65.0 * x + x * x;
    }

    /**
     * Convert a posed part to an entity-local {@link Obb}. The cube centre and three half-axis endpoints
     * are taken in posed model space, mapped to the local frame, and the axis vectors recovered as
     * (endpoint - centre) &mdash; which bakes in the {@code MODEL_SCALE}. Each half-extent is padded.
     *
     * <p>The part's Euler {@code sin/cos} are computed ONCE here and threaded into {@link #posed} for all
     * four points; the previous per-point path recomputed all six every call (24 trig evals per part).</p>
     */
    private static Obb toObb(Part p, double pad) {
        double cx = p.ox + p.sx / 2.0;
        double cy = p.oy + p.sy / 2.0;
        double cz = p.oz + p.sz / 2.0;
        double hx = p.sx / 2.0;
        double hy = p.sy / 2.0;
        double hz = p.sz / 2.0;

        double rcx = Math.cos(p.xRot);
        double rsx = Math.sin(p.xRot);
        double rcy = Math.cos(p.yRot);
        double rsy = Math.sin(p.yRot);
        double rcz = Math.cos(p.zRot);
        double rsz = Math.sin(p.zRot);

        Vec3 centre = toLocalPoint(posed(p, cx, cy, cz, rcx, rsx, rcy, rsy, rcz, rsz));
        Vec3 vx = toLocalPoint(posed(p, cx + hx, cy, cz, rcx, rsx, rcy, rsy, rcz, rsz)).subtract(centre);
        Vec3 vy = toLocalPoint(posed(p, cx, cy + hy, cz, rcx, rsx, rcy, rsy, rcz, rsz)).subtract(centre);
        Vec3 vz = toLocalPoint(posed(p, cx, cy, cz + hz, rcx, rsx, rcy, rsy, rcz, rsz)).subtract(centre);

        Vec3 half = new Vec3(vx.length() + pad, vy.length() + pad, vz.length() + pad);
        return new Obb(centre, vx.normalize(), vy.normalize(), vz.normalize(), half, p.limb);
    }

    // ================================================================================================
    // Math helpers
    // ================================================================================================

    /**
     * A part-local point (model units) rotated by the part's Euler angles (precomputed {@code cos/sin}) and
     * offset by its pivot &mdash; {@code rotationZYX(zRot,yRot,xRot)}: X first, then Y, then Z, matching
     * {@code ModelPart.translateAndRotate}.
     */
    private static Vec3 posed(Part p, double x, double y, double z,
                              double cx, double sx, double cy, double sy, double cz, double sz) {
        // Rx
        double y1 = y * cx - z * sx;
        double z1 = y * sx + z * cx;
        // Ry
        double x2 = x * cy + z1 * sy;
        double z2 = -x * sy + z1 * cy;
        // Rz
        double x3 = x2 * cz - y1 * sz;
        double y3 = x2 * sz + y1 * cz;
        return new Vec3(p.x + x3, p.y + y3, p.z + z2);
    }

    /**
     * Map a posed model-space point (model units, Y-down, front -Z) into the entity-local frame
     * (feet origin, Y-up, X = right, Z = front). Derived by inverting the renderer's model->world chain
     * with the body yaw stripped (Recon).
     */
    private static Vec3 toLocalPoint(Vec3 model) {
        double x = -MODEL_SCALE * model.x * UNIT;
        double y = MODEL_SCALE * (Y_SHIFT - model.y * UNIT);
        double z = -MODEL_SCALE * model.z * UNIT;
        return new Vec3(x, y, z);
    }

    // ================================================================================================
    // Part -> entity-local OBB
    // ================================================================================================

    /**
     * Two-handed flags mirror the vanilla {@code HumanoidModel.ArmPose}; GUN is our TACZ approximation.
     */
    private enum ArmPose {
        EMPTY(false), ITEM(false), BLOCK(false), BOW(true), SPEAR(false),
        CROSSBOW_CHARGE(true), CROSSBOW_HOLD(true), SPYGLASS(false), TOOT_HORN(false),
        BRUSH(false), GUN(true);

        final boolean twoHanded;

        ArmPose(boolean twoHanded) {
            this.twoHanded = twoHanded;
        }
    }

    /**
     * The six posed OBBs in entity-local space.
     */
    public static final class LocalRig {
        public Obb head;
        public Obb torso;
        public Obb leftArm;
        public Obb rightArm;
        public Obb leftLeg;
        public Obb rightLeg;

        private Obb[] all;

        /**
         * The six OBBs as an array, built once and cached (callers read only; fields are set in compute).
         */
        public Obb[] all() {
            Obb[] a = all;
            if (a == null) {
                a = new Obb[]{head, torso, leftArm, rightArm, leftLeg, rightLeg};
                all = a;
            }
            return a;
        }
    }

    /**
     * A single posed humanoid part: cube extent (fixed) + pivot + Euler rotation (posed each call).
     */
    private static final class Part {
        final double ox, oy, oz, sx, sy, sz;
        final LimbType limb;
        double x, y, z;          // pivot, model units
        double xRot, yRot, zRot; // radians

        Part(double ox, double oy, double oz, double sx, double sy, double sz,
             double px, double py, double pz, LimbType limb) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.x = px;
            this.y = py;
            this.z = pz;
            this.limb = limb;
        }
    }
}
