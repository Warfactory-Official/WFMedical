package com.warfactory.medical.damage.client;

import com.warfactory.medical.WFMedical;
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

/**
 * The body half of {@link HitReactionPacket}, as a PlayerAnimator layer over every player model (soldiers
 * included): xray-monolith's hit animations are additive clips picked by where the hit came from
 * ({@code hitfront/hitback/hitf_left/hitf_right}, shoulder turn) and, for a stagger, by body part
 * ({@code critical_hit_head/torso/hand/legs}). Here the same set is a damped kick on the matching bones; the local
 * player also gets the engine's camera clips.
 */
public final class HitReactionAnim {

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "hit_reaction");
    private static final int FLINCH_TICKS = 8;
    private static final int STAGGER_EXTRA_TICKS = 8;
    private static final float ATTACK = 0.15F;
    private static final float DECAY = 3.2F;

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

    /** One kick: attack, then exponential decay. */
    private static final class Reaction implements IAnimation {
        private final HitReactionPacket hit;
        private final int total;
        private int tick;
        private float delta;

        Reaction(HitReactionPacket hit) {
            this.hit = hit;
            this.total = hit.stagger() ? hit.zone().ticks() + STAGGER_EXTRA_TICKS : FLINCH_TICKS;
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
        }

        private float envelope() {
            float s = Math.min(1.0F, (tick + delta) / total);
            return s < ATTACK ? s / ATTACK : (float) Math.exp(-DECAY * (s - ATTACK) / (1.0F - ATTACK));
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value) {
            if (type != TransformType.ROTATION) {
                return value;
            }
            float a = envelope() * hit.strength() * (hit.stagger() ? 1.0F : 0.45F);
            float f = hit.front();
            float s = hit.side();
            float x = 0.0F;
            float y = 0.0F;
            float z = 0.0F;
            // Rotation of the torso/head: back from a hit in front, forward from one behind, away from the side.
            switch (part) {
                case "torso" -> {
                    x = -0.40F * f * a;
                    z = -0.30F * s * a;
                    y = 0.25F * s * a;
                }
                case "head" -> {
                    float w = hit.zone() == Stagger.Zone.HEAD ? 2.0F : 0.7F;
                    x = -0.45F * f * a * w;
                    y = 0.35F * s * a * w;
                    z = -0.20F * s * a * w;
                }
                case "rightArm" -> {
                    float w = hit.zone() == Stagger.Zone.ARM_R ? 2.2F : hit.zone() == Stagger.Zone.TORSO ? 1.0F : 0.4F;
                    x = -0.55F * a * w;
                    z = 0.35F * a * w;
                }
                case "leftArm" -> {
                    float w = hit.zone() == Stagger.Zone.ARM_L ? 2.2F : hit.zone() == Stagger.Zone.TORSO ? 1.0F : 0.4F;
                    x = -0.55F * a * w;
                    z = -0.35F * a * w;
                }
                case "rightLeg" -> x = leg(hit.zone() == Stagger.Zone.LEG_R, a);
                case "leftLeg" -> x = leg(hit.zone() == Stagger.Zone.LEG_L, a);
                default -> {
                    return value;
                }
            }
            return new Vec3f(value.getX() + x, value.getY() + y, value.getZ() + z);
        }

        /** The hit leg buckles forward, the other braces back; nothing for a hit elsewhere. */
        private float leg(boolean hitLeg, float a) {
            if (hit.zone().isLeg()) {
                return (hitLeg ? 0.9F : -0.4F) * a;
            }
            return 0.0F;
        }
    }
}
