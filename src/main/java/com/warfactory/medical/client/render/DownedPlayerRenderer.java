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
 * CLIENT-ONLY body pose for DOWNED players. When a player is passed out — the single merged
 * {@link com.warfactory.medical.core.HealthState#UNCONSCIOUS} state (either an opioid overdose or a
 * bleeding-out knockdown; see {@link ClientDownedTracker#isDowned(int)}) — this lays their rendered model
 * FACE-UP on the ground instead of the upright standing pose. Purely presentational; it reads only the
 * synced client tracker and never mutates medical state.
 *
 * <p>Self-registers on the FORGE bus, {@link Dist#CLIENT} only, so a dedicated server never classloads the
 * client render types referenced here. Because the local player is not drawn in first person, this affects
 * third-person / F5 and OTHER observers watching a downed player — which is exactly the intent.</p>
 *
 * <h2>Lay-down transform (applied in {@link #onRenderPre})</h2>
 * <p>{@link RenderPlayerEvent.Pre} fires with the {@link PoseStack} origin at the player's feet, in a
 * world-aligned frame (the renderer's own body-yaw rotation happens later, inside {@code setupRotations}).
 * We {@code pushPose()} and then, in order:</p>
 * <ol>
 *   <li>{@code translate(0, GROUND_LIFT, 0)} — nudge up so the lying body's ~quarter-block front-to-back
 *       depth rests on the block surface instead of half-sinking through it;</li>
 *   <li>{@code mulPose(Axis.XP.rotationDegrees(LAY_DEGREES))} — tip the standing model backward around the
 *       horizontal X axis so it ends up supine (on its back, face toward the sky). {@code LAY_DEGREES} is
 *       negative for the backward fall; flip its sign if a resource pack model renders face-down;</li>
 *   <li>{@code mulPose(Axis.YP.rotationDegrees(STABLE_YAW))} — a small fixed yaw so the sprawl is not
 *       perfectly axis-aligned. The renderer's later {@code setupRotations} spins the (now flat) body about
 *       the vertical axis by the body yaw, which only changes which compass direction the head points and
 *       does NOT flip face-up/face-down, so the supine orientation is stable regardless of facing.</li>
 * </ol>
 *
 * <h2>Per-limb sprawl</h2>
 * <p>The deterministic per-limb tilt is <em>not</em> applied here. {@link RenderPlayerEvent.Pre} fires
 * before {@code LivingEntityRenderer.render} calls {@code model.setupAnim(...)}, which reassigns every
 * part's rotation by value each frame — so any tilt written in this event is clobbered before the model is
 * drawn. The sprawl therefore lives in {@code HumanoidModelMixin}, which injects at the {@code TAIL} of
 * {@code setupAnim} (gated on the same {@link ClientDownedTracker#isDowned(int)} flag) so the offsets land in
 * the same window the animator uses and survive to the draw. Only the supine {@link PoseStack} transform is
 * this class's responsibility.</p>
 *
 * <h2>Robustness</h2>
 * <p>Every handler body is wrapped so a render-hook throw can never crash the game. If {@link #onRenderPre}
 * fails after pushing, {@link #onRenderPost} still pops (guarded by {@link #applied}) so the pose stack is
 * never left unbalanced.</p>
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DownedPlayerRenderer {

    /** Upward nudge (blocks) so the supine body rests on the surface rather than sinking into it. */
    private static final float GROUND_LIFT = 0.1F;
    /** Backward tip about the horizontal X axis to lay the model on its back (negative = fall backward). */
    private static final float LAY_DEGREES = -90.0F;
    /** Small fixed yaw so the sprawl reads natural instead of grid-aligned. */
    private static final float STABLE_YAW = 8.0F;

    /** {@code true} between a successful {@link #onRenderPre} push and its matching {@link #onRenderPost}. */
    private static boolean applied;

    private DownedPlayerRenderer() {
    }

    /**
     * Lay a downed player face-up. No-op (and no pose push) for any player that is not currently downed.
     */
    @SubscribeEvent
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (player == null || !ClientDownedTracker.isDowned(player.getId())) {
            return;
        }
        try {
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(0.0F, GROUND_LIFT, 0.0F);
            pose.mulPose(Axis.XP.rotationDegrees(LAY_DEGREES));
            pose.mulPose(Axis.YP.rotationDegrees(STABLE_YAW));
            applied = true;
        } catch (Throwable t) {
            // Never throw in a render hook. If we pushed above we may be unbalanced; onRenderPost pops when
            // applied is set, otherwise a partial failure before the push leaves the stack untouched.
            WFMedical.LOGGER.warn("[{}] Downed body pose failed; skipping this frame", WFMedical.MOD_ID, t);
        }
    }

    /**
     * Pop the pose pushed in {@link #onRenderPre}. Runs after the vanilla render (including the nameplate,
     * which is drawn outside our pushed pose and therefore stays upright).
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
