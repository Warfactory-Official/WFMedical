package com.warfactory.medical.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.ClientDownedTracker;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLIENT-ONLY: lays a downed player face-up on the ground (UNCONSCIOUS from any cause). Transform order:
 * nudge up (GROUND_LIFT), tip backward 90° on X (LAY_DEGREES), small fixed yaw (STABLE_YAW). Y-axis
 * setupRotations only spins the flat body in the compass plane — does NOT flip face/back — so supine is
 * stable regardless of facing. Per-limb sprawl is NOT applied here (setupAnim would clobber it); it lives
 * in HumanoidModelMixin at TAIL of setupAnim. {@link #applied} guards the pop so the pose stack is never
 * left unbalanced even when onRenderPre throws after pushing.
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DownedPlayerRenderer {

    /**
     * Nudge up so the lying body rests on the surface rather than sinking through it.
     */
    private static final float GROUND_LIFT = 0.1F;
    /**
     * Backward tip on X axis; negative = fall backward. Flip sign for face-down resource packs.
     */
    private static final float LAY_DEGREES = -90.0F;
    private static final float STABLE_YAW = 8.0F;

    /**
     * True between a successful Pre push and its matching Post pop.
     */
    private static boolean applied;

    private DownedPlayerRenderer() {
    }

    @SubscribeEvent
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (player == null || !ClientDownedTracker.isDowned(player.getId())) {
            return;
        }
        try {
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            // Flag immediately after the (atomic) push so onRenderPost always pops -- even if a transform
            // below throws -- instead of leaking the pushed pose and unbalancing the stack.
            applied = true;
            pose.translate(0.0F, GROUND_LIFT, 0.0F);
            pose.mulPose(Axis.XP.rotationDegrees(LAY_DEGREES));
            pose.mulPose(Axis.YP.rotationDegrees(STABLE_YAW));
        } catch (Throwable t) {
            // Never throw in a render hook; onRenderPost still pops the pushed pose because applied is set.
            WFMedical.LOGGER.warn("[{}] Downed body pose failed; skipping this frame", WFMedical.MOD_ID, t);
        }
    }

    /**
     * Pop after vanilla render; nameplate drawn outside our pose so it stays upright.
     */
    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (!applied) {
            return;
        }
        applied = false;
        try {
            event.getPoseStack().popPose();
        } catch (Throwable t) {
            WFMedical.LOGGER.warn("[{}] Downed body pose cleanup failed", WFMedical.MOD_ID, t);
        }
    }
}
