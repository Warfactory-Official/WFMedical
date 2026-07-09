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

/**
 * Third-person render layer that draws the worn tourniquet model on whichever arm/leg a player currently has
 * one applied to (driven by {@link ClientTourniquetTracker}, so it shows on any tracked player). The model is
 * placed in each limb bone's local space, so it follows the animated limb.
 *
 * <p><b>Placement constants are tunable.</b> The OBJ is authored in block units and re-centred on load; the
 * limb bone space is 1/16-block model units, Y-down, with the origin at the shoulder/hip pivot. We slide down
 * the limb then scale block&rarr;model with a Y flip. The exact offsets almost certainly want a visual nudge
 * in-game &mdash; adjust {@link #ARM_DOWN}/{@link #LEG_DOWN}/{@link #SCALE} (and the first-person offset in
 * {@code TourniquetArmRenderer}).</p>
 */
public final class TourniquetLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /**
     * Worn-tourniquet texture; shared with the first-person arm renderer.
     */
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(WFMedical.MOD_ID, "textures/entity/tourniquet.png");
    private static final ResourceLocation MODEL_LOC =
            new ResourceLocation(WFMedical.MOD_ID, "models/entity/tourniquet.obj");

    /**
     * OBJ block units -> entity model units (1/16 block). Y is negated to flip OBJ Y-up into model Y-down.
     */
    public static final float SCALE = 16.0F;
    /**
     * Model units down the arm (from the shoulder pivot toward the hand) to seat the band. Tunable.
     */
    private static final double ARM_DOWN = 6.0;
    /**
     * Model units down the leg (from the hip pivot toward the foot) to seat the band. Tunable.
     */
    private static final double LEG_DOWN = 7.0;

    private static ObjModel cachedModel;
    private static boolean loadFailed;

    public TourniquetLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /**
     * The shared tourniquet mesh, loaded once (also used by the first-person arm renderer). Null if it failed.
     */
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
        renderOn(mask, LimbType.RIGHT_ARM, model.rightArm, ARM_DOWN, pose, vc, light, overlay, m);
        renderOn(mask, LimbType.LEFT_ARM, model.leftArm, ARM_DOWN, pose, vc, light, overlay, m);
        renderOn(mask, LimbType.RIGHT_LEG, model.rightLeg, LEG_DOWN, pose, vc, light, overlay, m);
        renderOn(mask, LimbType.LEFT_LEG, model.leftLeg, LEG_DOWN, pose, vc, light, overlay, m);
    }

    private static void renderOn(int mask, LimbType limb, ModelPart part, double down, PoseStack pose,
                                 VertexConsumer vc, int light, int overlay, ObjModel m) {
        if ((mask & (1 << limb.ordinal())) == 0) {
            return;
        }
        pose.pushPose();
        part.translateAndRotate(pose);       // into the limb's local model space (follows the animation)
        pose.translate(0.0, down, 0.0);       // slide down the limb toward the hand/foot
        pose.scale(SCALE, -SCALE, SCALE);     // block units -> model units, OBJ Y-up -> model Y-down
        m.render(pose, vc, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }
}
