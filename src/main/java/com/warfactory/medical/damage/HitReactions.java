package com.warfactory.medical.damage;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.limb.LimbType;
import com.wf.wflib.armor.api.ArmorOutcome;
import com.wf.wflib.soldier.api.SoldierPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hit reactions after xray-monolith: every hit flinches the victim's body from the side it came from
 * ({@code character_hit_animations}); enough impact in a short time staggers it ({@link Stagger}): slowed, shoved,
 * and a soldier's aim kicked off the target. Clients animate from {@link HitReactionPacket}.
 */
public final class HitReactions {

    private static final ResourceLocation SLOW_ID = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "stagger");
    private static final float LEG_EXTRA_SLOW = 0.3F;
    private static final double MAX_SLOW = 0.9D;
    private static final double SHOVE = 0.12D;
    private static final float AIM_KICK_DEG = 4.0F;
    /** Of the incoming amount, what armour that stops a round still hands the body as momentum. */
    private static final float ARMOR_MOMENTUM = 0.4F;

    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();

    private static final class State {
        final Stagger.Meter meter = new Stagger.Meter();
        long staggerUntil;
    }

    private HitReactions() {
    }

    /** A non-fatal hit on a conscious casualty. {@code through}: energy past armour into the wound. */
    static void onHit(ServerPlayer victim, DamageSource src, LimbType limb, ArmorOutcome armor, float through,
                      float amount) {
        if (!MedicalConfig.hitReactions()) {
            return;
        }
        float impact = through + (armor == ArmorOutcome.THROUGH ? 0.0F : amount * ARMOR_MOMENTUM);
        if (impact <= 0.0F) {
            return;
        }
        Stagger.Zone zone = Stagger.Zone.of(limb);
        long now = victim.level().getGameTime();
        State st = STATES.computeIfAbsent(victim, v -> new State());
        float jitter = 0.75F + 0.5F * victim.getRandom().nextFloat();
        // The engine picks among a part's stagger clips by critical_wound_weights (100, 50).
        int variant = victim.getRandom().nextInt(3) == 0 ? 1 : 0;
        boolean stagger = st.meter.add(Stagger.power(impact, zone, jitter), now, (float) MedicalConfig.staggerThreshold(),
                (float) MedicalConfig.staggerDecayPerSecond() / 20.0F, HitClips.staggerTicks(zone, variant));
        float strength = Mth.clamp(impact / Stagger.REFERENCE_ENERGY, 0.2F, 1.0F);

        Vec3 from = src == null ? null : src.getSourcePosition();
        double dx = from == null ? 0.0D : from.x - victim.getX();
        double dz = from == null ? 0.0D : from.z - victim.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        float front = 1.0F;
        float side = 0.0F;
        if (len > 1.0E-3D) {
            dx /= len;
            dz /= len;
            double yaw = Math.toRadians(victim.getYRot());
            front = (float) (dx * -Math.sin(yaw) + dz * Math.cos(yaw));
            side = (float) (dx * -Math.cos(yaw) + dz * -Math.sin(yaw));
        }
        if (com.warfactory.medical.body.BalanceLog.on()) {
            WFMedical.LOGGER.info("[balance] tick={} victim={} reaction zone={} impact={} stagger={}", now,
                    victim.getGameProfile().getName(), zone, impact, stagger);
        }
        if (stagger) {
            stagger(victim, st, zone, variant, strength, now, dx, dz, len > 1.0E-3D);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim,
                new HitReactionPacket(victim.getId(), zone, stagger, front, side, strength, variant));
    }

    private static void stagger(ServerPlayer victim, State st, Stagger.Zone zone, int variant, float strength, long now,
                                double awayX, double awayZ, boolean hasDir) {
        st.staggerUntil = now + HitClips.staggerTicks(zone, variant);
        AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW_ID);
            double slow = Math.min(MAX_SLOW, MedicalConfig.staggerSlow() + (zone.isLeg() ? LEG_EXTRA_SLOW : 0.0F));
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, -slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (hasDir && !zone.isLeg()) {
            // Pushed away from the shooter (the packet direction points at him).
            victim.push(-awayX * SHOVE * strength, 0.0D, -awayZ * SHOVE * strength);
            victim.hurtMarked = true;
        }
        if (victim instanceof SoldierPlayer) {
            // A soldier has no mouse for the camera kick to move, so move its aim itself.
            float kick = AIM_KICK_DEG * strength * (zone == Stagger.Zone.HEAD ? 1.5F : 1.0F);
            victim.setXRot(Mth.clamp(victim.getXRot() - kick, -90.0F, 90.0F));
            victim.setYRot(victim.getYRot() + (victim.getRandom().nextFloat() - 0.5F) * 2.0F * kick);
        }
    }

    public static boolean isStaggered(ServerPlayer player) {
        State st = STATES.get(player);
        return st != null && player.level().getGameTime() < st.staggerUntil;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Map.Entry<ServerPlayer, State> e : STATES.entrySet()) {
            State st = e.getValue();
            ServerPlayer player = e.getKey();
            if (st.staggerUntil != 0L && player.level().getGameTime() >= st.staggerUntil) {
                st.staggerUntil = 0L;
                AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.removeModifier(SLOW_ID);
                }
            }
        }
    }
}
