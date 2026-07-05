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
 * CLIENT-ONLY: adds the deterministic per-limb "sprawl" to a DOWNED player's rendered body.
 *
 * <p>The supine lay-down transform lives in {@code DownedPlayerRenderer} (a {@code PoseStack} rotation in
 * {@link net.minecraftforge.client.event.RenderPlayerEvent}). The limb sprawl, however, MUST be applied
 * <em>after</em> {@link HumanoidModel#setupAnim}: the animator reassigns every part's {@code xRot/yRot/zRot}
 * by value each frame, so any tilt written before it (e.g. in {@code RenderPlayerEvent.Pre}) is clobbered
 * before {@code renderToBuffer} draws the model. Injecting at the {@code TAIL} of {@code setupAnim} runs our
 * offsets on top of the freshly-computed pose, in the same window the vanilla animator uses, so they survive
 * to the draw. Because {@code setupAnim} recomputes from scratch for the next entity, no save/restore of the
 * pooled model is needed — a non-downed player simply gets an untouched pose.</p>
 *
 * <p>For {@code PlayerModel} the outer cosmetic layer (hat, jacket, sleeves, pants) is copied from the base
 * parts <em>after</em> {@code super.setupAnim(...)} returns — i.e. after this injection — so the overlay
 * follows the sprawl automatically. Worn armor likewise copies the base part rotations at layer time.</p>
 *
 * <p>The offsets are drawn from a {@link Random} seeded with the entity id, so each player's sprawl is stable
 * frame-to-frame yet differs between players. Everything is wrapped so a render-hook throw can never crash the
 * game, honouring the "never throw in a render hook" rule.</p>
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    /**
     * Half-range (radians) of the per-limb random tilt; kept subtle for a relaxed sprawl, not a T-pose.
     */
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

    /**
     * Add a subtle seeded tilt to each rotation axis of one limb.
     */
    private static void wfmedical$sprawl(ModelPart part, Random rng) {
        part.xRot += wfmedical$tilt(rng);
        part.yRot += wfmedical$tilt(rng);
        part.zRot += wfmedical$tilt(rng);
    }

    /**
     * A subtle signed tilt in {@code [-TILT_RANGE, +TILT_RANGE]} radians from the seeded generator.
     */
    private static float wfmedical$tilt(Random rng) {
        return (rng.nextFloat() * 2.0F - 1.0F) * WFMEDICAL$TILT_RANGE;
    }

    /**
     * After the vanilla animator has posed the humanoid, add the seeded limb sprawl for downed players.
     * Targets the real {@code setupAnim(LivingEntity, ...)} by descriptor so the synthetic {@code Entity}
     * bridge is not double-injected.
     */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void wfmedical$sprawlWhenDowned(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                            float ageInTicks, float netHeadYaw, float headPitch,
                                            CallbackInfo callbackInfo) {
        try {
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
            wfmedical$sprawl(this.head, rng);
            wfmedical$sprawl(this.rightArm, rng);
            wfmedical$sprawl(this.leftArm, rng);
            wfmedical$sprawl(this.rightLeg, rng);
            wfmedical$sprawl(this.leftLeg, rng);
        } catch (Throwable t) {
            WFMedical.LOGGER.warn("[{}] Downed limb sprawl failed; skipping this frame", WFMedical.MOD_ID, t);
        }
    }
}
