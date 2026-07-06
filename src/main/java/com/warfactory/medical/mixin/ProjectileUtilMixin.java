package com.warfactory.medical.mixin;

import com.warfactory.medical.core.damage.MedicalHitReg;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Envelope hit-registration.
 * Only the box used for the hit-scan clip is changed, collision and physics still use the real box.
 * When {@code hitRegistrationMode} is OFF the redirect returns the unchanged box, so vanilla behaviour and
 * mod compatibility are untouched.
 */
@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {

    @Redirect(
            method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB wfmedical$envelopeBoxMelee(Entity entity) {
        return MedicalHitReg.registrationBox(entity);
    }

    @Redirect(
            method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB wfmedical$envelopeBoxProjectile(Entity entity) {
        return MedicalHitReg.registrationBox(entity);
    }
}
