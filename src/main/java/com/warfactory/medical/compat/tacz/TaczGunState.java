package com.warfactory.medical.compat.tacz;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Reads TACZ's synced gun state for driving the baked arm pose. Uses {@code getSynAimingProgress()} from the
 * server-synchronized API, so aim tracking is authoritative. References TACZ types directly (compileOnly dep);
 * callers gate on {@code TaczCompat.isHeldGun} so the JVM never class-loads this without TACZ present.
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
