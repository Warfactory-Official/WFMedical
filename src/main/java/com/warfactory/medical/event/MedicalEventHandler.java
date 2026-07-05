package com.warfactory.medical.event;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.capability.MedicalProvider;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

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
        if (event.getObject() instanceof Player) {
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

        // Taking damage interrupts any in-progress timed treatment.
        if (profile.hasActiveTreatment()) {
            MedicalActionService.cancel(player, "damaged");
        }

        RandomSource rand = player.getRandom();
        long nowTick = player.level().getGameTime();
        TraumaRegistry registry = TraumaRegistry.active();

        DamageCategory cat = DamageClassifier.classify(src);
        LimbType limb = HitLocation.pick(src, cat, rand);
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

    // ------------------------------------------------------------------ lethal -> bleed-out unconscious

    /**
     * Intercept lethal damage: rather than dying instantly the player transitions to
     * {@link HealthState#UNCONSCIOUS} (via the bleed-out cause — {@code bleedoutSinceTick} is set as the
     * bleed-out marker) and is pinned at ~1 health. Real death is only permitted once the engine's bleed-out
     * timer expires (it sets the state to {@link HealthState#DEAD} and drops health to zero); such a death is
     * allowed straight through. Gated on {@link MedicalConfig#enableBleedout()}.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        if (!MedicalConfig.enableBleedout()) {
            return;
        }
        if ((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            return;
        }

        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();

        // The engine already ruled this a real bleed-out; let vanilla finish the kill.
        if (profile.getState() == HealthState.DEAD) {
            return;
        }

        // Admin / void / generic kills MUST always kill, even with bleed-out enabled. These sources bypass
        // invulnerability (/kill's genericKill, the void, our own command-suite kill); a null source is an
        // unknown out-of-band lethal cause treated the same. Never intercept them into a bleed-out — let
        // vanilla's death (die() -> LivingDeathEvent -> respawn) proceed. First clear any transient downed
        // state and mark the profile DEAD so trackers stop rendering a downed pose on a player who is dying,
        // and any re-entrant death event short-circuits on the DEAD check above.
        DamageSource src = event.getSource();
        if (src == null || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            profile.setState(HealthState.DEAD);
            profile.setOverdoseUnconscious(false);
            profile.setOverdoseUntilTick(0L);
            profile.setBleedoutSinceTick(-1L);
            if (profile.hasActiveTreatment()) {
                MedicalActionService.cancel(player, "dead");
            }
            if (profile.isLastBroadcastDowned()) {
                MedicalNetworking.broadcastDowned(player, false);
                profile.setLastBroadcastDowned(false);
            }
            profile.markDirty();
            data.bumpRevision();
            return;
        }

        // A bleed-out unconsciousness interrupts any in-progress timed treatment.
        if (profile.hasActiveTreatment()) {
            MedicalActionService.cancel(player, "unconscious");
        }

        // Still have bleed-out budget: cancel death, crawl, keep bleeding until the timer runs out.
        event.setCanceled(true);
        profile.setState(HealthState.UNCONSCIOUS);
        if (profile.getBleedoutSinceTick() < 0L) {
            profile.setBleedoutSinceTick(player.level().getGameTime());
        }
        profile.markDirty();
        data.bumpRevision();
        player.setHealth(1.0F);
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
}
