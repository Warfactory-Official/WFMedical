package com.warfactory.medical.damage;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.joml.Quaternionf;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The stalker skeleton's hit clips, baked from xray-monolith's {@code stalker_animation.omf} by
 * {@code tools/omf/bake.py}: the {@code hit*17} flinch clips of {@code character_hit_animations} and the
 * {@code norm_2_critical_hit_*} stagger clips. Per player-model part, the world rotation of that bone chain against
 * the clip's first frame, 30 samples per second. Shared by the server (stagger length) and the client (the motion).
 */
public final class HitClips {

    public static final float FPS = 30.0F;
    /** GAMMA's "NPCs Faster Reactions" plays the critical-hit clips at this speed. */
    public static final float STAGGER_SPEED = 1.65F;

    /** One clip: {@code parts[part][frame]} rotation, {@code pelvis[frame]} offset in blocks (x left, y down, z back). */
    public record Clip(int frames, Map<String, Quaternionf[]> parts, float[][] pelvis) {
    }

    private static final Map<String, Clip> CLIPS = load();

    private HitClips() {
    }

    public static Clip get(String name) {
        Clip clip = CLIPS.get(name);
        if (clip == null) {
            throw new IllegalArgumentException("no hit clip " + name);
        }
        return clip;
    }

    /** The stagger clip of a body part, picking among its variants like the engine's {@code critical_wound_weights} (100, 50). */
    public static String staggerClip(Stagger.Zone zone, int variant) {
        return switch (zone) {
            case HEAD -> "head_0";
            case TORSO -> variant == 0 ? "torso_0" : "torso_1";
            case ARM_L -> "arm_l_0";
            case ARM_R -> "arm_r_0";
            case LEG_L -> "leg_l_0";
            case LEG_R -> "leg_r_0";
        };
    }

    /** Server tick length of a stagger. */
    public static int staggerTicks(Stagger.Zone zone, int variant) {
        return Math.round(get(staggerClip(zone, variant)).frames() / FPS / STAGGER_SPEED * 20.0F);
    }

    private static Map<String, Clip> load() {
        try (InputStream in = HitClips.class.getResourceAsStream("/wfmedical_hit_clips.json")) {
            if (in == null) {
                throw new IllegalStateException("wfmedical_hit_clips.json missing from the mod jar");
            }
            JsonObject clips = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class)
                    .getAsJsonObject("clips");
            Map<String, Clip> out = new HashMap<>();
            clips.entrySet().forEach(e -> {
                JsonObject c = e.getValue().getAsJsonObject();
                int frames = c.get("frames").getAsInt();
                Map<String, Quaternionf[]> parts = new HashMap<>();
                c.getAsJsonObject("parts").entrySet().forEach(p -> {
                    var arr = p.getValue().getAsJsonArray();
                    Quaternionf[] q = new Quaternionf[frames];
                    for (int i = 0; i < frames; i++) {
                        var k = arr.get(i).getAsJsonArray();
                        q[i] = new Quaternionf(k.get(0).getAsFloat(), k.get(1).getAsFloat(), k.get(2).getAsFloat(), k.get(3).getAsFloat());
                    }
                    parts.put(p.getKey(), q);
                });
                float[][] pelvis = new float[frames][3];
                var arr = c.getAsJsonArray("pelvis");
                for (int i = 0; i < frames; i++) {
                    for (int j = 0; j < 3; j++) {
                        pelvis[i][j] = arr.get(i).getAsJsonArray().get(j).getAsFloat();
                    }
                }
                out.put(e.getKey(), new Clip(frames, parts, pelvis));
            });
            return out;
        } catch (IOException | JsonParseException ex) {
            throw new IllegalStateException("cannot read wfmedical_hit_clips.json", ex);
        }
    }
}
