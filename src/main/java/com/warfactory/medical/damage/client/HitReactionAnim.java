package com.warfactory.medical.damage.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.damage.HitClips;
import com.warfactory.medical.damage.HitReactionPacket;
import com.warfactory.medical.damage.Stagger;
import com.wf.wflib.xray.api.XrScreen;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The body half of {@link HitReactionPacket}, as a PlayerAnimator layer over every player model (soldiers
 * included): xray-monolith's own stalker clips ({@link HitClips}), picked as the engine picks them, by where the
 * hit came from ({@code hitfront/hitback/hitf_left/hitf_right}, shoulder turn) and, for a stagger, by body part
 * ({@code critical_hit_head/torso/hand/legs}). The local player also gets the engine's camera clips.
 */
public final class HitReactionAnim {

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "hit_reaction");

    private HitReactionAnim() {
    }

    /** Client mod bus. */
    public static void register(IEventBus modBus) {
        if (!ModList.get().isLoaded("playeranimator")) {
            return;
        }
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() ->
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 120, p -> new ModifierLayer<>())));
    }

    /** Client main thread. */
    @SuppressWarnings("unchecked")
    public static void play(HitReactionPacket p) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(p.entityId());
        if (!(e instanceof AbstractClientPlayer player) || !ModList.get().isLoaded("playeranimator")) {
            return;
        }
        if (PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER) instanceof ModifierLayer<?> raw) {
            ((ModifierLayer<IAnimation>) raw).setAnimation(new Reaction(p));
        }
        if (p.stagger() && player == mc.player) {
            camera(p);
        }
    }

    /** The actor's own view: head shots {@code head_shot.anm}, else the {@code hit_*} clip of the side it came from. */
    private static void camera(HitReactionPacket p) {
        String name;
        if (p.zone() == Stagger.Zone.HEAD) {
            name = "head_shot";
        } else {
            String fb = p.front() > 0.4F ? "front" : p.front() < -0.4F ? "back" : "";
            String lr = p.side() > 0.4F ? "right" : p.side() < -0.4F ? "left" : "";
            name = "hit_" + (fb.isEmpty() ? lr.isEmpty() ? "front" : lr : lr.isEmpty() ? fb : fb + "_" + lr);
        }
        XrScreen.camera(XrScreen.anm("wflib", name), Math.max(0.4F, p.strength()));
    }

    /** Parts PlayerAnimator names, as {@link HitClips} keys them. */
    private static final String[] PARTS = {"torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg"};
    /** Engine: anim_channel_factor 3 over power_factor 2 on the flinch channel. */
    private static final float FLINCH_GAIN = 1.5F;
    private static final float MAX_WEIGHT = 1.6F;
    private static final float MAX_BEND = 1.05F;
    /** The clip is a stalker skeleton collapsing; the player model has no spine, neck or knees to follow it that far. */
    private static final java.util.Map<String, Float> MAX_TURN = java.util.Map.of(
            "torso", (float) Math.toRadians(60), "head", (float) Math.toRadians(55), "rightArm", (float) Math.toRadians(75),
            "leftArm", (float) Math.toRadians(75), "rightLeg", (float) Math.toRadians(45), "leftLeg", (float) Math.toRadians(45));
    /** Blocks the hips may drop (y, down) and drift (x, z) in a stagger. */
    private static final float MAX_DROP = 0.32F;
    private static final float MAX_DRIFT = 0.12F;
    /** Share of a clip's end it eases out over, so a clip that finishes off-rest still lands back at the pose. */
    private static final float EASE_OUT = 0.12F;

    /** One clip playing at a weight. */
    private record Layer(HitClips.Clip clip, float weight, float speed) {
        /** Clip frame at {@code seconds} since the hit, or -1 once it has played out. */
        float frame(float seconds) {
            float f = seconds * HitClips.FPS * speed;
            return f >= clip.frames() - 1 ? -1.0F : f;
        }

        float easedWeight(float frame) {
            float left = (clip.frames() - 1 - frame) / (clip.frames() - 1) / EASE_OUT;
            return weight * (left >= 1.0F ? 1.0F : left * left * (3.0F - 2.0F * left));
        }
    }

    /** The engine's flinch picks, {@code PlayHitMotion}: the side, the front/back, a shoulder turn and the legs' dip. */
    private static void flinch(java.util.List<Layer> out, HitReactionPacket p, float gain) {
        float g = Math.min(MAX_WEIGHT, p.strength() * FLINCH_GAIN * gain);
        float lateral = p.side() * 2.0F;
        if (Math.abs(lateral) > 0.2F) {
            out.add(new Layer(HitClips.get(lateral > 0 ? "hit_right" : "hit_left"), g * Math.min(1.0F, Math.abs(lateral)), 1.0F));
        }
        float depth = p.front() * 2.0F;
        out.add(new Layer(HitClips.get(depth > 0 ? "hit_front" : "hit_back"), g * Math.min(1.0F, Math.abs(depth)), 1.0F));
        out.add(new Layer(HitClips.get(p.side() > 0 ? "turn_right" : "turn_left"), g, 1.0F));
        out.add(new Layer(HitClips.get(p.side() > 0 ? "down_r" : "down_l"), Math.min(1.0F, g), 1.0F));
    }

    /** The hit's clips playing on a player model, sampled each frame into one rotation per part. */
    private static final class Reaction implements IAnimation {
        private final java.util.List<Layer> layers = new java.util.ArrayList<>();
        private final int total;
        private int tick;
        private float delta;
        private final java.util.Map<String, Quaternionf> rotation = new java.util.HashMap<>();
        private final float[] offset = new float[3];
        private final Quaternionf swing = new Quaternionf();

        Reaction(HitReactionPacket hit) {
            flinch(layers, hit, hit.stagger() ? 0.6F : 1.0F);
            if (hit.stagger()) {
                layers.add(new Layer(HitClips.get(HitClips.staggerClip(hit.zone(), hit.variant())), 1.0F, HitClips.STAGGER_SPEED));
            }
            int longest = 0;
            for (Layer l : layers) {
                longest = Math.max(longest, Math.round(l.clip().frames() / HitClips.FPS / l.speed() * 20.0F));
            }
            total = longest;
        }

        @Override
        public void tick() {
            tick++;
        }

        @Override
        public boolean isActive() {
            return tick < total;
        }

        @Override
        public void setupAnim(float tickDelta) {
            delta = tickDelta;
            float seconds = (tick + delta) / 20.0F;
            for (String part : PARTS) {
                rotation.put(part, new Quaternionf());
            }
            offset[0] = offset[1] = offset[2] = 0.0F;
            for (Layer l : layers) {
                float f = l.frame(seconds);
                if (f < 0.0F) {
                    continue;
                }
                float w = l.easedWeight(f);
                int k = (int) f;
                float a = f - k;
                for (String part : PARTS) {
                    Quaternionf[] keys = l.clip().parts().get(part);
                    Quaternionf q = new Quaternionf(keys[k]).slerp(keys[Math.min(k + 1, keys.length - 1)], a);
                    rotation.get(part).mul(new Quaternionf().slerp(q, w));
                }
                float[] p0 = l.clip().pelvis()[k];
                float[] p1 = l.clip().pelvis()[Math.min(k + 1, l.clip().frames() - 1)];
                for (int i = 0; i < 3; i++) {
                    offset[i] += (p0[i] + (p1[i] - p0[i]) * a) * w;
                }
            }
            for (String part : PARTS) {
                hold(rotation.get(part), MAX_TURN.get(part));
            }
            offset[0] = Math.max(-MAX_DRIFT, Math.min(MAX_DRIFT, offset[0] * 0.5F));
            offset[1] = Math.max(-0.05F, Math.min(MAX_DROP, offset[1]));
            offset[2] = Math.max(-MAX_DRIFT, Math.min(MAX_DRIFT, offset[2] * 0.5F));
            // PlayerAnimator leans the whole upper body about the waist as a "bend" (head and arms ride in it) and turns
            // the torso part about the spine; so split the torso's world rotation into a tilt and a twist.
            Quaternionf torso = rotation.get("torso");
            Quaternionf twist = new Quaternionf(0.0F, torso.y, 0.0F, torso.w);
            if (twist.lengthSquared() < 1.0E-8F) {
                twist.identity();
            } else {
                twist.normalize();
            }
            swing.set(torso).mul(new Quaternionf(twist).invert());
            rotation.put("torso", twist);
            Quaternionf inverse = new Quaternionf(swing).invert();
            for (String part : new String[] {"head", "rightArm", "leftArm"}) {
                rotation.put(part, new Quaternionf(inverse).mul(rotation.get(part)));
            }
        }

        /** {@code q} turned no further than {@code max} radians from rest. */
        private static void hold(Quaternionf q, float max) {
            float angle = 2.0F * (float) Math.acos(Math.min(1.0F, Math.abs(q.w)));
            if (angle > max) {
                q.set(new Quaternionf().slerp(q.w < 0 ? new Quaternionf(-q.x, -q.y, -q.z, -q.w) : q, max / angle));
            }
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value) {
            if (type == TransformType.POSITION && part.equals("body")) {
                // PlayerAnimator offsets are in blocks, y up.
                return new Vec3f(value.getX() + offset[0], value.getY() - offset[1], value.getZ() - offset[2]);
            }
            if (type == TransformType.BEND && part.equals("torso")) {
                float angle = 2.0F * (float) Math.atan2(Math.sqrt(swing.x * swing.x + swing.z * swing.z), swing.w);
                if (angle > Math.PI) {
                    angle -= 2.0F * (float) Math.PI;
                }
                float len = (float) Math.sqrt(swing.x * swing.x + swing.z * swing.z);
                if (len < 1.0E-5F) {
                    return value;
                }
                // PlayerAnimator's bend: a turn of {@code bend} about the horizontal axis at {@code axis} from +x.
                // Past ~60 degrees the player model's body lifts off the hips (it has no spine), so the deepest folds are held there.
                return new Vec3f((float) Math.atan2(-swing.z / len, swing.x / len), Math.max(-MAX_BEND, Math.min(MAX_BEND, angle)), value.getZ());
            }
            Quaternionf delta = type == TransformType.ROTATION ? rotation.get(part) : null;
            if (delta == null) {
                return value;
            }
            // The clip's world delta rides on whatever pose a lower layer (the gun hold) already gave the part.
            Quaternionf q = new Quaternionf(delta).mul(new Quaternionf().rotationZYX(value.getZ(), value.getY(), value.getX()));
            Vector3f e = q.getEulerAnglesZYX(new Vector3f());
            return new Vec3f(e.x, e.y, e.z);
        }
    }
}
