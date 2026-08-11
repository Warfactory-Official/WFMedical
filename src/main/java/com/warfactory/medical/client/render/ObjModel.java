package com.warfactory.medical.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.warfactory.medical.WFMedical;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ObjModel {

    private final float[][] positions;
    private final float[][] uvs;
    private final float[][] normals;
    private final int[][][] faces;

    private ObjModel(float[][] positions, float[][] uvs, float[][] normals, int[][][] faces) {
        this.positions = positions;
        this.uvs = uvs;
        this.normals = normals;
        this.faces = faces;
    }

    public static ObjModel load(ResourceLocation loc) {
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (res.isEmpty()) {
                WFMedical.LOGGER.error("[{}] OBJ model not found: {}", WFMedical.MOD_ID, loc);
                return null;
            }
            List<float[]> pos = new ArrayList<>();
            List<float[]> uv = new ArrayList<>();
            List<float[]> norm = new ArrayList<>();
            List<int[][]> face = new ArrayList<>();
            try (BufferedReader reader = res.get().openAsReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.charAt(0) == '#') {
                        continue;
                    }
                    String[] tok = line.split("\\s+");
                    switch (tok[0]) {
                        case "v" -> pos.add(new float[]{parse(tok, 1), parse(tok, 2), parse(tok, 3)});
                        case "vt" -> uv.add(new float[]{parse(tok, 1), 1.0F - parse(tok, 2)});
                        case "vn" -> norm.add(new float[]{parse(tok, 1), parse(tok, 2), parse(tok, 3)});
                        case "f" -> face.add(parseFace(tok, pos.size(), uv.size(), norm.size()));
                        default -> {
                        }
                    }
                }
            }
            float[][] positions = pos.toArray(new float[0][]);
            recenter(positions);
            return new ObjModel(positions, uv.toArray(new float[0][]), norm.toArray(new float[0][]),
                    face.toArray(new int[0][][]));
        } catch (Exception e) {
            WFMedical.LOGGER.error("[{}] Failed to load OBJ model {}", WFMedical.MOD_ID, loc, e);
            return null;
        }
    }

    private static float parse(String[] tok, int i) {
        return i < tok.length ? Float.parseFloat(tok[i]) : 0.0F;
    }

    private static int[][] parseFace(String[] tok, int nPos, int nUv, int nNorm) {
        int[][] verts = new int[tok.length - 1][3];
        for (int i = 1; i < tok.length; i++) {
            String[] parts = tok[i].split("/");
            verts[i - 1][0] = index(parts, 0, nPos);
            verts[i - 1][1] = index(parts, 1, nUv);
            verts[i - 1][2] = index(parts, 2, nNorm);
        }
        return verts;
    }

    private static int index(String[] parts, int slot, int count) {
        if (slot >= parts.length || parts[slot].isEmpty()) {
            return -1;
        }
        int v = Integer.parseInt(parts[slot]);
        return v < 0 ? count + v : v - 1;
    }

    private static void recenter(float[][] positions) {
        if (positions.length == 0) {
            return;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (float[] p : positions) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxZ = Math.max(maxZ, p[2]);
        }
        float cx = (minX + maxX) * 0.5F;
        float cy = (minY + maxY) * 0.5F;
        float cz = (minZ + maxZ) * 0.5F;
        for (float[] p : positions) {
            p[0] -= cx;
            p[1] -= cy;
            p[2] -= cz;
        }
    }

    public void render(PoseStack pose, VertexConsumer vc, int light, int overlay,
                       float red, float green, float blue, float alpha) {
        Matrix4f mat = pose.last().pose();
        PoseStack.Pose nm = pose.last();
        for (int[][] face : faces) {
            if (face.length == 4) {
                for (int[] v : face) {
                    emit(mat, nm, vc, v, light, overlay, red, green, blue, alpha);
                }
            } else if (face.length == 3) {
                emit(mat, nm, vc, face[0], light, overlay, red, green, blue, alpha);
                emit(mat, nm, vc, face[1], light, overlay, red, green, blue, alpha);
                emit(mat, nm, vc, face[2], light, overlay, red, green, blue, alpha);
                emit(mat, nm, vc, face[2], light, overlay, red, green, blue, alpha);
            } else {
                for (int i = 1; i + 1 < face.length; i++) {
                    emit(mat, nm, vc, face[0], light, overlay, red, green, blue, alpha);
                    emit(mat, nm, vc, face[i], light, overlay, red, green, blue, alpha);
                    emit(mat, nm, vc, face[i + 1], light, overlay, red, green, blue, alpha);
                    emit(mat, nm, vc, face[i + 1], light, overlay, red, green, blue, alpha);
                }
            }
        }
    }

    private void emit(Matrix4f mat, PoseStack.Pose nm, VertexConsumer vc, int[] v, int light, int overlay,
                      float red, float green, float blue, float alpha) {
        float[] p = positions[v[0]];
        float u = v[1] >= 0 ? uvs[v[1]][0] : 0.0F;
        float w = v[1] >= 0 ? uvs[v[1]][1] : 0.0F;
        float nx = 0.0F, ny = 1.0F, nz = 0.0F;
        if (v[2] >= 0) {
            float[] n = normals[v[2]];
            nx = n[0];
            ny = n[1];
            nz = n[2];
        }
        vc.addVertex(mat, p[0], p[1], p[2]).setColor(red, green, blue, alpha).setUv(u, w)
                .setOverlay(overlay).setLight(light).setNormal(nm, nx, ny, nz);
    }
}
