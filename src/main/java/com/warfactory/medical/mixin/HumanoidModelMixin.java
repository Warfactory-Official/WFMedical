package com.warfactory.medical.mixin;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.ClientDownedTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * CLIENT-ONLY: adds per-limb sprawl to a downed player's rendered body. MUST inject at TAIL of
 * setupAnim because the animator overwrites rotations by value each frame — anything written earlier
 * is clobbered. The outer cosmetic layer copies base-part rotations after super.setupAnim, so it
 * follows the sprawl automatically. Random is seeded with entity ID for stable per-player offsets.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    private static final float WFMEDICAL$TILT_RANGE = 0.28F;
    @Shadow
    public ModelPart head;
    @Shadow
    public ModelPart rightArm;
    @Shadow
    public ModelPart leftArm;
    @Shadow
    public ModelPart rightLeg;
    @Shadow
    public ModelPart leftLeg;

    private static void wfmedical$sprawl(ModelPart part, Random rng) {
        part.xRot += wfmedical$tilt(rng);
        part.yRot += wfmedical$tilt(rng);
        part.zRot += wfmedical$tilt(rng);
    }

    private static float wfmedical$tilt(Random rng) {
        return (rng.nextFloat() * 2.0F - 1.0F) * WFMEDICAL$TILT_RANGE;
    }

    /**
     * Targets the real {@code setupAnim(LivingEntity,...)} descriptor so the synthetic Entity bridge is not double-injected.
     */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void wfmedical$sprawlWhenDowned(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                            float ageInTicks, float netHeadYaw, float headPitch,
                                            CallbackInfo callbackInfo) {
        try {
            // Vanilla setupAnim re-sets head xRot/yRot every frame but NEVER touches head zRot (it assumes 0).
            // Our downed head-roll below writes head.zRot, and because the PlayerModel is POOLED/shared across
            // every player, that roll would otherwise persist into later, non-downed renders (a respawned or
            // nearby player's head stays crooked). Clear it every render; we re-apply our own roll only while
            // downed. (Arm/leg zRot are reset by setupAnim itself, so only the head needs this.)
            this.head.zRot = 0.0F;
            if (!(entity instanceof Player player) || !ClientDownedTracker.isDowned(player.getId())) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
                // In first person only the held-item arm is drawn (via renderHand, which also calls
                // setupAnim); leave it untilted so the viewmodel stays natural. The downed body pose is a
                // third-person / observer effect anyway.
                return;
            }
            Random rng = new Random(player.getId());
            // Head: OVERWRITE (not add) to a near-neutral resting pose so a passed-out player's head does not
            // stay cranked toward wherever they were looking when they went down (the animator sets head.yRot
            // from the frozen look yaw). A tiny seeded tilt keeps it from looking rigid.
            this.head.xRot = wfmedical$tilt(rng) * 0.5F;
            this.head.yRot = wfmedical$tilt(rng) * 0.5F;
            this.head.zRot = wfmedical$tilt(rng);
            // Limbs: additive sprawl on top of the freshly-animated pose.
            wfmedical$sprawl(this.rightArm, rng);
            wfmedical$sprawl(this.leftArm, rng);
            wfmedical$sprawl(this.rightLeg, rng);
            wfmedical$sprawl(this.leftLeg, rng);
        } catch (Throwable t) {
            WFMedical.LOGGER.warn("[{}] Downed limb sprawl failed; skipping this frame", WFMedical.MOD_ID, t);
        }
    }
}
