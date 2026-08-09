package com.warfactory.medical.core.damage;

import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.compat.TaczCompat;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.damage.rig.HumanoidRig;
import com.warfactory.medical.core.damage.rig.Obb;
import com.warfactory.medical.core.damage.rig.RigCache;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public final class HitGeometry {


    private static final double HITSCAN_RANGE = 64.0;
    private static final double TRACE_MARGIN = 1.0;
    private static final double UPPER_ARM_LOW = 0.55;

    private HitGeometry() {
    }


    public static @Nullable LimbType classifyHit(LivingEntity victim, DamageSource src, DamageCategory cat) {
        boolean debug = MedicalConfig.logHitDetection();
        if (victim instanceof Player && MedicalConfig.riggedLimbBoxes() && rigUsable(victim)) {
            LimbType rigLimb = classifyRig(victim, src, cat);
            if (rigLimb != null) {
                if (debug) {
                    HitDetectionDebug.logClassifyHit(victim, src, cat, "RIG", rigLimb);
                }
                return rigLimb;
            }
        }
        Vec3 hit = resolveHitPoint(victim, src, cat);
        LimbType result = hit == null ? null : classifyLocal(victim, hit);
        if (debug) {
            HitDetectionDebug.logClassifyHit(victim, src, cat, hit == null ? "NO_HIT_POINT" : "BANDED_FALLBACK",
                    result);
        }
        return result;
    }

    public static List<LimbType> classifyHitPierced(LivingEntity victim, DamageSource src, DamageCategory cat) {
        if (victim instanceof Player && MedicalConfig.riggedLimbBoxes() && rigUsable(victim)) {
            Vec3[] seg = attackSegment(victim, src, cat);
            if (seg != null) {
                List<LimbType> pierced = rigRayPierce(victim, seg[0], seg[1]);
                if (!pierced.isEmpty()) {
                    return pierced;
                }
            }
        }
        LimbType single = classifyHit(victim, src, cat);
        return single == null ? List.of() : List.of(single);
    }

    private static @Nullable LimbType classifyRig(LivingEntity victim, DamageSource src, DamageCategory cat) {
        AABB box = victim.getBoundingBox();
        var direct = src.getDirectEntity();
        var attacker = src.getEntity();

        if (direct instanceof Projectile && direct != attacker) {
            Optional<Vec3[]> taczSeg = TaczCompat.bulletSegment(src);
            if (taczSeg.isPresent()) {
                Vec3[] seg = taczSeg.get();
                LimbType rayLimb = rigRayPick(victim, seg[0], seg[1]);
                if (rayLimb != null) {
                    return rayLimb;
                }
                // Ray missed every OBB outright (e.g. a grazing shot at the envelope's edge) -- fall back
                // to nearest-surface point-pick rather than discarding TACZ's own resolved hit entirely.
                Optional<Vec3> tacz = TaczCompat.bulletHitPos(src);
                if (tacz.isPresent()) {
                    return rigPointPick(victim, tacz.get(), src, "TACZ_PROJECTILE_FALLBACK");
                }
            }
            Vec3 to = direct.position();
            Vec3 from = new Vec3(direct.xo, direct.yo, direct.zo);
            Vec3 dir = to.subtract(from);
            if (dir.lengthSqr() < 1.0e-8) {
                dir = direct.getDeltaMovement();
            }
            if (dir.lengthSqr() < 1.0e-8) {
                dir = victim.position().subtract(to);
            }
            if (dir.lengthSqr() < 1.0e-8) {
                return rigPointPick(victim, nearestPointOnBox(box, to), src, "PROJECTILE_DEGENERATE");
            }
            Vec3 d = dir.normalize();
            return rigRayPick(victim, from.subtract(d.scale(TRACE_MARGIN)), to.add(d.scale(TRACE_MARGIN)));
        }

        if (attacker instanceof LivingEntity shooter && attacker != victim) {
            boolean ballistic = (cat == DamageCategory.BALLISTIC);
            boolean melee = (direct == attacker);
            if (ballistic || melee) {
                if (ballistic) {
                    Optional<Vec3> tacz = TaczCompat.bulletHitPos(src);
                    if (tacz.isPresent()) {
                        return rigPointPick(victim, tacz.get(), src, "TACZ_BALLISTIC");
                    }
                }
                Vec3 eye = shooter.getEyePosition();
                Vec3 look = shooter.getViewVector(1.0F);
                double range = ballistic ? HITSCAN_RANGE : MedicalConfig.meleeReach();
                return rigRayPick(victim, eye, eye.add(look.scale(range)));
            }
        }

        Vec3 srcPos = src.getSourcePosition();
        if (srcPos != null) {
            Vec3 centre = box.getCenter();
            Vec3 entry = box.clip(srcPos, centre).orElse(centre);
            return rigPointPick(victim, entry, src, "SRC_POS_FALLBACK");
        }

        return null;
    }


    public static boolean isGapShot(LivingEntity victim, DamageSource src, DamageCategory cat) {
        if (src == null || !(victim instanceof Player) && !MedicalHitReg.isEnvelopeTarget(victim)) {
            return false;
        }
        if (!rigPoseSupported(victim)) {
            return false;
        }
        Vec3[] seg = attackSegment(victim, src, cat);
        if (seg == null) {
            return false;
        }

        // A shot only "threads the gap" (and is thrown away) when its path clears EVERY limb box by more than
        // the tolerance. The tolerance matters because the hit classifier assigns the nearest limb to ANY
        // envelope hit (ray-pick, then a no-limit nearest-point fallback), so rejecting a shot that merely
        // grazed a limb -- or that skimmed one whose server-side pose/position drifted a hair from what the
        // shooter saw -- registers as "my hit didn't count". Testing against boxes grown by the tolerance
        // keeps genuine between-the-limbs whiffs rejected while letting those near-limb hits through.
        return !rigRayHitsWithin(victim, seg[0], seg[1], MedicalConfig.hitGapRejectTolerance());
    }


    public static boolean shouldRejectGap(LivingEntity victim, DamageSource src, DamageCategory cat) {
        HitRegMode mode = MedicalConfig.hitRegistrationMode();
        if (mode == HitRegMode.OFF) {
            return false;
        }
        if (isDirectMelee(src)) {
            if (!(src.getEntity() instanceof Player)) {
                return false;
            }
            return isGapShot(victim, src, cat);
        }
        if (mode != HitRegMode.PRECISE) {
            return false;
        }
        return isGapShot(victim, src, cat);
    }

    public static boolean isDirectMelee(DamageSource src) {
        if (src == null) {
            return false;
        }
        var attacker = src.getEntity();
        return attacker instanceof LivingEntity && src.getDirectEntity() == attacker;
    }


    private static @Nullable Vec3[] attackSegment(LivingEntity victim, DamageSource src, DamageCategory cat) {
        var direct = src.getDirectEntity();
        var attacker = src.getEntity();
        if (direct instanceof Projectile && direct != attacker) {
            Optional<Vec3[]> taczSeg = TaczCompat.bulletSegment(src);
            if (taczSeg.isPresent()) {
                return taczSeg.get();
            }
            Vec3 to = direct.position();
            Vec3 from = new Vec3(direct.xo, direct.yo, direct.zo);
            Vec3 dir = to.subtract(from);
            if (dir.lengthSqr() < 1.0e-8) {
                dir = direct.getDeltaMovement();
            }
            if (dir.lengthSqr() < 1.0e-8) {
                dir = victim.position().subtract(to);
            }
            if (dir.lengthSqr() < 1.0e-8) {
                return null;
            }
            Vec3 d = dir.normalize();
            return new Vec3[]{from.subtract(d.scale(TRACE_MARGIN)), to.add(d.scale(TRACE_MARGIN))};
        }
        if (attacker instanceof LivingEntity shooter && attacker != victim) {
            boolean ballistic = (cat == DamageCategory.BALLISTIC);
            boolean melee = (direct == attacker);
            if (ballistic || melee) {
                if (ballistic && TaczCompat.bulletHitPos(src).isPresent()) {
                    return null;
                }
                Vec3 eye = shooter.getEyePosition();
                Vec3 look = shooter.getViewVector(1.0F);
                double range = ballistic ? HITSCAN_RANGE : MedicalConfig.meleeReach();
                return new Vec3[]{eye, eye.add(look.scale(range))};
            }
        }
        return null;
    }


    public static @Nullable Vec3 resolveHitPoint(LivingEntity victim, DamageSource src, DamageCategory cat) {
        AABB box = victim.getBoundingBox();
        var direct = src.getDirectEntity();
        var attacker = src.getEntity();

        if (direct instanceof Projectile && direct != attacker) {
            Optional<Vec3> tacz = TaczCompat.bulletHitPos(src);
            if (tacz.isPresent()) {
                return tacz.get();
            }
            Vec3 to = direct.position();
            Vec3 from = new Vec3(direct.xo, direct.yo, direct.zo);
            Vec3 dir = to.subtract(from);
            if (dir.lengthSqr() < 1.0e-8) {
                dir = direct.getDeltaMovement();
            }
            if (dir.lengthSqr() < 1.0e-8) {
                dir = victim.position().subtract(to);
            }
            if (dir.lengthSqr() < 1.0e-8) {
                return nearestPointOnBox(box, to);
            }
            Vec3 d = dir.normalize();
            Optional<Vec3> hit = box.clip(from.subtract(d.scale(TRACE_MARGIN)), to.add(d.scale(TRACE_MARGIN)));
            final Vec3 target = to;
            return hit.orElseGet(() -> nearestPointOnBox(box, target));
        }

        if (attacker instanceof LivingEntity shooter && attacker != victim) {
            boolean ballistic = (cat == DamageCategory.BALLISTIC);
            boolean melee = (direct == attacker);
            if (ballistic || melee) {
                if (ballistic) {
                    Optional<Vec3> tacz = TaczCompat.bulletHitPos(src);
                    if (tacz.isPresent()) {
                        return tacz.get();
                    }
                }
                Vec3 eye = shooter.getEyePosition();
                Vec3 look = shooter.getViewVector(1.0F);
                double range = ballistic ? HITSCAN_RANGE : MedicalConfig.meleeReach();
                Vec3 end = eye.add(look.scale(range));
                Optional<Vec3> hit = box.clip(eye, end);
                return hit.orElseGet(() -> nearestPointOnBox(box, end));
            }
        }

        Vec3 srcPos = src.getSourcePosition();
        if (srcPos != null) {
            Vec3 centre = box.getCenter();
            return box.clip(srcPos, centre).orElse(centre);
        }

        return null;
    }


    public static @Nullable LimbType classifyRay(LivingEntity victim, Vec3 from, Vec3 to) {
        if (victim instanceof Player && MedicalConfig.riggedLimbBoxes() && rigUsable(victim)) {
            LimbType rigLimb = rigRayPick(victim, from, to);
            if (rigLimb != null) {
                return rigLimb;
            }
        }
        Optional<Vec3> hit = victim.getBoundingBox().clip(from, to);
        return hit.map(v -> classifyLocal(victim, v)).orElse(null);
    }

    private static boolean rigUsable(LivingEntity victim) {
        if (!rigPoseSupported(victim)) {
            return false;
        }
        if (isUprightHumanoid(victim)) {
            AABB box = victim.getBoundingBox();
            return box.getYsize() >= box.getXsize();
        }
        return true;
    }


    public static boolean rigPoseSupported(LivingEntity victim) {
        if (isUprightHumanoid(victim)) {
            return true;
        }
        if (victim instanceof Player player && MedicalState.isDowned(player)) {
            return true;
        }
        if (victim.isAutoSpinAttack()) {
            return false;
        }
        Pose pose = victim.getPose();
        if (pose == Pose.SLEEPING || pose == Pose.DYING) {
            return false;
        }
        return victim.isFallFlying() || victim.isVisuallySwimming() || victim.getSwimAmount(1.0F) > 0.0F;
    }

    public static boolean isUprightHumanoid(LivingEntity victim) {
        if (victim instanceof Player player && MedicalState.isDowned(player)) {
            return false;
        }
        if (victim.isVisuallySwimming() || victim.isFallFlying() || victim.isAutoSpinAttack()) {
            return false;
        }
        if (victim.getSwimAmount(1.0F) > 0.0F) {
            return false;
        }
        Pose pose = victim.getPose();
        return pose != Pose.SWIMMING && pose != Pose.FALL_FLYING && pose != Pose.SLEEPING
                && pose != Pose.DYING && pose != Pose.SPIN_ATTACK;
    }


    private static @Nullable LimbType rigRayPick(LivingEntity victim, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0e-12) {
            return null;
        }
        Vec3 origin = worldToLocalPoint(victim, from);
        Vec3 localDir = worldToLocalDir(victim, dir);
        HumanoidRig.LocalRig rig = RigCache.resolve(victim);
        double best = Double.POSITIVE_INFINITY;
        LimbType limb = null;
        for (Obb obb : rig.all()) {
            double t = obb.rayEntry(origin, localDir);
            if (t < best) {
                best = t;
                limb = obb.limb();
            }
        }
        return best == Double.POSITIVE_INFINITY ? null : limb;
    }

    /**
     * Whether the ray comes within {@code pad} of ANY limb box (each grown by {@code pad}). This is the
     * tolerant counterpart to {@link #rigRayPick} used only for gap rejection: it answers "did the shot skim
     * a limb" rather than "which limb", so a near-miss the classifier would still count is not rejected.
     * A {@code pad} of 0 makes it exactly {@code rigRayPick(...) != null}.
     */
    private static boolean rigRayHitsWithin(LivingEntity victim, Vec3 from, Vec3 to, double pad) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0e-12) {
            return false;
        }
        Vec3 origin = worldToLocalPoint(victim, from);
        Vec3 localDir = worldToLocalDir(victim, dir);
        HumanoidRig.LocalRig rig = RigCache.resolve(victim);
        double p = Math.max(pad, 0.0);
        for (Obb obb : rig.all()) {
            if (obb.rayEntry(origin, localDir, p) != Double.POSITIVE_INFINITY) {
                return true;
            }
        }
        return false;
    }

    private static List<LimbType> rigRayPierce(LivingEntity victim, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0e-12) {
            return List.of();
        }
        Vec3 origin = worldToLocalPoint(victim, from);
        Vec3 localDir = worldToLocalDir(victim, dir);
        HumanoidRig.LocalRig rig = RigCache.resolve(victim);
        Obb[] all = rig.all();
        double[] ts = new double[all.length];
        LimbType[] limbs = new LimbType[all.length];
        int n = 0;
        for (Obb obb : all) {
            double t = obb.rayEntry(origin, localDir);
            if (t != Double.POSITIVE_INFINITY) {
                ts[n] = t;
                limbs[n] = obb.limb();
                n++;
            }
        }
        if (n == 0) {
            return List.of();
        }
        for (int i = 1; i < n; i++) {
            double tk = ts[i];
            LimbType lk = limbs[i];
            int j = i - 1;
            while (j >= 0 && ts[j] > tk) {
                ts[j + 1] = ts[j];
                limbs[j + 1] = limbs[j];
                j--;
            }
            ts[j + 1] = tk;
            limbs[j + 1] = lk;
        }
        List<LimbType> out = new ArrayList<>(n);
        double budget = MedicalConfig.penetrationBudget();
        for (int i = 0; i < n; i++) {
            out.add(limbs[i]);
            budget -= MedicalConfig.penetrationResistance(limbs[i]);
            if (budget <= 0.0) {
                break;
            }
        }
        return out;
    }

    private static LimbType rigPointPick(LivingEntity victim, Vec3 worldPoint, DamageSource src, String branch) {
        Vec3 local = worldToLocalPoint(victim, worldPoint);
        HumanoidRig.LocalRig rig = RigCache.resolve(victim);
        double best = Double.POSITIVE_INFINITY;
        LimbType limb = LimbType.TORSO;
        for (Obb obb : rig.all()) {
            double d = obb.distanceSq(local);
            if (d < best) {
                best = d;
                limb = obb.limb();
            }
        }
        if (MedicalConfig.logHitDetection()) {
            HitDetectionDebug.logRigPointPick(victim, src, branch, rig, local, limb);
        }
        return limb;
    }

    private static Vec3 worldToLocalPoint(LivingEntity victim, Vec3 world) {
        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-front.z, 0.0, front.x);
        Vec3 off = world.subtract(victim.position());
        return new Vec3(off.dot(right), off.y, off.dot(front));
    }

    private static Vec3 worldToLocalDir(LivingEntity victim, Vec3 dir) {
        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-front.z, 0.0, front.x);
        return new Vec3(dir.dot(right), dir.y, dir.dot(front));
    }


    public static LimbType classifyLocal(LivingEntity victim, Vec3 worldHit) {
        AABB box = victim.getBoundingBox();

        if (victim instanceof Player player && MedicalState.isDowned(player)) {
            return null;
        }
        if (box.getYsize() < box.getXsize()) {
            return null;
        }
        if (!isUprightHumanoid(victim)) {
            return null;
        }

        Vec3 centre = box.getCenter();
        double relY = (worldHit.y - box.minY) / box.getYsize();

        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw)).normalize();
        Vec3 right = new Vec3(-front.z, 0.0, front.x);

        Vec3 off = worldHit.subtract(centre);
        double side = off.dot(right);
        double along = off.dot(front);
        double nx = side / (box.getXsize() * 0.5);

        if (MedicalConfig.poseAwareArms() && isAimingWeapon(victim)
                && along > 0.0
                && relY >= UPPER_ARM_LOW && relY < MedicalConfig.headBandBottom()) {
            return armForAimPose(victim, nx);
        }

        return classifyStanding(relY, nx);
    }

    private static LimbType classifyStanding(double relY, double nx) {
        if (relY >= MedicalConfig.headBandBottom()) {
            return LimbType.HEAD;
        }
        if (relY <= MedicalConfig.legBandTop()) {
            return (nx >= 0.0) ? LimbType.RIGHT_LEG : LimbType.LEFT_LEG;
        }
        if (Math.abs(nx) >= MedicalConfig.armSideThreshold()) {
            return (nx >= 0.0) ? LimbType.RIGHT_ARM : LimbType.LEFT_ARM;
        }
        return LimbType.TORSO;
    }


    private static Vec3 nearestPointOnBox(AABB box, Vec3 target) {
        double x = clamp(target.x, box.minX, box.maxX);
        double y = clamp(target.y, box.minY, box.maxY);
        double z = clamp(target.z, box.minZ, box.maxZ);
        return new Vec3(x, y, z);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (Math.min(v, hi));
    }


    private static boolean isAimingWeapon(LivingEntity victim) {
        if (victim.isUsingItem()) {
            ItemStack using = victim.getUseItem();
            if (using.getItem() instanceof BowItem || using.getItem() instanceof CrossbowItem) {
                return true;
            }
            UseAnim anim = using.getUseAnimation();
            if (anim == UseAnim.BOW || anim == UseAnim.SPEAR || anim == UseAnim.CROSSBOW) {
                return true;
            }
        }
        return TaczCompat.isHeldGun(victim.getMainHandItem());
    }


    private static LimbType armForAimPose(LivingEntity victim, double nx) {
        HumanoidArm main = (victim instanceof Player player) ? player.getMainArm() : HumanoidArm.RIGHT;
        boolean rightMain = (main == HumanoidArm.RIGHT);
        if (nx > 0.0) {
            return LimbType.RIGHT_ARM;
        }
        if (nx < 0.0) {
            return LimbType.LEFT_ARM;
        }
        return rightMain ? LimbType.RIGHT_ARM : LimbType.LEFT_ARM;
    }
}
