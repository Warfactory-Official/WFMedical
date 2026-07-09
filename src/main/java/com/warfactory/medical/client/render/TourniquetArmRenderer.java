package com.warfactory.medical.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.ClientTourniquetTracker;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First-person: draws the worn tourniquet on the local player's own arm when that arm wears one, so the
 * wearer sees it while looking at their hands. Purely additive &mdash; it never cancels the vanilla arm.
 * Placement is tunable and independent of the third-person {@link TourniquetLayer}.
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TourniquetArmRenderer {

    /**
     * Model units down the first-person arm to seat the band. Tunable, independent of the third-person layer.
     */
    private static final double ARM_DOWN_FP = 6.0;

    private TourniquetArmRenderer() {
    }

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        LimbType limb = event.getArm() == HumanoidArm.RIGHT ? LimbType.RIGHT_ARM : LimbType.LEFT_ARM;
        if (!ClientTourniquetTracker.has(player.getId(), limb.ordinal())) {
            return;
        }
        ObjModel m = TourniquetLayer.model();
        if (m == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TourniquetLayer.TEXTURE));
        pose.pushPose();
        // The first-person arm transform is already applied (the vanilla arm ModelPart renders at this pose),
        // so we only slide down the arm and scale block->model with a Y flip, like the third-person layer.
        pose.translate(0.0, ARM_DOWN_FP, 0.0);
        pose.scale(TourniquetLayer.SCALE, -TourniquetLayer.SCALE, TourniquetLayer.SCALE);
        m.render(pose, vc, event.getPackedLight(), OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }
}
