package com.warfactory.medical.core.damage;

import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.compat.TaczCompat;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.damage.rig.HumanoidRig;
import com.warfactory.medical.core.damage.rig.Obb;
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

import java.util.Optional;


public final class HitGeometry {


    private static final double HITSCAN_RANGE = 64.0;
   // Slack (blocks) so a fast projectile already past/inside the box still yields an entry point.
    private static final double TRACE_MARGIN = 1.0;
    //Pose band floor: frontal-upper hits above this relY are reassigned to a raised arm when aiming.
    private static final double UPPER_ARM_LOW = 0.55;

    private HitGeometry() {
    }


    public static @Nullable LimbType classifyHit(LivingEntity victim, DamageSource src, DamageCategory cat) {
        if (victim instanceof Player && MedicalConfig.riggedLimbBoxes() && rigUsable(victim)) {
            LimbType rigLimb = classifyRig(victim, src, cat);
            if (rigLimb != null) {
                return rigLimb;
            }
        }
        Vec3 hit = resolveHitPoint(victim, src, cat);
        return hit == null ? null : classifyLocal(victim, hit);
    }

    /**
     * Tier-2 rig classify: build the <b>un-clipped</b> attack ray (projectile segment / eye&rarr;look /
     * blast-centre&rarr;box) and intersect it against the posed limb OBBs, taking the first one entered.
     * Point-only sources (a resolved explosion / TACZ impact point) pick the nearest OBB by
     * {@link Obb#distanceSq}. Returns {@code null} when no OBB resolves, so the caller falls back to
     * Tier-1 banding. Deterministic: no {@code RandomSource}.
     */
    private static @Nullable LimbType classifyRig(LivingEntity victim, DamageSource src, DamageCategory cat) {
        AABB box = victim.getBoundingBox();
        var direct = src.getDirectEntity();
        var attacker = src.getEntity();

        // (1) Projectile: the un-clipped segment it actually flew (extended so it spans arm OBBs).
        if (direct instanceof Projectile && direct != attacker) {
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
                return rigPointPick(victim, nearestPointOnBox(box, to));
            }
            Vec3 d = dir.normalize();
            return rigRayPick(victim, from.subtract(d.scale(TRACE_MARGIN)), to.add(d.scale(TRACE_MARGIN)));
        }

        // (2) Hitscan / melee: the attacker's un-clipped aim ray. Self-damage guard preserved.
        if (attacker instanceof LivingEntity shooter && attacker != victim) {
            boolean ballistic = (cat == DamageCategory.BALLISTIC);
            boolean melee = (direct == attacker);
            if (ballistic || melee) {
                if (ballistic) {
                    Optional<Vec3> tacz = TaczCompat.bulletHitPos(src);
                    if (tacz.isPresent()) {
                        return rigPointPick(victim, tacz.get());
                    }
                }
                Vec3 eye = shooter.getEyePosition();
                Vec3 look = shooter.getViewVector(1.0F);
                double range = ballistic ? HITSCAN_RANGE : MedicalConfig.meleeReach();
                return rigRayPick(victim, eye, eye.add(look.scale(range)));
            }
        }

        // (3) Explosion / positional: blast-centre entry point, nearest OBB.
        Vec3 srcPos = src.getSourcePosition();
        if (srcPos != null) {
            Vec3 centre = box.getCenter();
            Vec3 entry = box.clip(srcPos, centre).orElse(centre);
            return rigPointPick(victim, entry);
        }

