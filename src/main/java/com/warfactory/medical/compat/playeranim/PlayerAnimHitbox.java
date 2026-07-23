package com.warfactory.medical.compat.playeranim;

import com.warfactory.medical.core.damage.rig.HumanoidRig;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * CLIENT-ONLY bridge that feeds KosmX PlayerAnimator's animated bone transforms into the limb hitbox rig via
 * {@link HumanoidRig.AnimSampler}, so a player's hitboxes track any animation played on them (emotes, mod
 * animations) instead of only the vanilla pose.
 *
 * <p>Only {@link AbstractClientPlayer}s carry PlayerAnimator state, so mobs and any server-side rig build fall
 * through to the vanilla pose (returning {@code null}). This class names PlayerAnimator types directly, so it
 * must only ever be class-loaded when the {@code playeranimator} mod is present &mdash; {@link #register()} is
 * called from client setup solely behind that mod-presence check.
 *
 * <p><b>Authority note.</b> PlayerAnimator is client-only, so the animated pose reaches the server hit test
 * only through the victim's pose stream ({@code HitAuthority.CLIENT_HINT}); under server authority the server
 * rebuilds the vanilla pose and only the client-side visualization is animated.
 */
public final class PlayerAnimHitbox implements HumanoidRig.AnimSampler {

    private PlayerAnimHitbox() {
    }

    /**
     * Register this bridge as the rig's animation sampler. Call once, client-side, only when PlayerAnimator
     * is loaded.
     */
    public static void register() {
        HumanoidRig.setAnimSampler(new PlayerAnimHitbox());
    }

    @Override
    public double[] sample(LivingEntity entity, String bone,
                           double x, double y, double z, double xRot, double yRot, double zRot) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return null;
        }
        AnimationApplier anim = ((IAnimatedPlayer) player).getAnimation();
        if (anim == null || !anim.isActive()) {
            return null;
        }
        // Query the same 3-arg transform PlayerAnimator itself uses in updatePart, so the hitbox pose matches
        // the rendered pose exactly (POSITION composes the pivot, ROTATION the Euler angles; both 1:1 with
        // ModelPart, no reordering or sign changes). Bones the current animation doesn't define return value0.
        Vec3f pos = anim.get3DTransform(bone, TransformType.POSITION,
                new Vec3f((float) x, (float) y, (float) z));
        Vec3f rot = anim.get3DTransform(bone, TransformType.ROTATION,
                new Vec3f((float) xRot, (float) yRot, (float) zRot));
        return new double[] {
                pos.getX().floatValue(), pos.getY().floatValue(), pos.getZ().floatValue(),
                rot.getX().floatValue(), rot.getY().floatValue(), rot.getZ().floatValue()
        };
    }
}
