package com.warfactory.medical.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.compat.OpenPersistenceCompat;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.damage.HitGeometry;
import com.warfactory.medical.core.damage.HitRegMode;
import com.warfactory.medical.core.damage.MedicalHitReg;
import com.warfactory.medical.core.damage.rig.HumanoidRig;
import com.warfactory.medical.core.damage.rig.Obb;
import com.warfactory.medical.core.damage.rig.RigTuning;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;


@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HitboxDebugRenderer {

    private static final double RANGE = 32.0;
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7}, // along X
            {0, 2}, {1, 3}, {4, 6}, {5, 7}, // along Y
            {0, 4}, {1, 5}, {2, 6}, {3, 7}  // along Z
    };
    public static boolean enabled = false;

    private HitboxDebugRenderer() {
    }

    /**
     * Flip the overlay on/off (called from the keybind poll).
     */
    public static void toggle() {
        enabled = !enabled;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        AABB area = new AABB(cam.x - RANGE, cam.y - RANGE, cam.z - RANGE,
                cam.x + RANGE, cam.y + RANGE, cam.z + RANGE);
        List<LivingEntity> targets = mc.level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e instanceof Player || OpenPersistenceCompat.isPersistentBody(e));
        if (targets.isEmpty()) {
            return;
        }

        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ps.last().pose();
        Matrix3f nrm = ps.last().normal();
        boolean regActive = MedicalConfig.hitRegistrationMode() != HitRegMode.OFF;
        for (LivingEntity e : targets) {
            //vanilla collision box
            AABB tight = e.getBoundingBox();
            LevelRenderer.renderLineBox(ps, vc, tight.minX, tight.minY, tight.minZ,
                    tight.maxX, tight.maxY, tight.maxZ, 0.55F, 0.55F, 0.55F, 0.6F);
            //Envelope hit-scan box (white)
            if (regActive) {
                AABB env = MedicalHitReg.registrationBox(e);
                LevelRenderer.renderLineBox(ps, vc, env.minX, env.minY, env.minZ,
                        env.maxX, env.maxY, env.maxZ, 1.0F, 1.0F, 1.0F, 0.4F);
            }
            float rigAlpha = HitGeometry.rigPoseSupported(e) ? 1.0F : 0.4F;
            renderRig(mat, nrm, vc, e, pt, rigAlpha);
        }
        ps.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void renderRig(Matrix4f mat, Matrix3f nrm, VertexConsumer vc, LivingEntity e, float pt, float alpha) {
        HumanoidRig.LocalRig rig = HumanoidRig.compute(e);
        double px = Mth.lerp(pt, e.xOld, e.getX());
        double py = Mth.lerp(pt, e.yOld, e.getY());
        double pz = Mth.lerp(pt, e.zOld, e.getZ());
        double yaw = Math.toRadians(Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot));
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);   // front = (-sin, 0, cos)
        double rx = -fz;
        double rz = fx;              // right = (-front.z, 0, front.x)
        // While tuning, spotlight the limb being edited: brighten it, fade the rest so the target reads clearly.
        LimbType hl = RigTuning.ACTIVE ? RigTuning.highlight : null;
        for (Obb obb : rig.all()) {
            float[] c = colorFor(obb.limb());
            float a = alpha;
            if (hl != null) {
                a = obb.limb() == hl ? Math.min(1.0F, alpha + 0.4F) : alpha * 0.3F;
            }
            drawObb(mat, nrm, vc, obb, px, py, pz, fx, fz, rx, rz, c[0], c[1], c[2], a);
        }
    }

    private static void drawObb(Matrix4f mat, Matrix3f nrm, VertexConsumer vc, Obb obb,
                                double px, double py, double pz,
                                double fx, double fz, double rx, double rz,
                                float red, float green, float blue, float alpha) {
        Vec3 c = obb.center();
        Vec3 ax = obb.axisX();
        Vec3 ay = obb.axisY();
        Vec3 az = obb.axisZ();
        double hx = obb.half().x;
        double hy = obb.half().y;
        double hz = obb.half().z;

        Vec3[] corners = new Vec3[8];
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    // Corner in entity-local coords
                    double lx = c.x + sx * hx * ax.x + sy * hy * ay.x + sz * hz * az.x;
                    double ly = c.y + sx * hx * ax.y + sy * hy * ay.y + sz * hz * az.y;
                    double lz = c.z + sx * hx * ax.z + sy * hy * ay.z + sz * hz * az.z;
                    //dd entity-local world: feet + lx*right + ly*up + lz*front.
                    double wx = px + lx * rx + lz * fx;
                    double wy = py + ly;
                    double wz = pz + lx * rz + lz * fz;
                    corners[cornerIndex(sx, sy, sz)] = new Vec3(wx, wy, wz);
                }
            }
        }
        for (int[] edge : EDGES) {
            line(mat, nrm, vc, corners[edge[0]], corners[edge[1]], red, green, blue, alpha);
        }
    }

    private static int cornerIndex(int sx, int sy, int sz) {
        return (sx > 0 ? 1 : 0) | (sy > 0 ? 2 : 0) | (sz > 0 ? 4 : 0);
    }

    private static void line(Matrix4f mat, Matrix3f nrm, VertexConsumer vc, Vec3 a, Vec3 b,
                             float red, float green, float blue, float alpha) {
        float dx = (float) (b.x - a.x);
        float dy = (float) (b.y - a.y);
        float dz = (float) (b.z - a.z);
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0e-6f) {
            dx /= len;
            dy /= len;
            dz /= len;
        }
        vc.vertex(mat, (float) a.x, (float) a.y, (float) a.z).color(red, green, blue, alpha)
                .normal(nrm, dx, dy, dz).endVertex();
        vc.vertex(mat, (float) b.x, (float) b.y, (float) b.z).color(red, green, blue, alpha)
                .normal(nrm, dx, dy, dz).endVertex();
    }

    private static float[] colorFor(LimbType limb) {
        return switch (limb) {
            case HEAD -> new float[]{1.0F, 0.2F, 0.2F};   // red
            case TORSO -> new float[]{0.2F, 1.0F, 0.3F};   // green
            case LEFT_ARM -> new float[]{0.2F, 0.8F, 1.0F};   // cyan
            case RIGHT_ARM -> new float[]{0.2F, 0.4F, 1.0F};   // blue
            case LEFT_LEG -> new float[]{1.0F, 0.9F, 0.2F};   // yellow
            default -> new float[]{1.0F, 0.5F, 0.1F};   // orange
        };
    }
}
