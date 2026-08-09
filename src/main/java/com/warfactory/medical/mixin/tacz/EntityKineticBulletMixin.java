package com.warfactory.medical.mixin.tacz;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.TacHitResult;
import com.warfactory.medical.compat.TaczHitCapture;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.damage.HitDetectionDebug;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ resolves its own precise entity-part hit point in {@code onHitEntity} but never attaches it
 * to the {@link net.minecraft.world.damagesource.DamageSource} it hands to vanilla {@code hurt()} —
 * by the time WFMedical's damage handler runs, that geometry is gone. Capture it here, keyed by the
 * bullet's own entity id, so {@code TaczCompat#bulletHitPos} can hand it back to the rig classifier.
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class EntityKineticBulletMixin {

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void wfmedical$captureHitPos(TacHitResult hitResult, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        Entity bullet = (Entity) (Object) this;
        TaczHitCapture.capture(bullet.getId(), hitResult.getLocation(), startVec, endVec);
    }


    @Inject(method = "tacAttackEntity", at = @At("HEAD"))
    private void wfmedical$captureTotalDamage(EntityKineticBullet.MaybeMultipartEntity target, float amount,
                                              Pair<DamageSource, DamageSource> damageSources, CallbackInfo ci) {
        Entity bullet = (Entity) (Object) this;
        TaczHitCapture.captureDamage(bullet.getId(), amount);
    }

    /**
     * Diagnostic-only: TACZ's own broad-phase collision test decides whether a shot hits anything at all
     * BEFORE WFMedical's rig OBBs ever get consulted, using its own (vanilla-derived) fixed bounding box.
     * A shot landing only inside a rig limb box that sticks out past that box (e.g. an outstretched arm)
     * never reaches {@code onHitEntity} and produces no damage. This redirect leaves TACZ's real behavior
     * untouched but, when {@link MedicalConfig#logHitDetection()} is on, compares its verdict against the
     * rig classification for every nearby player so that gap can be observed directly in the log.
     */
    @Redirect(method = "onBulletTick",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/util/EntityUtil;findEntityOnPath("
                            + "Lnet/minecraft/world/entity/projectile/Projectile;"
                            + "Lnet/minecraft/world/phys/Vec3;"
                            + "Lnet/minecraft/world/phys/Vec3;"
                            + ")Lcom/tacz/guns/entity/EntityKineticBullet$EntityResult;"))
    private EntityKineticBullet.EntityResult wfmedical$debugFindEntityOnPath(
            Projectile shooter, Vec3 startVec, Vec3 endVec) {
        EntityKineticBullet.EntityResult real = EntityUtil.findEntityOnPath(shooter, startVec, endVec);
        if (MedicalConfig.logHitDetection()) {
            HitDetectionDebug.logBulletPath(shooter, startVec, endVec, real == null ? null : real.getEntity());
        }
        return real;
    }
}
