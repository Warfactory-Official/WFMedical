package com.warfactory.medical.core.damage;

import com.warfactory.medical.compat.OpenPersistenceCompat;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.damage.rig.HumanoidRig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Envelope hit-registration: the vanilla entity AABB is narrower than the humanoid model (the arms overhang
 * it, and a prone/crawl box is far shorter than the lying body), so shots that visually strike those regions
 * never register. This widens the box the ray is clipped against &mdash; <b>only</b> for the hit-scan, never
 * for collision/physics &mdash; so those hits land. Used by {@code ProjectileUtilMixin}. The PRECISE-mode
 * gap-rejection (throwing out shots that threaded between the limbs) lives in {@link HitGeometry#isGapShot}.
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
     * The box the hit-scan should clip {@code entity} against. For an envelope target (mode != OFF) the
     * collision box is widened to the model silhouette &mdash; horizontally for an upright body (to reach the
     * arms), all-round for a prone/crawl body (whose short box sits well inside the lying model). Everything
     * else (non-targets, mode OFF, zero inflation) gets its normal box back, so non-players and the disabled
     * path stay exactly vanilla. Kept cheap: it runs once per candidate per hit-scan.
     */
    public static AABB registrationBox(Entity entity) {
        AABB box = entity.getBoundingBox();
        if (MedicalConfig.hitRegistrationMode() == HitRegMode.OFF) {
            return box;
        }
        if (!(entity instanceof LivingEntity living) || !isEnvelopeTarget(entity)) {
            return box;
        }
        double inflate = MedicalConfig.hitEnvelopeInflation();
        if (inflate <= 0.0) {
            return box;
        }

        if (MedicalConfig.riggedLimbBoxes() && HitGeometry.rigPoseSupported(living)) {
            AABB rigBounds = HumanoidRig.worldBounds(living);
            return box.minmax(rigBounds).inflate(inflate);
        }

        if (!HitGeometry.isUprightHumanoid(living)) {
            double standing = living.getDimensions(Pose.STANDING).height;
            double reach = Math.max(standing, 1.0) * 0.5 + inflate;
            return box.inflate(reach, inflate, reach);
        }
        return box.inflate(inflate, 0.0, inflate);
    }
}
