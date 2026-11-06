package com.warfactory.medical.ragdoll.client;

import com.jme3.math.Quaternion;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.norwood.ahf.client.body.AhfBody;
import com.warfactory.medical.ragdoll.MedicalRagdollEntity;
import de.maxhenkel.corpse.entities.CorpseEntity;
import de.maxhenkel.corpse.entities.DummyPlayer;
import de.sarocesch.sarosragdoll.ragdoll.physics.EnumRagdollBodyPart;
import de.sarocesch.sarosragdoll.ragdoll.physics.SynchronizedRigidBodyTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/** Physics supplies the six part poses; native layers keep armor extensions and held item renderers. */
public final class MedicalRagdollRenderer extends EntityRenderer<MedicalRagdollEntity> {

    private final PlayerModel<AbstractClientPlayer> wide;
    private final PlayerModel<AbstractClientPlayer> slim;
    private PlayerModel<AbstractClientPlayer> model;
    private final HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>, HumanoidModel<AbstractClientPlayer>> armor;
    private final ItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> hands;
    private final Map<CorpseEntity, DummyPlayer> corpses = new WeakHashMap<>();
    private final com.jme3.math.Vector3f position = new com.jme3.math.Vector3f();
    private final Quaternion rotation = new Quaternion();
    private final Quaternionf orientation = new Quaternionf();
    private final Vector3f pivot = new Vector3f();

    public MedicalRagdollRenderer(EntityRendererProvider.Context context) {
        super(context);
        wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        model = wide;
        RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent = new RenderLayerParent<>() {
            @Override
            public PlayerModel<AbstractClientPlayer> getModel() {
                return model;
            }

            @Override
            public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
                return skin(player).texture();
            }
        };
        armor = new HumanoidArmorLayer<>(parent,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager());
        hands = new ItemInHandLayer<>(parent, context.getItemInHandRenderer());
    }

    public static boolean ready(MedicalRagdollEntity entity) {
        for (EnumRagdollBodyPart part : EnumRagdollBodyPart.values()) {
            if (entity.getPartTransform(part) == null) {
                return false;
            }
        }
        return !entity.isRemoved() && !entity.isInvisible();
    }

    @Override
    public void render(MedicalRagdollEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        if (!ready(entity)) {
            return;
        }
        AbstractClientPlayer wearer = wearer(entity);
        if (wearer == null) {
            return;
        }
        PlayerSkin skin = skin(wearer);
        model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        model.setAllVisible(true);
        model.young = false;
        model.riding = false;
        model.crouching = false;
        model.swimAmount = 0;
        model.attackTime = 0;
        model.hat.visible = wearer.isModelPartShown(PlayerModelPart.HAT);
        model.jacket.visible = wearer.isModelPartShown(PlayerModelPart.JACKET);
        model.leftSleeve.visible = wearer.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = wearer.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        model.leftPants.visible = wearer.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = wearer.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        double x = entity.xOld + (entity.getX() - entity.xOld) * partialTick;
        double y = entity.yOld + (entity.getY() - entity.yOld) * partialTick;
        double z = entity.zOld + (entity.getZ() - entity.zOld) * partialTick;
        for (EnumRagdollBodyPart part : EnumRagdollBodyPart.values()) {
            ModelPart bone = bone(part);
            SynchronizedRigidBodyTransform transform = entity.getPartTransform(part);
            transform.getInterpolatedPosition(partialTick, position);
            transform.getInterpolatedRotation(partialTick, rotation);
            orientation.set(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW()).rotateX((float) Math.PI);
            poseBone(bone, part, orientation, (float) (position.x - x), (float) (position.y - y),
                    (float) (position.z - z), pivot);
        }
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        AhfBody.clear();
        try {
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture())), light,
                    OverlayTexture.NO_OVERLAY);
            armor.render(pose, buffers, light, wearer, 0, 0, partialTick, entity.tickCount + partialTick, 0, 0);
            hands.render(pose, buffers, light, wearer, 0, 0, partialTick, entity.tickCount + partialTick, 0, 0);
        } finally {
            AhfBody.clear();
        }
    }

    private AbstractClientPlayer wearer(MedicalRagdollEntity ragdoll) {
        Entity target = ragdoll.target();
        if (target instanceof AbstractClientPlayer player) {
            return player;
        }
        if (target instanceof CorpseEntity corpse && !corpse.isSkeleton()) {
            DummyPlayer dummy = corpses.computeIfAbsent(corpse, c -> new DummyPlayer(
                    Minecraft.getInstance().level,
                    new GameProfile(c.getCorpseUUID().orElse(c.getUUID()), c.getCorpseName()),
                    c.getEquipment(), c.getCorpseModel()));
            if (de.maxhenkel.corpse.Main.SERVER_CONFIG.renderEquipment.get()) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    dummy.setItemSlot(slot, corpse.getEquipment().get(slot.ordinal()));
                }
            }
            return dummy;
        }
        return null;
    }

    private static PlayerSkin skin(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        PlayerSkin skin = player.getSkin();
        return !skin.texture().equals(net.minecraft.client.resources.DefaultPlayerSkin.get(player.getUUID()).texture())
                ? skin : mc.getSkinManager().getInsecureSkin(player.getGameProfile());
    }

    static void poseBone(ModelPart bone, EnumRagdollBodyPart part, Quaternionf orientation,
                         float x, float y, float z, Vector3f scratch) {
        bone.resetPose();
        // Match Saro's rigid-body centers, including each vanilla part's rest pivot.
        scratch.set(bone.x / 16, bone.y / 16, bone.z / 16);
        switch (part) {
            case HEAD -> scratch.add(0, .25F, 0);
            case CHEST -> scratch.add(0, -.375F, 0);
            case RIGHT_ARM -> scratch.add(.369F, -.375F, 0);
            case LEFT_ARM -> scratch.add(-.369F, -.375F, 0);
            case RIGHT_LEG -> scratch.add(.125F, -1.125F, 0);
            case LEFT_LEG -> scratch.add(-.1F, -1.125F, 0);
        }
        orientation.transform(scratch);
        bone.setPos((x + scratch.x) * 16, (y + scratch.y) * 16, (z + scratch.z) * 16);
        double qx = orientation.x, qy = orientation.y, qz = orientation.z, qw = orientation.w;
        double norm = qx * qx + qy * qy + qz * qz + qw * qw;
        double sinPitch = Math.max(-1, Math.min(1, 2 * (qw * qy - qz * qx) / norm));
        // ModelPart composes Rz Ry Rx; JOML's quaternion Euler accessors do not round-trip that convention.
        if (Math.abs(sinPitch) > .99999999) {
            bone.setRotation(0, (float) Math.copySign(Math.PI / 2, sinPitch),
                    (float) Math.atan2(2 * (qw * qz - qx * qy), norm - 2 * (qx * qx + qz * qz)));
        } else {
            bone.setRotation((float) Math.atan2(2 * (qw * qx + qy * qz), norm - 2 * (qx * qx + qy * qy)),
                    (float) Math.asin(sinPitch),
                    (float) Math.atan2(2 * (qw * qz + qx * qy), norm - 2 * (qy * qy + qz * qz)));
        }
    }

    private ModelPart bone(EnumRagdollBodyPart part) {
        return switch (part) {
            case HEAD -> model.head;
            case CHEST -> model.body;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
        };
    }

    @Override
    public ResourceLocation getTextureLocation(MedicalRagdollEntity entity) {
        AbstractClientPlayer player = wearer(entity);
        return player == null ? net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture() : skin(player).texture();
    }
}
