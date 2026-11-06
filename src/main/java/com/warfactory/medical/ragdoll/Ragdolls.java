package com.warfactory.medical.ragdoll;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.api.MedicalState;
import de.maxhenkel.corpse.entities.CorpseEntity;
import de.sarocesch.sarosragdoll.ragdoll.physics.NativeInstaller;
import de.sarocesch.sarosragdoll.ragdoll.physics.PhysicsWorlds;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/** All inventory stays on the patient or native Corpse. A ragdoll contains only presentation and physics state. */
public final class Ragdolls {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, WFMedical.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<MedicalRagdollEntity>> TYPE = TYPES.register("ragdoll",
            () -> EntityType.Builder.<MedicalRagdollEntity>of(MedicalRagdollEntity::new, MobCategory.MISC)
                    .sized(1, 1).clientTrackingRange(10).updateInterval(1).build("wfmedical:ragdoll"));
    private static final java.util.Set<CorpseEntity> pending = new java.util.HashSet<>();
    private static boolean enabled;
    private static boolean warned;

    private Ragdolls() {}
    public static boolean enabled() { return enabled; }
    static void register(IEventBus modBus) { enabled = true; TYPES.register(modBus); }

    public static boolean isManaged(Player player) {
        return enabled && player.isAlive() && (MedicalState.isDowned(player) || MedicalState.isUnconscious(player))
                && !(MedicalConfig.effectImmuneInCreative() && (player.isCreative() || player.isSpectator()));
    }

    public static @Nullable MedicalRagdollEntity find(Entity target) {
        if (!enabled || target == null) return null;
        return target.level().getEntitiesOfClass(MedicalRagdollEntity.class, target.getBoundingBox().inflate(4),
                ragdoll -> !ragdoll.isRemoved() && target.getUUID().equals(ragdoll.targetUuid())).stream().findFirst().orElse(null);
    }

    static boolean physicsAvailable() {
        boolean available = NativeInstaller.isLoaded() && !PhysicsWorlds.isDisabled();
        if (!available && !warned) {
            warned = true;
            WFMedical.LOGGER.error("Ragdoll physics unavailable; keeping normal medical bodies and Corpse rendering");
        }
        return available;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!physicsAvailable()) { pending.clear(); return; }
        var corpses = java.util.List.copyOf(pending);
        pending.clear();
        for (CorpseEntity corpse : corpses) {
            if (!corpse.isRemoved() && corpse.level() instanceof ServerLevel level
                    && level.getEntity(corpse.getUUID()) == corpse && find(corpse) == null) spawn(corpse);
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (isManaged(player) && find(player) == null) spawn(player);
        }
    }

    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !ModList.get().isLoaded("corpse")) return;
        if (event.getEntity() instanceof CorpseEntity corpse && !corpse.isSkeleton()) {
            // Queue until the corpse has joined the level; never add another entity inside its pre-join event.
            pending.add(corpse);
        }
    }

    static void spawn(Entity target) {
        if (!(target.level() instanceof ServerLevel level)) return;
        if (target instanceof CorpseEntity corpse) {
            // Reuse a downed player's physical body on actual death, preserving its settled pose.
            Player owner = corpse.getCorpseUUID().map(level::getPlayerByUUID).orElse(null);
            MedicalRagdollEntity previous = owner == null ? null : find(owner);
            if (previous != null) { previous.bind(corpse); return; }
        }
        MedicalRagdollEntity ragdoll = TYPE.get().create(level);
        if (ragdoll == null) return;
        ragdoll.bind(target);
        ragdoll.moveTo(target.getX(), target.getY() + .9, target.getZ(), target.getYRot(), 0);
        ragdoll.setLaunch(target.getDeltaMovement().x * 20, target.getDeltaMovement().y * 20,
                target.getDeltaMovement().z * 20);
        level.addFreshEntity(ragdoll);
    }

    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        pending.clear();
        warned = false;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        MedicalRagdollEntity ragdoll = find(event.getEntity());
        if (ragdoll != null) ragdoll.discard();
    }
}
