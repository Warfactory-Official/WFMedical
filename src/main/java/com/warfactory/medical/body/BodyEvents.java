package com.warfactory.medical.body;

import com.warfactory.medical.body.api.MedicalState;
import com.warfactory.medical.body.attachment.IMedicalData;
import com.warfactory.medical.body.attachment.MedicalAttachments;
import com.warfactory.medical.body.network.BodyNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

public final class BodyEvents {

    private BodyEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MedicalEngine.onServerTick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return;
        }
        MedicalEngine.tickBreathing(player, data.getProfile());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDrownDamage(LivingIncomingDamageEvent event) {
        if (!MedicalConfig.drowningAsphyxiaEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        if (event.getSource().is(DamageTypes.DROWN)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDownedSuffocation(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        DamageSource src = event.getSource();
        if (src != null && src.is(DamageTypes.IN_WALL) && MedicalState.isDowned(player)) {
            event.setCanceled(true);
        }
    }

    /** manageNaturalRegen: vanilla heals clamped to the medical health value (else they fight the engine's clamp). */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!MedicalConfig.manageNaturalRegen()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if ((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            return;
        }
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return;
        }
        float cap = data.getProfile().cached().effectiveCurrentHealth();
        float headroom = cap - player.getHealth();
        if (headroom <= 0.0F) {
            event.setCanceled(true);
        } else if (event.getAmount() > headroom) {
            event.setAmount(headroom);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();
        if (profile.getState() != HealthState.DEAD
                || profile.isLastBroadcastDowned()
                || profile.hasActiveTreatment()) {
            markDead(player, data, profile);
        }
    }

    public static void markDead(ServerPlayer player, IMedicalData data, MedicalProfile profile) {
        profile.enterDeadState(false);
        if (profile.hasActiveTreatment()) {
            BodyHooks.ACTIVE_TREATMENT.get().cancel(player, "dead");
        }

        data.bumpRevision();
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (MedicalState.isHandsDisabled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (MedicalState.isHandsDisabled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (MedicalState.isHandsDisabled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (MedicalState.isHandsDisabled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (MedicalState.isHandsDisabled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (MedicalState.isHandsDisabled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && MedicalState.isHandsDisabled(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && MedicalState.isHandsDisabled(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MedicalEngine.onPlayerJoin(player);
            BodyHooks.JOIN.values().forEach(hook -> hook.accept(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MedicalEngine.onPlayerJoin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MedicalEngine.onPlayerJoin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MedicalEngine.onPlayerLeave(player);
        }
    }

    @SubscribeEvent
    public static void onChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return;
        }
        GameType next = event.getNewGameMode();
        boolean immuneNext = (next == GameType.CREATIVE || next == GameType.SPECTATOR)
                && MedicalConfig.effectImmuneInCreative();
        if (immuneNext) {
            MedicalEffects.clear(player);
        } else {
            MedicalEngine.resync(player, true);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer viewer)) {
            return;
        }
        IMedicalData data = MedicalAttachments.get(target);
        if (data == null) {
            return;
        }
        BodyNetwork.sendDownedTo(viewer, target.getId(), data.getProfile().isDowned());
        BodyHooks.START_TRACKING.values().forEach(hook -> hook.accept(viewer, target));
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            return;
        }
        MedicalAttachments.copy(event.getOriginal(), event.getEntity());
    }
}
