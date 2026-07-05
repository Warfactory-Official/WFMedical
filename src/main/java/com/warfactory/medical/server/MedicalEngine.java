package com.warfactory.medical.server;

import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.PhysiologyParams;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.network.MedicalNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The scheduled physiology engine. Runs server-side only, on the server tick, but does real work only
 * once every {@link MedicalConfig#updateIntervalTicks()} ticks and only for players whose medical state is
 * actually active (dirty, bleeding, injured, or below full blood). Healthy/pristine players are skipped
 * entirely, and per-limb aggregate caches are rebuilt lazily inside {@link MedicalProfile#recompute}.
 */
public final class MedicalEngine {

    /** Baseline natural regeneration of a minor trauma's severity, per tick, when the type is silent. */
    private static final float DEFAULT_MINOR_REGEN_PER_TICK = 0.0006F;
    /** Slow worsening applied to an untreated major trauma, per tick. */
    private static final float MAJOR_WORSEN_PER_TICK = 0.00015F;
    /** Rate at which painkiller pain-suppression wears off, per tick (a full dose lasts ~tens of seconds). */
    private static final float PAIN_SUPPRESSION_DECAY_PER_TICK = 0.0008F;

    private static int tickCounter;

    private MedicalEngine() {
    }

    /** Forge server-tick hook; keeps the cadence counter and fans out to online players. */
    public static void onServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        int interval = MedicalConfig.updateIntervalTicks();
        if (interval < 1) {
            interval = 1;
        }
        if (++tickCounter < interval) {
            return;
        }
        tickCounter = 0;

        PhysiologyParams params = MedicalConfig.toPhysiologyParams();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (int i = 0; i < players.size(); i++) {
            tickPlayer(players.get(i), params, interval);
        }
    }

    private static void tickPlayer(ServerPlayer player, PhysiologyParams params, int interval) {
        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();

        // Creative immunity: strip our modifiers and skip all penalty processing.
        if ((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            MedicalEffects.clear(player);
            return;
        }

        // Fast path: nothing to simulate for a clean, full-blood, healthy player.
        if (!isActive(profile)) {
            return;
        }

        long nowTick = player.level().getGameTime();

        // (2.0) Advance any active timed treatment; on completion this applies the treatment (marking the
        // profile dirty) and clears itself, so the recompute below picks up the result immediately.
        if (profile.hasActiveTreatment()) {
            MedicalActionService.tick(player, profile, nowTick);
        }

        // (2a) Blood loss from the last cached bleeding aggregate.
        if (MedicalConfig.enableBleeding()) {
            double bleeding = profile.cached().totalBleeding();
            if (bleeding > 0.0D) {
                profile.setBloodMl(profile.getBloodMl() - bleeding * interval);
            }
        }

        // (2b) Minor-trauma regeneration + untreated-major slow worsening + treated-major healing.
        advanceTrauma(profile, interval);

        // (2b.5) Injectable-drug state: blackout timer, slow drug-load decay, severe-overdose health drain.
        advanceSubstances(player, profile, nowTick, interval);

        // (3) Recompute derived stats only when something is dirty (rebuilds only dirty limb caches).
        boolean wasDirty = profile.isDirty();
        DerivedStats stats = wasDirty ? profile.recompute(params) : profile.cached();

        // (2c) Advance the knockdown bleed-out timer using the fresh state.
        advanceKnockdown(player, profile, params, nowTick);

        // (4) Reconcile the vanilla body with the derived snapshot — only when the snapshot actually
        // changed (wasDirty). When nothing changed the attribute modifiers are already correct, so we skip
        // the remove/re-add churn on MAX_HEALTH / MOVEMENT_SPEED for a stable-but-injured player.
        if (wasDirty) {
            MedicalEffects.apply(player, stats);
        }

        // (5) Delta-ish sync: only when the authoritative revision moved past the last sent one.
        if (wasDirty) {
            data.bumpRevision();
        }
        if (data.needsSync()) {
            MedicalNetworking.sendFull(player, profile);
            data.markSynced();
        }

        // (6) Downed-state broadcast: after every physiology transition has settled this tick, edge-detect
        // the passed-out ("downed") predicate and push it to tracking clients only on change. This single
        // hook uniformly covers blackout start/wake AND knockdown enter/exit (both of which force isActive
        // true, so a downed-but-otherwise-idle player is still evaluated here every pass), and it naturally
        // broadcasts false when the engine transitions the player to DEAD (a dead player is not downed).
        reconcileDownedBroadcast(player, profile);
    }

    /**
     * Broadcast the player's current downed state to trackers only when it differs from the last value we
     * sent (tracked by the transient {@link MedicalProfile#isLastBroadcastDowned()} mirror). Cheap no-op
     * on a steady state; a single packet on each enter / exit edge.
     */
    private static void reconcileDownedBroadcast(ServerPlayer player, MedicalProfile profile) {
        boolean nowDowned = profile.isDowned();
        if (nowDowned != profile.isLastBroadcastDowned()) {
            MedicalNetworking.broadcastDowned(player, nowDowned);
            profile.setLastBroadcastDowned(nowDowned);
        }
    }

    /**
     * Advance injectable-drug state each engine pass: keep the transient {@code blackoutActive} flag in
     * sync with the {@code blackoutUntilTick} timer (waking the player + re-syncing when it elapses),
     * decay the accumulated {@code drugLoad}, and apply the severe-overdose respiratory-depression health
     * drain while blacked out (which can be fatal, flowing through the existing death pipeline). Marks the
     * profile dirty on any transition so the recompute re-derives mobility (blackout locks / wake unlocks).
     */
    private static void advanceSubstances(ServerPlayer player, MedicalProfile profile, long nowTick, int interval) {
        // Severe overdose: drug load at/above the lethal line drives the respiratory-depression drain AND
        // sustains the blackout past the fixed timer. Without this, the drain stopped when the timed window
        // elapsed (guarded by isBlackoutActive), so a single dose-stack could never exhaust health before
        // waking; now the player stays unconscious and keeps draining until the load decays back below the
        // lethal threshold or an antidote reverses it — making an untreated severe overdose actually fatal.
        boolean severeOverdose = MedicalConfig.overdoseLethalEnabled()
                && MedicalConfig.overdoseLethalThreshold() > 0.0D
                && profile.getDrugLoad() >= MedicalConfig.overdoseLethalThreshold();

        long until = profile.getBlackoutUntilTick();
        boolean timerBlackout = until > 0L && nowTick < until;
        boolean shouldBlackout = timerBlackout || severeOverdose;
        if (shouldBlackout != profile.isBlackoutActive()) {
            profile.setBlackoutActive(shouldBlackout);
            profile.markDirty();
        }
        // The timer elapsed and no severe overdose is holding the player under: they wake. Clear the timer and
        // mark dirty so mobility is restored + re-synced. A severe overdose defers this until the load decays.
        if (until > 0L && nowTick >= until && !severeOverdose) {
            profile.setBlackoutUntilTick(0L);
            profile.markDirty();
        }

        // Slow drug-load decay over time (setDrugLoad marks the profile dirty when it actually changes).
        float load = profile.getDrugLoad();
        if (load > 0.0F) {
            float decayed = (float) Math.max(0.0D, load - MedicalConfig.drugDecayPerTick() * interval);
            profile.setDrugLoad(decayed);
        }

        // Severe overdose: respiratory-depression drain while blacked out (guarded by the lethal threshold, so
        // it never runs for a stable, non-overdosed player, but keeps running for the whole time the load stays
        // lethal — not just the fixed timer window). Reaching 0 health flows through the vanilla LivingDeath ->
        // knockdown/death pipeline like any other lethal cause.
        if (severeOverdose && profile.isBlackoutActive()) {
            float drain = (float) (MedicalConfig.overdoseLethalDrainPerTick() * interval);
            if (drain > 0.0F) {
                float current = player.getHealth();
                if (current > 0.0F) {
                    player.setHealth(Math.max(0.0F, current - drain));
                }
            }
        }
    }

    /** Allocation-free liveness test; false means the profile can be skipped this pass. */
    private static boolean isActive(MedicalProfile profile) {
        if (profile.isDirty() || profile.getPainSuppression() > 0.0F || profile.hasActiveTreatment()) {
            return true;
        }
        // Keep ticking while a drug is on board or a blackout is in progress so the engine can decay the
        // load and run the blackout timer instead of fast-path skipping the player.
        if (profile.getDrugLoad() > 0.0F || profile.isBlackoutActive() || profile.getBlackoutUntilTick() > 0L) {
            return true;
        }
        DerivedStats c = profile.cached();
        if (c.totalBleeding() > 0.0D || c.totalPain() > 0.0F || c.healthModifier() > 0.0F) {
            return true;
        }
        // Blood only matters once it drops below the penalty threshold; blood is restored via blood bags,
        // not natural regen, so a topped-off-but-not-full player with no other issue is safely skippable
        // (this avoids treating a player who lost a few ml as "active" forever with zero physiological effect).
        if (profile.getBloodMl() < MedicalConfig.bloodLowFraction() * profile.getMaxBloodMl()) {
            return true;
        }
        return c.state() != HealthState.HEALTHY;
    }

    /**
     * Natural minor-trauma regeneration, treated-major healing, and untreated-major worsening. Removes
     * fully-healed trauma and marks touched limbs dirty so their caches rebuild on the next recompute.
     */
    private static void advanceTrauma(MedicalProfile profile, int interval) {
        // Painkillers wear off over time (perceived-pain suppression only; never heals the wound).
        if (profile.getPainSuppression() > 0.0F) {
            profile.setPainSuppression(profile.getPainSuppression() - PAIN_SUPPRESSION_DECAY_PER_TICK * interval);
        }
        for (LimbType lt : LimbType.VALUES) {
            Limb limb = profile.limb(lt);
            List<Trauma> traumas = limb.getTraumas();
            if (traumas.isEmpty()) {
                // Still let a regenerating minor-damage pool tick down.
                if (limb.getMinorDamage() > 0.0F) {
                    limb.setMinorDamage(limb.getMinorDamage() - DEFAULT_MINOR_REGEN_PER_TICK * interval * limb.getMaxHealth());
                    limb.markDirty();
                    profile.markDirty();
                }
                continue;
            }
            boolean changed = false;
            for (int i = traumas.size() - 1; i >= 0; i--) {
                Trauma t = traumas.get(i);
                float typeHeal = t.getType().getHealSpeedPerTick() * interval;
                if (t.isMinor()) {
                    float rate = typeHeal > 0.0F ? typeHeal : DEFAULT_MINOR_REGEN_PER_TICK * interval;
                    t.setSeverity(t.getSeverity() - rate);
                    if (t.getSeverity() <= 0.0F) {
                        traumas.remove(i);
                    }
                    changed = true;
                } else {
                    boolean handled = t.isTreated() || t.isSutured() || t.isStabilized();
                    if (handled) {
                        if (typeHeal > 0.0F) {
                            t.setSeverity(t.getSeverity() - typeHeal);
                            if (t.getSeverity() <= 0.0F && !t.getType().isPermanent()) {
                                traumas.remove(i);
                            }
                            changed = true;
                        }
                    } else if (t.getSeverity() < t.getType().getMaxSeverity()) {
                        // Untreated major trauma slowly worsens.
                        t.setSeverity(t.getSeverity() + MAJOR_WORSEN_PER_TICK * interval);
                        changed = true;
                    }
                }
            }
            if (limb.getMinorDamage() > 0.0F) {
                limb.setMinorDamage(limb.getMinorDamage() - DEFAULT_MINOR_REGEN_PER_TICK * interval * limb.getMaxHealth());
                changed = true;
            }
            if (changed) {
                limb.markDirty();
                profile.markDirty();
            }
        }
    }

    /** Track how long a player has been knocked down and bleed them out once the limit is reached. */
    private static void advanceKnockdown(ServerPlayer player, MedicalProfile profile,
                                         PhysiologyParams params, long nowTick) {
        if (params.knockdownEnabled() && profile.getState() == HealthState.KNOCKED_DOWN) {
            long since = profile.getKnockdownSinceTick();
            if (since < 0L) {
                profile.setKnockdownSinceTick(nowTick);
            } else if (nowTick - since >= params.knockdownBleedoutTicks()) {
                profile.setState(HealthState.DEAD);
                // Drop any admin-forced knockdown so the DEAD state isn't immediately re-forced back to
                // KNOCKED_DOWN by the physiology override on the next recompute (which would block the death).
                profile.setForcedState(null);
                profile.setKnockdownSinceTick(-1L);
                if (profile.hasActiveTreatment()) {
                    MedicalActionService.cancel(player, "dead");
                }
                player.setHealth(0.0F); // bled out; vanilla death pipeline resolves the rest
            }
        } else if (profile.getKnockdownSinceTick() >= 0L) {
            profile.setKnockdownSinceTick(-1L);
        }
    }

    /** Full sync on join/respawn/dimension change: rebuild caches, apply effects, push a snapshot. */
    public static void onPlayerJoin(ServerPlayer player) {
        resync(player);
    }

    /**
     * Authoritative full re-sync of a player's medical state onto the vanilla body and the client, used by
     * join/respawn/dimension-change AND by every admin command mutation so a hand-edited profile updates
     * instantly instead of waiting for the next physiology pass.
     *
     * <p>Recomputes the derived stats, applies them to the vanilla body ({@code allowRaise=true}, so a
     * pristine player is set EXACTLY to the derived current health — e.g. 30/30 rather than the stale
     * vanilla 20/30 — and a freshly-injured one is clamped straight to its lower derived health) unless the
     * player is creative/spectator-immune, bumps the revision, pushes a full snapshot, and finally
     * edge-reconciles the downed broadcast so trackers start/stop rendering the downed pose to match.</p>
     */
    public static void resync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();
        DerivedStats stats = profile.recompute(MedicalConfig.toPhysiologyParams());
        if (!((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative())) {
            // allowRaise=true: set health EXACTLY to the derived current health so a pristine/healed player
            // reads full (30/30) and a freshly-injured one is clamped down to its new derived value.
            MedicalEffects.apply(player, stats, true);
        }
        data.bumpRevision();
        MedicalNetworking.sendFull(player, profile);
        data.markSynced();
        reconcileDownedBroadcast(player, profile);
    }

    /** Cleanup on logout: remove our transient attribute modifiers from the vanilla body. */
    public static void onPlayerLeave(ServerPlayer player) {
        MedicalEffects.clear(player);
        // Clear any lingering downed flag on observers so a player who logs out while downed doesn't leave
        // a stale downed pose on the trackers that still had them rendered.
        IMedicalData data = MedicalCapabilities.get(player);
        if (data != null && data.getProfile().isLastBroadcastDowned()) {
            MedicalNetworking.broadcastDowned(player, false);
            data.getProfile().setLastBroadcastDowned(false);
        }
    }
}
