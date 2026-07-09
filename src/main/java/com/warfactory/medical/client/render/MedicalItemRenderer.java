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
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom in-world / GUI item renderer (a {@link BlockEntityWithoutLevelRenderer}) for medical items: draws
 * each item's OBJ model (via {@link ObjModel}) with the item's own texture. Wired to the items through
 * {@code IClientItemExtensions.getCustomRenderer()} on {@code MedicalItem}.
 *
 * <p>Only items whose baked model is {@code builtin/entity} actually route through here &mdash; datagen emits
 * that model for any item that ships a {@code models/item/&lt;name&gt;.obj}, and a flat sprite for the rest,
 * so an item without an OBJ renders vanilla and is never invisible. The OBJ (block units, re-centred on load)
 * is drawn around the origin at {@link #BASE_SCALE}; the model JSON's per-context display transforms do the
 * GUI/hand/ground posing. <b>{@link #BASE_SCALE} and the display transforms are tunable</b> and will want an
 * in-game visual nudge.</p>
 */
public final class MedicalItemRenderer extends BlockEntityWithoutLevelRenderer {

    /**
     * OBJ block-units -> item space. The OBJ spans ~0.28 block; this scales it up to roughly fill the [0,1]
     * block that the generated block-style display transforms expect, after which those transforms size it per
     * context. {@code 1 / 0.28 ~= 3.5} fills the block edge-to-edge. Tunable.
     */
    private static final float BASE_SCALE = 3.5F;

    private static MedicalItemRenderer instance;
    private final Map<String, ObjModel> models = new HashMap<>();
    private final Map<String, Boolean> missing = new HashMap<>();

    private MedicalItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /**
     * The shared singleton, created lazily on the client.
     */
    public static MedicalItemRenderer get() {
        if (instance == null) {
            instance = new MedicalItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffer, int light, int overlay) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        String name = id.getPath();
        ObjModel m = model(name);
        if (m == null) {
            return; // no OBJ (shouldn't happen: only builtin/entity items reach here) -> nothing to draw
        }
        ResourceLocation tex = new ResourceLocation(WFMedical.MOD_ID, "textures/item/" + name + ".png");
        pose.pushPose();
        // ItemRenderer has already applied the model's display transform plus a -0.5 block offset that pivots
        // around the block CENTRE. The OBJ is re-centred on its own origin (block units, ~0.28 block across), so
        // we translate to the block centre and scale it up to roughly fill the [0,1] block the generated
        // block-style display transforms expect. Without the centre translate the mesh renders off the pivot
        // and the display rotation flings it out of frame. OBJ Y-up matches item space, so NO Y flip here
        // (unlike TourniquetLayer, which renders in Y-down entity-model space and negates Y).
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
            m = ObjModel.load(new ResourceLocation(WFMedical.MOD_ID, "models/item/" + name + ".obj"));
            if (m == null) {
                missing.put(name, Boolean.TRUE);
                return null;
            }
            models.put(name, m);
        }
        return m;
    }
}
