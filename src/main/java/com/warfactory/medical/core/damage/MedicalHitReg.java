package com.warfactory.medical.core.damage;

import com.warfactory.medical.compat.OpenPersistenceCompat;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.damage.rig.HumanoidRig;
import com.warfactory.medical.core.damage.rig.RigTuning;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Envelope hit-registration: the vanilla entity AABB is narrower than the humanoid model (the arms overhang
 * it, and a prone/crawl box is far shorter than the lying body), so shots that visually strike those regions
 * never register. This widens the box the ray is clipped against &mdash; <b>only</b> for the hit-scan, never
 * for collision/physics &mdash; so those hits land. Used by {@code ProjectileUtilMixin}. The PRECISE-mode
 * gap-rejection (throwing out shots that threaded between the limbs) lives in {@link HitGeometry#isGapShot}.
 *
 * <p><b>Static per-stance envelope.</b> The box is a fixed inflation of the vanilla collision box, sized per
 * stance (standing / crouching / prone / downed) via {@link MedicalConfig#hitEnvelopeReachHorizontal} /
 * {@link MedicalConfig#hitEnvelopeReachVertical}. Because the vanilla box already shrinks/grows with the pose
 * (tall upright, short-and-low while swimming/crawling), a per-stance reach lets the standing box stay tight
 * around the arm overhang while the body-horizontal prone/downed stances get the far reach the lying
 * silhouette needs; the fine-phase per-limb OBB test rejects any surplus, so over-sizing only costs a few
 * extra fine tests, never a missed hit. No per-hit pose rebuild &mdash; just a stance lookup and one inflate.
 * While hitbox tuning is {@link RigTuning#ACTIVE} the live-tuned reach is used instead of the config so the
 * drawn envelope and the real hit box resize together.</p>
 */
public final class MedicalHitReg {

    private MedicalHitReg() {
    }

    /**
     * Entities whose model is wider than their collision box: players and Open Persistence bodies.
     */
    public static boolean isEnvelopeTarget(Entity entity) {
        return entity instanceof Player || OpenPersistenceCompat.isPersistentBody(entity);
    }

    /**
     * The box the hit-scan should clip {@code entity} against: the vanilla collision box inflated by the
     * stance's envelope reach (horizontal on X/Z, vertical on Y). Non-targets, mode OFF, and a zero envelope
     * all get the normal box back, so non-players and the disabled path stay exactly vanilla. Kept cheap
     * &mdash; a stance resolve, a couple of reads and one {@link AABB#inflate}, no {@code compute()}.
     */
    public static AABB registrationBox(Entity entity) {
        AABB box = entity.getBoundingBox();
        if (MedicalConfig.hitRegistrationMode() == HitRegMode.OFF) {
            return box;
        }
        if (!isEnvelopeTarget(entity) || !(entity instanceof LivingEntity living)) {
            return box;
        }
        RigTuning.RigPose pose = HumanoidRig.resolvePose(living);
        double h;
        double v;
        if (RigTuning.ACTIVE) {
            // Debug: live-tuned reach, so the overlay and the real hit box track the command nudges together.
            h = RigTuning.envReach(pose, RigTuning.EnvAxis.HORIZONTAL);
            v = RigTuning.envReach(pose, RigTuning.EnvAxis.VERTICAL);
        } else {
            h = MedicalConfig.hitEnvelopeReachHorizontal(pose);
            v = MedicalConfig.hitEnvelopeReachVertical(pose);
        }
        if (h <= 0.0 && v <= 0.0) {
            return box;
        }
        return box.inflate(h, v, h);
    }
}