        // (4) No geometry.
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
            return false; // point-only / no-ray source -> not a gap
        }
        // Fast path: the ray crosses the real collision box -> a solid centre-mass hit, no rig needed.
        if (victim.getBoundingBox().clip(seg[0], seg[1]).isPresent()) {
            return false;
        }
        // Margin shot only: build the rig and test the actual limb boxes. Gap (whiff) if it hits none.
        return rigRayPick(victim, seg[0], seg[1]) == null;
    }


    private static @Nullable Vec3[] attackSegment(LivingEntity victim, DamageSource src, DamageCategory cat) {
        var direct = src.getDirectEntity();
        var attacker = src.getEntity();
        if (direct instanceof Projectile && direct != attacker) {
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
            return new Vec3[] {from.subtract(d.scale(TRACE_MARGIN)), to.add(d.scale(TRACE_MARGIN))};
        }
        if (attacker instanceof LivingEntity shooter && attacker != victim) {
            boolean ballistic = (cat == DamageCategory.BALLISTIC);
            boolean melee = (direct == attacker);
            if (ballistic || melee) {
                if (ballistic && TaczCompat.bulletHitPos(src).isPresent()) {
                    return null; // point impact, not a ray
                }
                Vec3 eye = shooter.getEyePosition();
                Vec3 look = shooter.getViewVector(1.0F);
                double range = ballistic ? HITSCAN_RANGE : MedicalConfig.meleeReach();
                return new Vec3[] {eye, eye.add(look.scale(range))};
            }
        }
        return null;
    }


    public static @Nullable Vec3 resolveHitPoint(LivingEntity victim, DamageSource src, DamageCategory cat) {
        AABB box = victim.getBoundingBox();
        var direct = src.getDirectEntity();
        var attacker = src.getEntity();

        // (1) Projectile entity: trace the segment it actually flew last tick.
        if (direct instanceof Projectile && direct != attacker) {
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

        // (2) Hitscan bullet OR (3) melee: ray from the attacker's eye along their aim. Self-damage
        // (thorns, self-inflicted) is non-directional: skip the aim ray and fall through (spec section 8).
        if (attacker instanceof LivingEntity shooter && attacker != victim) {
            boolean ballistic = (cat == DamageCategory.BALLISTIC);
            boolean melee = (direct == attacker);
            if (ballistic || melee) {
                // Prefer a real ballistic impact point if a future TACZ build exposes one.
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

        // (4) Explosion / any positional source: entry face toward the source point.
        Vec3 srcPos = src.getSourcePosition();
        if (srcPos != null) {
            Vec3 centre = box.getCenter();
            return box.clip(srcPos, centre).orElse(centre);
        }

        // (5) No geometry.
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
        if (victim instanceof Player player && MedicalState.isDowned(player)) {
            return false;
        }
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
        if (victim.isAutoSpinAttack()) {
            return false;
        }
        Pose pose = victim.getPose();
        if (pose == Pose.SLEEPING || pose == Pose.DYING) {
            return false;
        }
        // The tilted rig covers exactly the poses PlayerRenderer.setupRotations pitches horizontal.
        return victim.isFallFlying() || victim.isVisuallySwimming() || victim.getSwimAmount(1.0F) > 0.0F;
    }

    public static boolean isUprightHumanoid(LivingEntity victim) {
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
        HumanoidRig.LocalRig rig = HumanoidRig.compute(victim);
        double best = Double.POSITIVE_INFINITY;
        LimbType limb = null;
        for (Obb obb : rig.all()) {
            double t = obb.rayEntry(origin, localDir);
            if (t < best) {
                best = t;
                limb = obb.limb;
            }
        }
        return best == Double.POSITIVE_INFINITY ? null : limb;
    }

    /** Nearest-OBB (by {@link Obb#distanceSq}) limb for a point-only source; never {@code null}. */
    private static LimbType rigPointPick(LivingEntity victim, Vec3 worldPoint) {
        Vec3 local = worldToLocalPoint(victim, worldPoint);
        HumanoidRig.LocalRig rig = HumanoidRig.compute(victim);
        double best = Double.POSITIVE_INFINITY;
        LimbType limb = LimbType.TORSO;
        for (Obb obb : rig.all()) {
            double d = obb.distanceSq(local);
            if (d < best) {
                best = d;
                limb = obb.limb;
            }
        }
        return limb;
    }

    /** World point -> entity-local (feet origin, Y-up, X=right, Z=front), body-yaw removed. */
    private static Vec3 worldToLocalPoint(LivingEntity victim, Vec3 world) {
        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-front.z, 0.0, front.x);
        Vec3 off = world.subtract(victim.position());
        return new Vec3(off.dot(right), off.y, off.dot(front));
    }

    /** World direction -> entity-local axes (no translation), matching {@link #worldToLocalPoint}. */
    private static Vec3 worldToLocalDir(LivingEntity victim, Vec3 dir) {
        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-front.z, 0.0, front.x);
        return new Vec3(dir.dot(right), dir.y, dir.dot(front));
    }


    public static LimbType classifyLocal(LivingEntity victim, Vec3 worldHit) {
        AABB box = victim.getBoundingBox();

        // (3.5) Downed fallback: height bands are meaningless on the low-wide rotated box.
        if (victim instanceof Player player && MedicalState.isDowned(player)) {
            return null;
        }
        if (box.getYsize() < box.getXsize()) {
            // Box wider-than-tall guard: any lying / flattened pose -> defer to the sampler.
            return null;
        }
        if (!isUprightHumanoid(victim)) {
            // Crawling / swimming / flying: the model is tilted horizontal, so vertical bands are invalid.
            return null;
        }

        Vec3 centre = box.getCenter();
        double relY = (worldHit.y - box.minY) / box.getYsize();

        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw)).normalize();
        Vec3 right = new Vec3(-front.z, 0.0, front.x);

        Vec3 off = worldHit.subtract(centre);
        double side = off.dot(right);   // >0 = victim's RIGHT, <0 = LEFT
        double along = off.dot(front);  // >0 = FRONT, <0 = BACK
        double nx = side / (box.getXsize() * 0.5);

        // (3.4) Pose-aware arms: a frontal, upper hit while aiming takes the raised arm first.
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
        // A held TACZ gun raises the arms even without a vanilla use-anim.
        return TaczCompat.isHeldGun(victim.getMainHandItem()) || TaczCompat.isHeldGun(victim.getOffhandItem());
    }


    private static LimbType armForAimPose(LivingEntity victim, double nx) {
        HumanoidArm main = (victim instanceof Player player) ? player.getMainArm() : HumanoidArm.RIGHT;
        boolean rightMain = (main == HumanoidArm.RIGHT);
        // The near side of the entry decides which raised hand caught it; ties go to the main hand.
        if (nx > 0.0) {
            return LimbType.RIGHT_ARM;
        }
        if (nx < 0.0) {
            return LimbType.LEFT_ARM;
        }
        return rightMain ? LimbType.RIGHT_ARM : LimbType.LEFT_ARM;
    }
}
