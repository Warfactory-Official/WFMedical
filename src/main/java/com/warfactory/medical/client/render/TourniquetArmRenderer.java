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

@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TourniquetArmRenderer {

    private static final double ARM_DOWN_FP = 0.375; // slide ~6px down the arm, in blocks

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
        pose.translate(0.0, ARM_DOWN_FP, 0.0);
        pose.scale(TourniquetLayer.SCALE, -TourniquetLayer.SCALE, TourniquetLayer.SCALE);
        m.render(pose, vc, event.getPackedLight(), OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }
}
