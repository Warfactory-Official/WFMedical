package com.warfactory.medical.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.ClientTourniquetTracker;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public final class TourniquetLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation(WFMedical.MOD_ID, "textures/entity/tourniquet.png");
    private static final ResourceLocation MODEL_LOC =
            new ResourceLocation(WFMedical.MOD_ID, "models/entity/tourniquet.obj");


    public static final float SCALE = 1.1F;
    private static final double ARM_DOWN = 0.125;  // slide ~6px down the arm, in blocks
    private static final double LEG_DOWN = 0.250; // slide ~7px down the leg, in blocks
    private static final double ARM_OFFSET = 0.0575f;

    private static ObjModel cachedModel;
    private static boolean loadFailed;

    public TourniquetLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static ObjModel model() {
        if (cachedModel == null && !loadFailed) {
            cachedModel = ObjModel.load(MODEL_LOC);
            loadFailed = (cachedModel == null);
        }
        return cachedModel;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        int mask = ClientTourniquetTracker.mask(player.getId());
        if (mask == 0 || player.isInvisible()) {
            return;
        }
        ObjModel m = model();
        if (m == null) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        renderOn(mask, LimbType.RIGHT_ARM, model.rightArm, ARM_DOWN, -ARM_OFFSET, pose, vc, light, overlay, m);
        renderOn(mask, LimbType.LEFT_ARM, model.leftArm, ARM_DOWN, ARM_OFFSET, pose, vc, light, overlay, m);
        renderOn(mask, LimbType.RIGHT_LEG, model.rightLeg, LEG_DOWN, 0, pose, vc, light, overlay, m);
        renderOn(mask, LimbType.LEFT_LEG, model.leftLeg, LEG_DOWN, 0, pose, vc, light, overlay, m);
    }

    private static void renderOn(int mask, LimbType limb, ModelPart part, double down, double armOffset, PoseStack pose,
                                 VertexConsumer vc, int light, int overlay, ObjModel m) {
        if ((mask & (1 << limb.ordinal())) == 0) {
            return;
        }
        pose.pushPose();
        part.translateAndRotate(pose);
        pose.translate(armOffset, down, 0 );
        pose.scale(SCALE, -SCALE, SCALE);
        m.render(pose, vc, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }
}
