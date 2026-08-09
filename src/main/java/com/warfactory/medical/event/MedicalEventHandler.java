package com.warfactory.medical.event;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.capability.MedicalProvider;
import com.warfactory.medical.compat.OpenPersistenceCompat;
import com.warfactory.medical.compat.TaczCompat;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.damage.*;
import com.warfactory.medical.core.damage.rig.RigCache;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import com.warfactory.medical.network.MedicalNetworking;
import com.warfactory.medical.server.MedicalActionService;
import com.warfactory.medical.server.MedicalEffects;
import com.warfactory.medical.server.MedicalEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MedicalEventHandler {

    private static final ResourceLocation MEDICAL_KEY = new ResourceLocation(WFMedical.MOD_ID, "medical");

    private static final float BLOCKED_RESIDUAL_FRACTION = 0.15F;
    private static final float BLOCKED_RESIDUAL_MAX = 1.0F;

    private static final float OVERFLOW_BLEED_FACTOR = 0.8F;
    private static final float OVERFLOW_BLEED_MAX = 1.0F;

    private MedicalEventHandler() {
    }


    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        Entity object = event.getObject();
        if (object instanceof Player
                || (OpenPersistenceCompat.isPersistentBody(object) && MedicalConfig.openPersistenceCompat())) {
            MedicalProvider provider = new MedicalProvider();
            event.addCapability(MEDICAL_KEY, provider);
            event.addListener(provider::invalidate);
        }
    }


    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MedicalEngine.onServerTick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return;
        }
        MedicalEngine.tickBreathing(player, data.getProfile());
    }

    @SubscribeEvent
    public static void onDrownDamage(LivingAttackEvent event) {
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

    @SubscribeEvent
    public static void onDownedSuffocation(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        DamageSource src = event.getSource();
        if (src != null && src.is(DamageTypes.IN_WALL) && MedicalState.isDowned(player)) {
            event.setCanceled(true);
        }
    }



    @SubscribeEvent
    public static void onLivingAttackGapReject(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        boolean isPlayer = victim instanceof Player;
        boolean isBody = !isPlayer && OpenPersistenceCompat.isPersistentBody(victim)
                && MedicalConfig.openPersistenceCompat();
        if (!isPlayer && !isBody) {
            return;
        }
        DamageSource src = event.getSource();
        if (src == null || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        DamageCategory cat = DamageClassifier.classify(src);
        if (HitGeometry.shouldRejectGap(victim, src, cat)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        if ((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            return;
        }

        DamageSource src = event.getSource();
        if (src != null && src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();

        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }

        // A TACZ bullet lands as two hurt events (a non-armor-piercing and an armor-piercing portion, with
        // i-frames zeroed between them). It applied damage twice
        float effectiveAmount = amount;
        OptionalDouble taczTotal = TaczCompat.bulletTotalDamage(src);
        if (taczTotal.isPresent()) {
            if (!TaczCompat.claimBulletHit(src, player.level().getGameTime())) {
                event.setAmount(0.0F);
                return;
            }
            effectiveAmount = (float) taczTotal.getAsDouble();
        }

        recordDamagingPlayer(profile, src, player);

        boolean alreadyDowned = profile.isDowned() || profile.getState() == HealthState.UNCONSCIOUS;
        boolean finishDowned = alreadyDowned && MedicalConfig.finishDownedOnHit();

        HurtResolution res = finishDowned ? null : resolveHit(player, src, effectiveAmount, profile);
        if (finishDowned || (res != null && res.majorTrauma())) {
            markDead(player, data, profile);
            event.setAmount(Math.max(effectiveAmount, player.getHealth() + 1.0F));
            return;
        }

        if (profile.hasActiveTreatment()) {
            MedicalActionService.cancel(player, "damaged");
        }
        if (!res.traumaAdded()) {
            return;
        }
        profile.markDirty();
        // Reconcile (recompute derived stats, apply effects, broadcast downed state) immediately rather
        // than waiting for the next throttled MedicalEngine tick (up to updateIntervalTicks ticks later).
        // Without this, a hit that pushes the victim into UNCONSCIOUS renders them fully upright/mobile
        // for that whole window before snapping into the downed pose -- most visible on armored hits,
        // since armor is what most often turns a would-be-lethal (instant markDead, no gap) hit into a
        // survived-but-downed one.
        MedicalEngine.resync(player, true);

        if (res.armor() == ArmorEvaluation.Outcome.BLOCKED) {
            event.setAmount(Math.min(effectiveAmount * BLOCKED_RESIDUAL_FRACTION, BLOCKED_RESIDUAL_MAX));
        } else {
            event.setAmount(0.0F);
        }
    }

    /**
     * Consolidates health recovery: when WFMedical manages regen it is the sole authority on a player's
     * current health (the tick drives it UP to the medical value, see {@link MedicalEngine}). Here we clamp
     * any vanilla heal (natural regen, regen potions, ...) so it can never push health past that medical
     * value -- otherwise vanilla natural regen fights the medical clamp every tick, and disabling vanilla
     * regen would also stop medical recovery.
     */
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
        IMedicalData data = MedicalCapabilities.get(player);
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

    private static void recordDamagingPlayer(MedicalProfile profile, DamageSource src, ServerPlayer victim) {
        if (src == null) {
            return;
        }
        if (src.getEntity() instanceof Player attacker && attacker != victim) {
            profile.setLastDamagingPlayer(attacker.getUUID(), victim.level().getGameTime());
        }
    }

    private static HurtResolution resolveHit(LivingEntity victim, DamageSource src, float amount, MedicalProfile profile) {
        RandomSource rand = victim.getRandom();
        long nowTick = victim.level().getGameTime();
        TraumaRegistry registry = TraumaRegistry.active();

        DamageCategory cat = DamageClassifier.classify(src);
        List<LimbType> limbs = MedicalConfig.penetrationEnabled()
                ? HitLocation.pickPierced(victim, src, cat, rand)
                : List.of(HitLocation.pick(victim, src, cat, rand));
        if (limbs.isEmpty()) {
            return new HurtResolution(false, false, ArmorEvaluation.Outcome.FULL);
        }

        LimbType primary = limbs.get(0);
        ArmorEvaluation.Outcome primaryOutcome = ArmorEvaluation.evaluate(victim, primary, cat, amount, rand);

        boolean majorTrauma = primaryOutcome != ArmorEvaluation.Outcome.BLOCKED
                && MedicalConfig.canInstakillOnImpact(cat)
                && amount >= MedicalConfig.maxHealthPoints() * (float) MedicalConfig.majorTraumaFraction(cat);
        if (majorTrauma) {
            return new HurtResolution(true, false, primaryOutcome);
        }

        boolean addedAny = false;
        double falloff = MedicalConfig.penetrationEnergyFalloff();
        float energy = amount;
        for (int i = 0; i < limbs.size(); i++) {
            LimbType limb = limbs.get(i);
            ArmorEvaluation.Outcome outcome = (i == 0)
                    ? primaryOutcome
                    : ArmorEvaluation.evaluate(victim, limb, cat, energy, rand);
            addedAny |= applyLimbTrauma(cat, outcome, limb, energy, profile, registry, nowTick, rand);
            energy = (float) (energy * falloff);
        }
        return new HurtResolution(false, addedAny, primaryOutcome);
    }

    private static boolean applyLimbTrauma(DamageCategory cat, ArmorEvaluation.Outcome outcome, LimbType limbType,
                                           float energy, MedicalProfile profile, TraumaRegistry registry,
                                           long nowTick, RandomSource rand) {
        List<Trauma> generated = TraumaGenerator.generate(cat, outcome, limbType, energy, registry, nowTick, rand);
        Limb targetLimb = profile.limb(limbType);
        targetLimb.rebuildCache();
        float beforeReduction = targetLimb.getCachedHealthReduction();
        boolean added = mergeTrauma(profile, limbType, generated);
        if (added) {
            TraumaEscalation.escalate(targetLimb, limbType, registry, MedicalConfig.maxTraumaPerLimb(), nowTick);
            applyDepletionEffects(targetLimb, limbType, beforeReduction, registry, nowTick, rand);
        }
        return added;
    }

    private static void applyDepletionEffects(Limb limb, LimbType limbType, float beforeReduction,
                                              TraumaRegistry registry, long nowTick, RandomSource rand) {
        float cap = MedicalConfig.healthShare(limbType) * MedicalConfig.maxHealthPoints();
        if (cap <= 0.0F) {
            return;
        }
        limb.rebuildCache();
        float afterReduction = limb.getCachedHealthReduction();
        if (afterReduction < cap) {
            return;
        }
        int maxPerLimb = MedicalConfig.maxTraumaPerLimb();
        if (MedicalConfig.enableFractures() && !limb.hasCachedFracture()
                && (limbType.isArm() || limbType.isLeg())) {
            TraumaType fracture = resolveTrauma(registry, "fracture", TraumaCategory.FRACTURE);
            if (fracture != null) {
                limb.tryMerge(new Trauma(fracture, limbType, 1.0F, nowTick), maxPerLimb);
            }
        }
        float overflow = afterReduction - Math.max(cap, beforeReduction);
        if (overflow > 0.0F && MedicalConfig.enableBleeding()) {
            TraumaType bleed = resolveTrauma(registry, "laceration_large", TraumaCategory.LACERATION);
            if (bleed != null) {
                float sev = Math.min(overflow * OVERFLOW_BLEED_FACTOR, OVERFLOW_BLEED_MAX);
                if (sev > 0.0F) {
                    limb.tryMerge(new Trauma(bleed, limbType, sev, nowTick), maxPerLimb);
                }
            }
        }
        limb.rebuildCache();
    }

    private static TraumaType resolveTrauma(TraumaRegistry registry, String id, TraumaCategory category) {
        TraumaType type = registry.get(id);
        if (type == null) {
            type = registry.firstOfCategory(category);
        }
        return type;
    }

    private static boolean mergeTrauma(MedicalProfile profile, LimbType limb, List<Trauma> generated) {
        boolean added = false;
        int maxPerLimb = MedicalConfig.maxTraumaPerLimb();
        Limb targetLimb = profile.limb(limb);
        for (int i = 0; i < generated.size(); i++) {
            Trauma t = generated.get(i);
            if (t.isFracture() && !MedicalConfig.enableFractures()) {
                continue;
            }
            targetLimb.tryMerge(t, maxPerLimb);
            added = true;
        }
        return added;
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        IMedicalData data = MedicalCapabilities.get(player);
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


    private static void markDead(ServerPlayer player, IMedicalData data, MedicalProfile profile) {
        profile.enterDeadState(false);
        if (profile.hasActiveTreatment()) {
            MedicalActionService.cancel(player, "dead");
        }
        // Deliberately NOT un-broadcasting downed=false / resetting the hitbox here: this used to snap
        // the corpse back to the standing pose/hitbox the instant death was triggered, which races ahead
        // of vanilla's own ~20-tick death-fall animation and produces a visible "stands upright, then
        // lays back down a second later" flash on any hit that finishes off an already-downed player.
        // A dead body staying rendered lying flat through the death sequence is the correct look anyway.
        // The downed flag/hitbox is reset independently on respawn: the respawned player gets a brand new
        // MedicalProfile (onPlayerClone bails out on death, MedicalProfile.java) whose lastBroadcastDowned
        // defaults false, MedicalEventHandler.onPlayerRespawn resyncs it, and the client's own
        // ClientDownedTracker.Events.onRespawnClone independently clears the flag for the new entity id.
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
            MedicalNetworking.sendHitAuthority(player);
            IMedicalData data = MedicalCapabilities.get(player);
            if (data != null) {
                MedicalNetworking.broadcastTourniquets(player, data.getProfile());
            }
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
            RigCache.clearHint(player.getId());
        }
    }

    @SubscribeEvent
    public static void onChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IMedicalData data = MedicalCapabilities.get(player);
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
        IMedicalData data = MedicalCapabilities.get(target);
        if (data == null) {
            return;
        }
        MedicalNetworking.sendDownedTo(viewer, target.getId(), data.getProfile().isDowned());
        MedicalNetworking.sendTourniquetsTo(viewer, target.getId(),
                MedicalNetworking.tourniquetMask(data.getProfile()));
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            return;
        }
        Player original = event.getOriginal();
        original.reviveCaps();
        try {
            MedicalCapabilities.copy(original, event.getEntity());
        } finally {
            original.invalidateCaps();
        }
    }

    @SubscribeEvent
    public static void onPersistentBodyHurt(LivingHurtEvent event) {
        if (!MedicalConfig.openPersistenceCompat()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Player || entity.level().isClientSide) {
            return;
        }
        if (!(entity instanceof LivingEntity victim) || !OpenPersistenceCompat.isPersistentBody(victim)) {
            return;
        }
        DamageSource src = event.getSource();
        if (src != null && src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        float effectiveAmount = amount;
        OptionalDouble taczTotal = TaczCompat.bulletTotalDamage(src);
        if (taczTotal.isPresent()) {
            if (!TaczCompat.claimBulletHit(src, victim.level().getGameTime())) {
                event.setAmount(0.0F);
                return;
            }
            effectiveAmount = (float) taczTotal.getAsDouble();
        }
        IMedicalData data = victim.getCapability(MedicalCapabilities.MEDICAL).resolve().orElse(null);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();

        HurtResolution res = resolveHit(victim, src, effectiveAmount, profile);
        if (res.majorTrauma()) {
            profile.enterDeadState(false);
            data.bumpRevision();
            event.setAmount(Math.max(effectiveAmount, victim.getHealth() + 1.0F));
            return;
        }
        if (res.traumaAdded()) {
            profile.markDirty();
            data.bumpRevision();
        }
    }


    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLogoutCopyProfileToBody(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!MedicalConfig.openPersistenceCompat() || !OpenPersistenceCompat.isLoaded()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IMedicalData playerData = MedicalCapabilities.get(player);
        if (playerData == null) {
            return;
        }
        findPersistentBody(player).ifPresent(body -> {
            IMedicalData bodyData = body.getCapability(MedicalCapabilities.MEDICAL).resolve().orElse(null);
            if (bodyData != null) {
                bodyData.load(playerData.save());
                bodyData.bumpRevision();
                if (body instanceof LivingEntity living) {
                    DerivedStats stats = bodyData.getProfile().recompute(MedicalConfig.toPhysiologyParams());
                    MedicalEffects.applyToBody(living, stats);
                }
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLoginCopyProfileFromBody(PlayerEvent.PlayerLoggedInEvent event) {
        if (!MedicalConfig.openPersistenceCompat() || !OpenPersistenceCompat.isLoaded()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IMedicalData playerData = MedicalCapabilities.get(player);
        if (playerData == null) {
            return;
        }
        findPersistentBody(player).ifPresent(body -> {
            IMedicalData bodyData = body.getCapability(MedicalCapabilities.MEDICAL).resolve().orElse(null);
            if (bodyData != null) {
                playerData.load(bodyData.save());
                playerData.bumpRevision();
                MedicalEngine.resync(player, true);
            }
        });
    }

    private static Optional<Entity> findPersistentBody(ServerPlayer player) {
        UUID id = player.getUUID();
        AABB box = player.getBoundingBox().inflate(4.0);
        List<Entity> found = player.level().getEntities((Entity) null, box,
                e -> OpenPersistenceCompat.isPersistentBody(e)
                        && OpenPersistenceCompat.bodyOwner(e).map(id::equals).orElse(false));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private record HurtResolution(boolean majorTrauma, boolean traumaAdded, ArmorEvaluation.Outcome armor) {
    }
}
