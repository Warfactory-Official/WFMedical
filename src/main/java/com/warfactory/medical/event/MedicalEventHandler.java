package com.warfactory.medical.event;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.capability.MedicalProvider;
import com.warfactory.medical.compat.OpenPersistenceCompat;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.damage.*;
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
import java.util.UUID;

/**
 * Forge event handler that drives the medical pipeline.
 *
 * <p>All work is server-authoritative. The heavy lifting lives in {@link MedicalEngine} (scheduled
 * physiology) and {@code core.damage.*}; this class wires vanilla events to them, translating raw hurt
 * into trauma and converting lethal damage into a bleed-out unconsciousness.</p>
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MedicalEventHandler {

    private static final ResourceLocation MEDICAL_KEY = new ResourceLocation(WFMedical.MOD_ID, "medical");

    /**
     * Fraction of a fully-blocked hit that leaks through as vanilla-like minor bruising.
     */
    private static final float BLOCKED_RESIDUAL_FRACTION = 0.15F;
    /**
     * Hard cap on that residual so a huge blocked hit still stays cosmetic.
     */
    private static final float BLOCKED_RESIDUAL_MAX = 1.0F;

    /**
     * Fraction of a drained limb's overflow damage redirected into an external laceration.
     */
    private static final float OVERFLOW_BLEED_FACTOR = 0.8F;
    /**
     * Cap on a single overflow bleed's severity so one huge hit into a maxed limb stays bounded.
     */
    private static final float OVERFLOW_BLEED_MAX = 1.0F;

    private MedicalEventHandler() {
    }

    // ------------------------------------------------------------------ capability attach

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        Entity object = event.getObject();
        // Players always get the medical capability; an Open Persistence logout body gets one too (gated on
        // the compat toggle) so it can carry/accrue the owner's medical profile while they are offline.
        if (object instanceof Player
                || (OpenPersistenceCompat.isPersistentBody(object) && MedicalConfig.openPersistenceCompat())) {
            MedicalProvider provider = new MedicalProvider();
            event.addCapability(MEDICAL_KEY, provider);
            event.addListener(provider::invalidate);
        }
    }

    // ------------------------------------------------------------------ scheduled physiology

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MedicalEngine.onServerTick(event.getServer());
        }
    }

    /**
     * Advances asphyxia state every tick (not the engine's throttled cadence) so it responds immediately;
     * {@link MedicalEngine#tickBreathing} early-outs cheaply for anyone breathing normally.
     */
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

    /**
     * Suppresses vanilla drowning damage and routes it through the asphyxia system instead.
     */
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

    // ------------------------------------------------------------------ damage -> trauma


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

    /**
     * Translate incoming damage into persistent trauma. Invulnerability-bypassing sources (void, {@code /kill})
     * are left to vanilla so admin kills still work. When trauma is generated the vanilla amount is zeroed
     * (or reduced to a small residual for a fully-blocked hit) so health stays purely derived.
     */
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
        // Never intercept sources that are meant to bypass everything (void, /kill, generic-kill).
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

        // Finishing a helpless, already-downed player is independent of the blow's magnitude or armor: any
        // real hit kills them (fixes "an unconscious player can't be killed"), gated by finishDownedOnHit.
        boolean alreadyDowned = profile.isDowned() || profile.getState() == HealthState.UNCONSCIOUS;
        boolean finishDowned = alreadyDowned && MedicalConfig.finishDownedOnHit();

        // Otherwise classify + armor-evaluate the hit and decide MAJOR TRAUMA vs a survivable damage->trauma
        // translation. Instant death is INTRINSIC (no on/off toggle): a hit the medical armor did not BLOCK
        // whose damage alone reaches this category's fraction of the FULL healthy bar kills on impact --
        // distinct from the many small hits that accumulate into a survivable unconsciousness (health stays
        // derived). Unconsciousness is thus never a mandatory step before every death.
        HurtResolution res = finishDowned ? null : resolveHit(player, src, amount, profile);
        if (finishDowned || (res != null && res.majorTrauma())) {
            markDead(player, data, profile);
            // Guarantee the vanilla hit is fatal (this killing blow bypasses the derived-health model), then
            // fall through to vanilla so actuallyHurt() -> die() -> LivingDeathEvent runs (which now only
            // finalizes and never re-intercepts, because the profile is already DEAD).
            event.setAmount(Math.max(amount, player.getHealth() + 1.0F));
            return;
        }

        // Survivable hit. Taking damage interrupts any in-progress timed treatment.
        if (profile.hasActiveTreatment()) {
            MedicalActionService.cancel(player, "damaged");
        }
        if (!res.traumaAdded()) {
            // Nothing translated (e.g. empty registry): leave vanilla behaviour intact so the player is
            // never accidentally invulnerable.
            return;
        }
        profile.markDirty();
        data.bumpRevision();

        // Health is now derived from trauma; stop vanilla from double-counting the same hit. A fully blocked
        // hit still leaves a cosmetic vanilla-like nick so armour "thunk" reads as a light bruise.
        if (res.armor() == ArmorEvaluation.Outcome.BLOCKED) {
            event.setAmount(Math.min(amount * BLOCKED_RESIDUAL_FRACTION, BLOCKED_RESIDUAL_MAX));
        } else {
            event.setAmount(0.0F);
        }
    }

    /**
     * Classifies the hit, evaluates medical armor, and either reports a major (lethal-on-impact) trauma or
     * merges generated trauma into {@code profile}. Does NOT enact death or touch the event amount.
     */
    private static HurtResolution resolveHit(LivingEntity victim, DamageSource src, float amount, MedicalProfile profile) {
        RandomSource rand = victim.getRandom();
        long nowTick = victim.level().getGameTime();
        TraumaRegistry registry = TraumaRegistry.active();

        DamageCategory cat = DamageClassifier.classify(src);
        LimbType limb = HitLocation.pick(victim, src, cat, rand);
        ArmorEvaluation.Outcome outcome = ArmorEvaluation.evaluate(victim, limb, cat, amount, rand);

        // MAJOR TRAUMA: a non-blocked hit that alone reaches this category's fraction of the FULL healthy bar.
        boolean majorTrauma = outcome != ArmorEvaluation.Outcome.BLOCKED
                && MedicalConfig.canInstakillOnImpact(cat)
                && amount >= MedicalConfig.maxHealthPoints() * (float) MedicalConfig.majorTraumaFraction(cat);
        if (majorTrauma) {
            return new HurtResolution(true, false, outcome);
        }

        List<Trauma> generated = TraumaGenerator.generate(cat, outcome, limb, amount, registry, nowTick, rand);
        Limb targetLimb = profile.limb(limb);
        targetLimb.rebuildCache();
        float beforeReduction = targetLimb.getCachedHealthReduction();
        boolean added = mergeTrauma(profile, limb, generated);
        if (added) {
            applyDepletionEffects(targetLimb, limb, beforeReduction, registry, nowTick, rand);
        }
        return new HurtResolution(false, added, outcome);
    }

    /**
     * When a limb's health reduction reaches its capped share ("drained"): force a fracture and redirect the
     * overflow of THIS hit into an external laceration. The overflow bleed is deliberately external (stoppable
     * with a bandage/tourniquet) so a maxed limb is not an unstoppable death sentence in combat.
     */
    private static void applyDepletionEffects(Limb limb, LimbType limbType, float beforeReduction,
                                              TraumaRegistry registry, long nowTick, RandomSource rand) {
        float cap = MedicalConfig.healthShare(limbType) * MedicalConfig.maxHealthPoints();
        if (cap <= 0.0F) {
            return;
        }
        limb.rebuildCache();
        float afterReduction = limb.getCachedHealthReduction();
        if (afterReduction < cap) {
            return; // not drained
        }
        int maxPerLimb = MedicalConfig.maxTraumaPerLimb();
        // Fracture-on-depletion: a drained limb breaks (if the feature is on and it is not already fractured).
        if (MedicalConfig.enableFractures() && !limb.hasCachedFracture()) {
            TraumaType fracture = resolveTrauma(registry, "fracture", TraumaCategory.FRACTURE);
            if (fracture != null) {
                limb.tryMerge(new Trauma(fracture, limbType, 1.0F, nowTick), maxPerLimb);
            }
        }
        // Overflow of THIS hit beyond the cap -> a large EXTERNAL laceration on that limb (carrying the blend
        // of heavy bleeding + some pain). Deliberately an external bleed, not internal bleeding, so it stays
        // STOPPABLE on the limb with a bandage / tourniquet -- otherwise a maxed limb would be an unstoppable
        // death sentence in any fight without a hemostatic.
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

    /**
     * Merge generated trauma into the profile's target limb, respecting the fracture feature toggle and the
     * per-limb cap. Returns whether anything was actually added.
     */
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

    /**
     * Finalizes medical bookkeeping on death: mark DEAD, clear downed/overdose/bleed-out markers, cancel
     * treatment, broadcast downed=false, and restore the standing hitbox. Never cancels the event — any
     * cause (kill-on-impact, bleed-out timer, lethal overdose, /kill, void) goes straight through.
     */
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
        // Only touch the profile if there is anything to finalize (already-DEAD, non-downed, treatment-free
        // players need nothing) so a plain vanilla death stays cheap. markDead is idempotent.
        if (profile.getState() != HealthState.DEAD
                || profile.isLastBroadcastDowned()
                || profile.hasActiveTreatment()) {
            markDead(player, data, profile);
        }
    }

    // ------------------------------------------------------------------ death finalization

    /**
     * Sets the medical state to DEAD, clears all transient downed/overdose/bleed-out markers, cancels
     * treatment, broadcasts downed=false, and restores the standing hitbox. Idempotent.
     */
    private static void markDead(ServerPlayer player, IMedicalData data, MedicalProfile profile) {
        profile.enterDeadState(false);
        if (profile.hasActiveTreatment()) {
            MedicalActionService.cancel(player, "dead");
        }
        if (profile.isLastBroadcastDowned()) {
            MedicalNetworking.broadcastDowned(player, false);
            profile.setLastBroadcastDowned(false);
        }
        // Restore the standing collision box / eye-height so the dying body isn't left with the rotated downed
        // hitbox (and the low camera), which otherwise lingered into the respawn as a twisted pose.
        player.refreshDimensions();
        data.bumpRevision();
    }

    /**
     * Block all interaction (place/use/break/attack) when the player cannot use their hands:
     * {@link MedicalState#isHandsDisabled} is true while unconscious OR with both arms disabled. A medic
     * acting on a downed player is a separate conscious actor and is never blocked.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (MedicalState.isHandsDisabled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ incapacitation (unconscious)

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
        }
    }

    // ------------------------------------------------------------------ lifecycle

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

    /**
     * Keeps attribute modifiers consistent across a gamemode switch. The event fires BEFORE the switch, so
     * {@code player.isCreative()} still reports the OLD mode; we use {@code getNewGameMode()} instead.
     * On a creative/spectator→survival transition the stale flags would wrongly skip re-adding the +10
     * MAX_HEALTH modifier, so we pass the authoritative decision computed from the new mode.
     */
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

    /**
     * Downed-state catch-up for late observers: edge broadcasts only fire on a state change, so a viewer who
     * starts tracking an already-downed player would never learn it. Sends the current downed state to just
     * that viewer.
     */
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
    }

    /**
     * Carries the medical profile across a non-death clone boundary (dimension change preserves trauma);
     * a true-death respawn keeps a fresh profile. The original's caps are temporarily revived to read after
     * death invalidation.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            // Death respawn: start clean; PlayerRespawnEvent -> onPlayerJoin re-syncs the fresh profile.
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
        IMedicalData data = victim.getCapability(MedicalCapabilities.MEDICAL).resolve().orElse(null);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();

        // Gap-rejection is handled pre-hit in onLivingAttackGapReject (persistent bodies included), so this
        // only sees attacks that connected with a limb. Same intrinsic major-trauma rule as live players.
        HurtResolution res = resolveHit(victim, src, amount, profile);
        if (res.majorTrauma()) {
            // A massive blow destroys the body: mark the carried profile DEAD and let the vanilla lethal
            // amount kill the entity (bodies keep vanilla health, so here we DO push the amount).
            profile.enterDeadState(false);
            data.bumpRevision();
            event.setAmount(Math.max(amount, victim.getHealth() + 1.0F));
            return;
        }
        if (res.traumaAdded()) {
            profile.markDirty();
            data.bumpRevision();
        }
        // Deliberately NOT zeroing event.getAmount() for a survivable hit: the body keeps vanilla health (no
        // offline physiology tick), so Open Persistence's health/death handling is untouched -- we only stamp
        // the carried profile.
    }

    // Open Persistence integration

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
                // Give the body the owner's derived health pool (default 30, not the vanilla 20) so a
                // combat-logged body is exactly as killable as the player was. Bodies don't tick physiology,
                // so stamp it once here (a permanent modifier that survives a restart).
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
