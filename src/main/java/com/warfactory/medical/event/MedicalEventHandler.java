package com.warfactory.medical.event;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.capability.MedicalProvider;
import com.warfactory.medical.compat.OpenPersistenceCompat;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.damage.*;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.network.MedicalNetworking;
import com.warfactory.medical.server.MedicalActionService;
import com.warfactory.medical.server.MedicalEffects;
import com.warfactory.medical.server.MedicalEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
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
 * The Forge FORGE-bus event handler that drives the entire medical pipeline.
 *
 * <p>Auto-registered via {@link Mod.EventBusSubscriber}; every handler is a static method. All work is
 * server-authoritative and gated behind {@code instanceof ServerPlayer} / {@code !level.isClientSide}
 * so nothing ever runs on a logical client. The heavy lifting lives in {@link MedicalEngine} (scheduled
 * physiology) and the {@code core.damage.*} pipeline (damage -> trauma); this class only wires vanilla
 * events to them and performs the two authoritative interceptions the engine cannot: translating raw
 * hurt into trauma, and converting lethal damage into a bleed-out unconsciousness.</p>
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MedicalEventHandler {

    /**
     * Capability attachment key for the per-player medical data.
     */
    private static final ResourceLocation MEDICAL_KEY = new ResourceLocation(WFMedical.MOD_ID, "medical");

    /**
     * Fraction of a fully-blocked hit that leaks through as vanilla-like minor bruising.
     */
    private static final float BLOCKED_RESIDUAL_FRACTION = 0.15F;
    /**
     * Hard cap on that residual so a huge blocked hit still stays cosmetic.
     */
    private static final float BLOCKED_RESIDUAL_MAX = 1.0F;

    private MedicalEventHandler() {
    }

    // ------------------------------------------------------------------ capability attach

    /**
     * Attach a fresh {@link MedicalProvider} to every player and wire its invalidation listener.
     */
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

    /**
     * Fan the server tick out to the engine, which enforces its own cadence and dirty-skip.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MedicalEngine.onServerTick(event.getServer());
        }
    }

    /**
     * Per-player tick hook that advances the overdose ASPHYXIA phase for any asphyxiating server player. Runs
     * every tick (not the engine's throttled cadence) so the sped-up air drain is smooth and reliably
     * overrides vanilla's air regen; a cheap no-op for everyone not currently asphyxiating.
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
        MedicalProfile profile = data.getProfile();
        if (profile.isAsphyxiating()) {
            MedicalEngine.tickAsphyxia(player, profile);
        }
    }

    // ------------------------------------------------------------------ damage -> trauma

    /**
     * Translate incoming damage into persistent trauma instead of directly draining health. Runs only for
     * a server-side player that is neither creative/spectator-immune nor being hit by an
     * invulnerability-bypassing source (void, {@code /kill}), which are left to vanilla so admin kills and
     * out-of-world deaths still work. When trauma is generated the vanilla amount is zeroed (or reduced to
     * a small residual for a fully-blocked hit) so health stays purely derived.
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

        // --- Lethal / finishing blow: die on impact instead of the damage->trauma translation below. ---
        // Unconsciousness is a SURVIVABLE state reached by GRADUAL depletion (bleeding / accumulated
        // trauma), an overdose, or an admin override -- it is NOT a mandatory step before every death. Two
        // cases kill outright here:
        //   (a) FINISH: the player is ALREADY downed/unconscious and takes a real hit -- helpless, so it
        //       kills (fixes "an unconscious player can't be killed").
        //   (b) KILL ON IMPACT: a single blow big enough to deplete their current derived health -- a
        //       genuinely lethal hit (sniper / explosion / heavy weapon), distinct from the many small hits
        //       that accumulate into a survivable unconsciousness.
        // Everything else falls through to the normal damage->trauma translation (health stays derived).
        float currentHealth = player.getHealth();
        boolean alreadyDowned = profile.isDowned() || profile.getState() == HealthState.UNCONSCIOUS;
        boolean finishDowned = alreadyDowned && MedicalConfig.finishDownedOnHit();
        boolean killOnImpact = MedicalConfig.lethalBlowsEnabled()
                && currentHealth > 0.0F
                && amount >= currentHealth * (float) MedicalConfig.lethalBlowHealthFraction();
        if (finishDowned || killOnImpact) {
            markDead(player, data, profile);
            // Guarantee the vanilla hit is fatal (this killing blow bypasses the derived-health model), then
            // fall through to vanilla so actuallyHurt() -> die() -> LivingDeathEvent runs (which now only
            // finalizes and never re-intercepts, because the profile is already DEAD).
            event.setAmount(Math.max(amount, currentHealth + 1.0F));
            return;
        }

        // Taking damage interrupts any in-progress timed treatment.
        if (profile.hasActiveTreatment()) {
            MedicalActionService.cancel(player, "damaged");
        }

        RandomSource rand = player.getRandom();
        long nowTick = player.level().getGameTime();
        TraumaRegistry registry = TraumaRegistry.active();

        DamageCategory cat = DamageClassifier.classify(src);
        // PRECISE hit registration: an attack the envelope registered but which actually threaded a gap
        // between the rigged limbs is a whiff -> cancel it (no damage, no trauma). Centre-mass hits take the
        // cheap tight-box fast-path inside isGapShot, so only grazing arm-margin shots ever build the rig.
        if (MedicalConfig.hitRegistrationMode() == HitRegMode.PRECISE && HitGeometry.isGapShot(player, src, cat)) {
            event.setCanceled(true);
            return;
        }
        LimbType limb = HitLocation.pick(player, src, cat, rand);
        ArmorEvaluation.Outcome outcome = ArmorEvaluation.evaluate(player, limb, cat, amount, rand);
        List<Trauma> generated = TraumaGenerator.generate(cat, outcome, limb, amount, registry, nowTick, rand);

        boolean added = false;
        int maxPerLimb = MedicalConfig.maxTraumaPerLimb();
        Limb targetLimb = profile.limb(limb);
        for (int i = 0; i < generated.size(); i++) {
            Trauma t = generated.get(i);
            // Respect the fracture feature toggle: drop fractures rather than filtering upstream.
            if (t.isFracture() && !MedicalConfig.enableFractures()) {
                continue;
            }
            targetLimb.tryMerge(t, maxPerLimb);
            added = true;
        }

        if (!added) {
            // Nothing translated (e.g. empty registry): leave vanilla behaviour intact so the player is
            // never accidentally invulnerable.
            return;
        }

        profile.markDirty();
        data.bumpRevision();

        // Health is now derived from trauma; stop vanilla from double-counting the same hit. A fully
        // blocked hit still leaves a cosmetic vanilla-like nick so armour "thunk" reads as a light bruise.
        if (outcome == ArmorEvaluation.Outcome.BLOCKED) {
            float residual = Math.min(amount * BLOCKED_RESIDUAL_FRACTION, BLOCKED_RESIDUAL_MAX);
            event.setAmount(residual);
        } else {
            event.setAmount(0.0F);
        }
    }

    // ------------------------------------------------------------------ death finalization

    /**
     * Finalize a death. Unconsciousness is NOT a mandatory step before death: this handler NEVER cancels the
     * event. Whatever made the blow lethal — a kill-on-impact / finishing blow already flagged DEAD in
     * {@link #onLivingHurt}, the engine's expired bleed-out timer, a lethal overdose drain, {@code /kill}, the
     * void, or plain vanilla damage that outran the derived-health model — is allowed straight through. All we
     * do is finalize the medical bookkeeping (mark DEAD, clear every downed/overdose/bleed-out marker, cancel
     * any treatment, broadcast the downed=false edge and restore the standing hitbox) so no tracker keeps
     * rendering a downed pose on a dying player and the body doesn't die with the rotated downed collision box.
     *
     * <p>The actual "gradual depletion becomes a survivable unconsciousness" behaviour lives in
     * {@link com.warfactory.medical.core.Physiology} (lethal condition -> UNCONSCIOUS while bleed-out is
     * enabled) together with the &ge;1-HP pin in {@link MedicalEffects}: a player collapses BEFORE their health
     * ever reaches zero, so a death event here only ever means a genuine kill.</p>
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

    /**
     * Finalize a player as DEAD in the medical model: set the state, clear every transient downed / overdose /
     * bleed-out / asphyxia marker and any admin-forced override, cancel any in-progress treatment, broadcast
     * the downed=false edge to trackers, and refresh the vanilla hitbox/eye-height back to standing. Idempotent
     * and shared by {@link #onLivingHurt} (kill-on-impact / finishing a downed player) and {@link #onLivingDeath}.
     */
    private static void markDead(ServerPlayer player, IMedicalData data, MedicalProfile profile) {
        profile.setState(HealthState.DEAD);
        profile.setForcedState(null);
        profile.setOverdoseUnconscious(false);
        profile.setOverdoseUntilTick(0L);
        profile.setBleedoutSinceTick(-1L);
        profile.setAsphyxiating(false);
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
        profile.markDirty();
        data.bumpRevision();
    }

    // ------------------------------------------------------------------ incapacitation (unconscious)

    /**
     * While unconscious, a player is fully helpless: they cannot act. Movement/sprint/jump are already
     * derived-locked (speed 0, {@code jumpMultiplier}/sprint blocked via the mixin + attribute modifier), and
     * jump is additionally hard-cancelled in {@code LivingEntityMixin}. The handlers below block the remaining
     * interaction surface — using/placing items, breaking blocks, attacking and interacting with entities —
     * for any unconscious player. They read the server-authoritative {@link MedicalState} (which is also
     * client-correct for the local player), so they no-op for a conscious player and only ever cancel a downed
     * player's own actions (a medic interacting WITH a downed player is a separate, conscious actor and is
     * never blocked). Cancelling the event on both logical sides gives instant client feedback and stays
     * server-authoritative.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (MedicalState.isUnconscious(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (MedicalState.isUnconscious(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (MedicalState.isUnconscious(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (MedicalState.isUnconscious(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (MedicalState.isUnconscious(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (MedicalState.isUnconscious(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && MedicalState.isUnconscious(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && MedicalState.isUnconscious(player)) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MedicalEngine.onPlayerJoin(player);
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

    /**
     * Keep the medical attribute modifiers (notably the +10 MAX_HEALTH lift) consistent across a gamemode
     * switch. Without this, entering creative/spectator strips our modifiers (creative immunity calls
     * {@link MedicalEffects#clear}), and returning to survival never re-adds them because a healthy player is
     * skipped by the engine's dirty fast-path — leaving max-health rolled back to the vanilla 20.
     *
     * <p>The event fires BEFORE the switch is applied, so {@code player.isCreative()} still reflects the OLD
     * mode; the decision is made purely on {@link PlayerEvent.PlayerChangeGameModeEvent#getNewGameMode()}. If
     * the NEW mode is creative/spectator and creative-immunity is enabled we strip our modifiers; otherwise we
     * fully re-sync (re-adds the modifier, sets health to the derived target, pushes a snapshot). Server-only
     * and null-safe against a missing capability.</p>
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
            // The event fires BEFORE the switch, so player.isCreative()/isSpectator() still report the OLD
            // mode; the parameterless resync() would re-consult those stale flags and, on a
            // creative/spectator -> survival transition, wrongly keep treating the player as creative-immune
            // and skip re-adding the +10 MAX_HEALTH modifier. Pass the authoritative decision (immuneNext is
            // false in this branch, so effects MUST be applied) computed from getNewGameMode() above.
            MedicalEngine.resync(player, true);
        }
    }

    /**
     * Downed-state catch-up for late observers. Edge broadcasts only fire on a state change, so a viewer
     * who begins tracking a player who is ALREADY downed would otherwise never learn it. When a server
     * player starts tracking another server player, push the target's current downed state to just that
     * viewer. Null-safe: if the target has no medical capability, nothing is sent.
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
     * Carry the medical profile across the clone boundary. Per design, a true-death respawn does NOT
     * auto-heal by carrying trauma (the fresh clone keeps its pristine default profile), while a
     * non-death clone (dimension change, e.g. returning from the End) preserves the full trauma graph.
     * The original's capabilities are temporarily revived so they can be read after death invalidation.
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

    // Open Persistence integration

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

        RandomSource rand = victim.getRandom();
        long nowTick = victim.level().getGameTime();
        TraumaRegistry registry = TraumaRegistry.active();
        DamageCategory cat = DamageClassifier.classify(src);
        if (MedicalConfig.hitRegistrationMode() == HitRegMode.PRECISE && HitGeometry.isGapShot(victim, src, cat)) {
            event.setCanceled(true);
            return;
        }
        LimbType limb = HitLocation.pick(victim, src, cat, rand);
        ArmorEvaluation.Outcome outcome = ArmorEvaluation.evaluate(victim, limb, cat, amount, rand);
        List<Trauma> generated = TraumaGenerator.generate(cat, outcome, limb, amount, registry, nowTick, rand);

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
        if (added) {
            profile.markDirty();
            data.bumpRevision();
        }
        // Deliberately NOT zeroing event.getAmount(): the body keeps vanilla health (no offline physiology
        // tick), so Open Persistence's health/death handling is untouched -- we only stamp the carried profile.
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
}
