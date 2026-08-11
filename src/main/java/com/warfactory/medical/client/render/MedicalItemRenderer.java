package com.warfactory.medical.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.warfactory.medical.WFMedical;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Map;

public final class MedicalItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final float BASE_SCALE = 3.5F;

    private static MedicalItemRenderer instance;
    private final Map<String, ObjModel> models = new HashMap<>();
    private final Map<String, Boolean> missing = new HashMap<>();

    private MedicalItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    public static MedicalItemRenderer get() {
        if (instance == null) {
            instance = new MedicalItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffer, int light, int overlay) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        String name = id.getPath();
        ObjModel m = model(name);
        if (m == null) {
            return;
        }
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "textures/item/" + name + ".png");
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.scale(BASE_SCALE, BASE_SCALE, BASE_SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(tex));
        m.render(pose, vc, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    private ObjModel model(String name) {
        if (Boolean.TRUE.equals(missing.get(name))) {
            return null;
        }
        ObjModel m = models.get(name);
        if (m == null) {
            m = ObjModel.load(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "models/item/" + name + ".obj"));
            if (m == null) {
                missing.put(name, Boolean.TRUE);
                return null;
            }
            models.put(name, m);
        }
        return m;
    }
}
