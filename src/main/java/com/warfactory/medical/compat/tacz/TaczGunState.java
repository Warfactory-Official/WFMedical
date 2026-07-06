package com.warfactory.medical.compat.tacz;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Reads TACZ's <i>synced</i> gun state off a {@link LivingEntity}, for driving the baked arm pose
 * ({@link TaczArmPose}). Uses {@link IGunOperator#getSynAimingProgress()} which is in {@code com.tacz.guns
 * .api.entity} (present and synchronized on the dedicated server), so the pose tracks aim authoritatively
 * with no client trust.
 *
 * <p>This class references TACZ types directly (TACZ is a {@code compileOnly} dependency), so it must only be
 * touched when TACZ is actually present. Callers reach it exclusively through the gun-pose branch of
 * {@link com.warfactory.medical.core.damage.rig.HumanoidRig}, which is gated on
 * {@link com.warfactory.medical.compat.TaczCompat#isHeldGun} (true only when TACZ is loaded) &mdash; so the
 * JVM never class-loads this on a TACZ-less instance.</p>
 */
public final class TaczGunState {

    private TaczGunState() {
    }

    /**
     * Synced aiming progress in [0,1] for a gun-operating entity (0 = hip, 1 = full ADS). Returns 0 on any
     * failure (non-operator entity, API change), so the pose degrades to the relaxed hold.
     */
    public static float aimingProgress(LivingEntity entity) {
        try {
            IGunOperator operator = IGunOperator.fromLivingEntity(entity);
            if (operator == null) {
                return 0.0F;
            }
            return Mth.clamp(operator.getSynAimingProgress(), 0.0F, 1.0F);
        } catch (Throwable t) {
            return 0.0F;
        }
    }
}
