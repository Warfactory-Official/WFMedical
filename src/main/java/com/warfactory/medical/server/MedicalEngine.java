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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

/**
 * The scheduled physiology engine. Runs server-side only, on the server tick, but does real work only
 * once every {@link MedicalConfig#updateIntervalTicks()} ticks and only for players whose medical state is
 * actually active (dirty, bleeding, injured, or below full blood). Healthy/pristine players are skipped
 * entirely, and per-limb aggregate caches are rebuilt lazily inside {@link MedicalProfile#recompute}.
 */
public final class MedicalEngine {

    /**
     * Baseline natural regeneration of a minor trauma's severity, per tick, when the type is silent.
     */
    private static final float DEFAULT_MINOR_REGEN_PER_TICK = 0.0006F;
    /**
     * Slow worsening applied to an untreated major trauma, per tick.
     */
    private static final float MAJOR_WORSEN_PER_TICK = 0.00015F;
    /**
     * Rate at which the systemic ANALGESIA (painkiller) mask wears off, per tick (a full dose lasts ~tens of
     * seconds).
     */
    private static final float PAIN_SUPPRESSION_DECAY_PER_TICK = 0.0008F;
    /**
     * Rate at which a limb's LOCAL ANESTHETIC wears off, per tick. Slower than the systemic analgesia mask so
     * a targeted block lasts a good while (a full 0.9 dose at the engine cadence lasts ~1.5 minutes).
     */
    private static final float LOCAL_NUMB_DECAY_PER_TICK = 0.0005F;

    private static int tickCounter;

    private MedicalEngine() {
    }

    /**
     * Forge server-tick hook; keeps the cadence counter and fans out to online players.
     */
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

        // A dead / dying player (health <= 0) is still in the player list until they click Respawn. Do NOT run
        // physiology on them: their body is still injured, so a recompute would re-derive UNCONSCIOUS and the
        // >=1 HP pin would raise health back above 0 -> the server then ignores PERFORM_RESPAWN (which requires
        // health <= 0), leaving them stuck in the dying animation with a disabled respawn button. Their profile
        // is discarded on the death-respawn clone anyway.
        if (player.getHealth() <= 0.0F) {
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

        // (2b.5) Injectable-drug state: overdose-unconsciousness timer, slow drug-load decay, severe-overdose health drain.
        advanceSubstances(player, profile, nowTick, interval);

        // (3) Recompute derived stats only when something is dirty (rebuilds only dirty limb caches).
        boolean wasDirty = profile.isDirty();
        DerivedStats stats = wasDirty ? profile.recompute(params) : profile.cached();

        // (3.5) Adrenaline: hold off a PURELY pain-driven knockout for a grace period. When pain reaches
        // knockout level (stats.painKoPending), start / hold the timer while keeping the player conscious; once
        // the grace elapses, flag adrenaline exhausted so the NEXT recompute lets the pain knockout land. If
        // pain eases below that level first, the timer resets and adrenaline recharges. (A blood-loss knockout
        // sets painKoPending=false and so is never delayed here.)
        if (params.adrenalineEnabled()) {
            if (stats.painKoPending()) {
                if (profile.getPainKoSince() <= 0L) {
                    profile.setPainKoSince(nowTick);
                } else if (!profile.isAdrenalineExhausted()
                        && nowTick - profile.getPainKoSince() >= MedicalConfig.adrenalinePainKoDelayTicks()) {
                    profile.setAdrenalineExhausted(true);
                    profile.markDirty();
                }
            } else if (profile.getPainKoSince() > 0L || profile.isAdrenalineExhausted()) {
                profile.setPainKoSince(0L);
                profile.setAdrenalineExhausted(false);
                profile.markDirty();
            }
        }

        // (2c) Keep the overlay's death-progress in sync with how close the blood pool is to the lethal
        // loss threshold, then enact a derived DEATH: bleeding out totally (or having no effective health
        // left) drops the player to 0 HP so the vanilla death pipeline (baseTick -> die ->
        // onLivingDeath.markDead) finalizes. The health-<=0 skip at the top of tickPlayer then leaves the
        // corpse untouched until respawn, so nothing re-pins it back above 0 and blocks the respawn button.
        updateDeathProgress(profile, params);
        if (stats.state() == HealthState.DEAD && player.getHealth() > 0.0F) {
            player.setHealth(0.0F);
        }

        // (4) Reconcile the vanilla body with the derived snapshot — only when the snapshot actually
        // changed (wasDirty). When nothing changed the attribute modifiers are already correct, so we skip
        // the remove/re-add churn on MAX_HEALTH / MOVEMENT_SPEED for a stable-but-injured player.
        if (wasDirty) {
            MedicalEffects.apply(player, stats);
        }

        // (4.5) Broken arm -> weakened melee: apply Weakness while any arm is fractured. Re-applied each pass
        // (short duration) so it outlives the engine cadence and lapses on its own shortly after the fracture
        // is treated. Ranged aim is separately ruined by the client aim-sway floor; this is the melee half.
        int armWeakness = MedicalConfig.brokenArmMeleeWeaknessLevel();
        if (armWeakness > 0 && stats.anyArmFracture()) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, armWeakness - 1, true, false, true));
        }

        // (5) Sync only when the authoritative revision moved past the last sent one. syncTo sends an
        // incremental delta against the client's baseline (or a full snapshot the first time / after a
        // resync re-established the baseline).
        if (wasDirty) {
            data.bumpRevision();
        }
        if (data.needsSync()) {
            MedicalNetworking.syncTo(player, profile);
            data.markSynced();
        }

        // (6) Downed-state broadcast: after every physiology transition has settled this tick, edge-detect
        // the passed-out ("downed") predicate and push it to tracking clients only on change. This single
        // hook uniformly covers overdose start/wake AND bleed-out enter/exit (both of which force isActive
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
            // Swap the vanilla collision box / eye-height between standing and the rotated downed hitbox on the
            // same enter/exit edge the pose is broadcast (PlayerMixin reads the fresh downed state). This keeps
            // the server-authoritative hitbox in sync so a downed player can be hit, and reverts it on wake.
            player.refreshDimensions();
        }
    }

    /**
     * Advance injectable-drug state each engine pass: keep the transient {@code overdoseUnconscious} flag in
     * sync with the {@code overdoseUntilTick} timer (waking the player + re-syncing when it elapses),
     * decay the accumulated {@code drugLoad}, and apply the severe-overdose respiratory-depression health
     * drain while unconscious (which can be fatal, flowing through the existing death pipeline). Marks the
     * profile dirty on any transition so the recompute re-derives mobility (overdose locks / wake unlocks).
     */
    private static void advanceSubstances(ServerPlayer player, MedicalProfile profile, long nowTick, int interval) {
        // Timed stimulant / clotting effects END on their own deadline (default 3 min); the injected drug LOAD
        // lingers LONGER (the come-down / crash), so the buff is gone while the overdose risk is still present.
        if (profile.getStimulant() > 0.0F && nowTick >= profile.getStimulantEndTick()) {
            profile.setStimulant(0.0F);
            profile.markDirty();
        }
        if (profile.getClottingBoost() > 0.0F && nowTick >= profile.getClottingBoostEndTick()) {
            profile.setClottingBoost(0.0F);
            profile.markDirty();
        }

        // Severe overdose: drug load at/above the lethal line drives the respiratory-depression drain AND
        // sustains the unconsciousness past the fixed timer. Without this, the drain stopped when the timed
        // window elapsed (guarded by isOverdoseUnconscious), so a single dose-stack could never exhaust health
        // before waking; now the player stays unconscious and keeps draining until the load decays back below
        // the lethal threshold or an antidote reverses it — making an untreated severe overdose actually fatal.
        boolean severeOverdose = MedicalConfig.overdoseLethalEnabled()
                && MedicalConfig.overdoseLethalThreshold() > 0.0D
                && profile.getDrugLoad() >= MedicalConfig.overdoseLethalThreshold();

        long until = profile.getOverdoseUntilTick();
        boolean timerUnconscious = until > 0L && nowTick < until;
        boolean shouldBeUnconscious = timerUnconscious || severeOverdose;
        if (shouldBeUnconscious != profile.isOverdoseUnconscious()) {
            profile.setOverdoseUnconscious(shouldBeUnconscious);
            profile.markDirty();
        }
        // The timer elapsed and no severe overdose is holding the player under: they wake. Clear the timer and
        // mark dirty so mobility is restored + re-synced. A severe overdose defers this until the load decays.
        if (until > 0L && nowTick >= until && !severeOverdose) {
            profile.setOverdoseUntilTick(0L);
            profile.markDirty();
        }

        // Slow drug-load decay over time (setDrugLoad marks the profile dirty when it actually changes).
        float load = profile.getDrugLoad();
        if (load > 0.0F) {
            float decayed = (float) Math.max(0.0D, load - MedicalConfig.drugDecayPerTick() * interval);
            profile.setDrugLoad(decayed);
        }

        // Severe overdose: respiratory-depression drain while overdose-unconscious (guarded by the lethal
        // threshold, so it never runs for a stable, non-overdosed player, but keeps running for the whole time
        // the load stays lethal — not just the fixed timer window).
        //
        // CRITICAL post-merge interaction: an overdose now raises the state to UNCONSCIOUS, and MedicalEffects
        // pins an UNCONSCIOUS player's health to >= 1 (so a bleed-out / non-lethal overdose stays alive at ~1).
        // A naive "drain toward 0" would therefore be cancelled by that pin and a lethal overdose could never
        // kill. So the drain descends VISIBLY while it stays above the 1-HP pin floor, and the tick on which it
        // would cross that floor is treated as the fatal tick: we transition to DEAD (via the forced-state
        // override so THIS pass's recompute yields DEAD and MedicalEffects stops pinning / early-returns),
        // clear the overdose + bleed-out markers and drop health to 0 — mirroring the bleed-out timer death so
        // the vanilla death pipeline resolves the rest.
        if (severeOverdose && profile.isOverdoseUnconscious() && profile.getState() != HealthState.DEAD) {
            float drain = (float) (MedicalConfig.overdoseLethalDrainPerTick() * interval);
            if (drain > 0.0F) {
                float current = player.getHealth();
                float next = current - drain;
                if (next > 1.0F) {
                    // Still above the unconscious pin floor: show the gradual respiratory-depression descent.
                    player.setHealth(next);
                } else {
                    // Fatal tick: the drain would cross the 1-HP UNCONSCIOUS pin floor -> die now.
                    enactEngineDeath(player, profile);
                }
            }
        }
    }

    /**
     * Per-tick breathing / ASPHYXIA advance, driven from {@link com.warfactory.medical.event.MedicalEventHandler}'s
     * player tick (NOT the throttled engine cadence) so it responds immediately and reliably overrides vanilla's
     * air handling. Asphyxia is NOT physical trauma; it is its own condition with two interchangeable causes:
     * <ul>
     *   <li>DROWNING — underwater with the breath gone (routed here instead of vanilla drowning damage);</li>
     *   <li>DRUG — a heavy opioid overdose (respiratory depression), started probabilistically on injection.</li>
     * </ul>
     * Lifecycle: a conscious STRUGGLE window (heavily slowed, blurred, no sprint/jump), then PASS OUT with a
     * death deadline; the player DIES unless the cause is cleared first (reach the surface / reverse or decay the
     * drug). Clearing the cause at any point recovers cleanly. A cheap no-op for anyone breathing normally.
     *
     * <p>The heavy movement constraint, blur and vignette come from {@link MedicalProfile#isAsphyxiating()} /
     * the UNCONSCIOUS state flowing through the derived snapshot, so this hook only drives air, weakness and the
     * conscious&rarr;unconscious&rarr;death (or recovery) progression.</p>
     */
    public static void tickBreathing(ServerPlayer player, MedicalProfile profile) {
        // Creative/spectator immunity: drop any asphyxia and let air recover.
        if ((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            if (profile.isAsphyxiating() || profile.isAsphyxiaUnconscious()) {
                profile.clearAsphyxia();
                profile.markDirty();
                resync(player);
            }
            return;
        }
        // A dead / dying player is left alone (mirrors tickPlayer's top guard).
        if (player.getHealth() <= 0.0F) {
            return;
        }

        boolean drowning = MedicalConfig.drowningAsphyxiaEnabled()
                && player.isUnderWater() && player.getAirSupply() <= 0;

        // Fast path: nothing to do unless an asphyxia episode is in progress or drowning is about to start one.
        if (!profile.isAsphyxiating() && !profile.isAsphyxiaUnconscious() && !drowning) {
            return;
        }

        long now = player.level().getGameTime();
        // A drug cause SUSTAINS an asphyxia while the overdose stays heavy; naloxone / decay clears it.
        boolean drugCause = MedicalConfig.asphyxiaEnabled()
                && profile.getDrugLoad() >= (float) MedicalConfig.asphyxiaThreshold();
        boolean causeActive = drowning || drugCause;

        // --- Already passed out from asphyxia: recover if the cause has cleared, else die at the deadline.
        if (profile.isAsphyxiaUnconscious()) {
            player.setAirSupply(0); // hold at 0 so vanilla never deals its own drowning damage in the meantime
            if (!causeActive) {
                profile.clearAsphyxia();
                profile.markDirty();
                resync(player); // wakes back up; air recovers naturally
                return;
            }
            if (now >= profile.getAsphyxiaDeadlineTick()) {
                killByAsphyxia(player, profile);
            }
            return;
        }

        // --- Conscious struggle: recover if the cause clears, else drain toward passing out.
        if (profile.isAsphyxiating()) {
            if (!causeActive) {
                profile.clearAsphyxia();
                profile.markDirty();
                resync(player); // recovered before passing out; air recovers naturally
                return;
            }
            // Debilitating weakness while fighting for air (short + re-applied each tick, no particles).
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40,
                    MedicalConfig.asphyxiaWeaknessAmplifier(), true, false, true));
            // Drain the air bar for feedback, then hold it at 0 so vanilla never applies drowning damage.
            int air = player.getAirSupply();
            player.setAirSupply(air > 0 ? Math.max(0, air - MedicalConfig.asphyxiaAirLossPerTick()) : 0);
            // Struggle window elapsed -> pass out; a death deadline starts ticking.
            if (now - profile.getAsphyxiaSince() >= MedicalConfig.asphyxiaStruggleTicks()) {
                player.setAirSupply(0);
                profile.setAsphyxiating(false);
                profile.setAsphyxiaUnconscious(true);
                profile.setAsphyxiaDeadlineTick(now + MedicalConfig.asphyxiaUnconsciousTicks());
                profile.markDirty();
                resync(player);
            }
            return;
        }

        // --- Not yet asphyxiating: DROWNING starts an episode here. (A drug asphyxia is instead started,
        // probabilistically, by SubstanceService on injection so the asphyxiaChance roll still governs it.)
        if (drowning) {
            profile.startAsphyxia(now);
            profile.markDirty();
            resync(player);
        }
    }

    /**
     * Enact an asphyxia death: force the DEAD state (so a recompute agrees and {@link MedicalEffects} stops
     * pinning the unconscious health floor), clear the asphyxia + bleed-out markers, cancel any treatment, and
     * drop health to 0 so the vanilla death pipeline resolves the rest. Mirrors the severe-overdose lethal path.
     */
    private static void killByAsphyxia(ServerPlayer player, MedicalProfile profile) {
        enactEngineDeath(player, profile);
    }

    /**
     * Engine-driven death enactment (severe-overdose drain and the asphyxia deadline): clear the full union
     * of downed markers via {@link MedicalProfile#enterDeadState(boolean)} (pinned so recomputes agree until
     * the vanilla death event fires), cancel any treatment, and drop health to 0.
     */
    private static void enactEngineDeath(ServerPlayer player, MedicalProfile profile) {
        profile.enterDeadState(true);
        if (profile.hasActiveTreatment()) {
            MedicalActionService.cancel(player, "dead");
        }
        player.setHealth(0.0F);
    }

    /**
     * Allocation-free liveness test; false means the profile can be skipped this pass.
     */
    private static boolean isActive(MedicalProfile profile) {
        if (profile.isDirty() || profile.getPainSuppression() > 0.0F || profile.hasActiveTreatment()) {
            return true;
        }
        // Keep ticking while a drug is on board or an overdose unconsciousness is in progress so the engine can
        // decay the load and run the overdose-unconsciousness timer instead of fast-path skipping the player.
        // Also keep ticking while any localized numbing is decaying, or an adrenaline pain-KO timer is running.
        if (profile.getDrugLoad() > 0.0F || profile.isOverdoseUnconscious() || profile.getOverdoseUntilTick() > 0L
                || profile.anyLocalNumbing() || profile.getPainKoSince() > 0L || profile.isAdrenalineExhausted()
                || profile.isAsphyxiating() || profile.isAsphyxiaUnconscious()
                || profile.getStimulant() > 0.0F || profile.getClottingBoost() > 0.0F) {
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
        // A clotting boost (hemostatic / combat stimulant) raises BOTH the severity a bleed can self-clot at
        // AND the speed it clots. Computed once for this player and folded into the self-heal branch below.
        float clot = profile.getClottingBoost();
        double selfHealThreshold = MedicalConfig.bleedingSelfHealThreshold();
        double selfHealRate = MedicalConfig.bleedingSelfHealRate();
        if (clot > 0.0F) {
            selfHealThreshold = Math.min(1.0, selfHealThreshold + clot * MedicalConfig.clottingBoostThresholdBonus());
            selfHealRate *= (1.0 + clot * MedicalConfig.clottingBoostRateMultiplier());
        }
        for (LimbType lt : LimbType.VALUES) {
            Limb limb = profile.limb(lt);
            // Localized anesthetic wears off over time (per limb), regardless of whether the limb has trauma.
            if (limb.getLocalNumbing() > 0.0F) {
                limb.setLocalNumbing(limb.getLocalNumbing() - LOCAL_NUMB_DECAY_PER_TICK * interval);
                profile.markDirty();
            }
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
                    } else {
                        // Untreated MAJOR trauma evolves on its own:
                        //  - a bleeding wound the body can keep up with (severity at/below the self-heal
                        //    threshold, default 0.3) slowly CLOTS -- the wound closes and its bleeding fades;
                        //  - a FRACTURE slowly KNITS over a long time (fractureSelfHealMinutes, default 20 min),
                        //    even untreated -- splinting just makes it faster (the treated branch above);
                        //  - anything else (a severe bleed) slowly WORSENS until it is treated.
                        boolean bleeds = t.getType().getBleedingPerSeverity() > 0.0F;
                        double fractureMinutes = MedicalConfig.fractureSelfHealMinutes();
                        if (bleeds && t.getSeverity() <= (float) selfHealThreshold) {
                            t.setSeverity(t.getSeverity() - (float) selfHealRate * interval);
                            if (t.getSeverity() <= 0.0F && !t.getType().isPermanent()) {
                                traumas.remove(i);
                            }
                            changed = true;
                        } else if (t.isFracture() && fractureMinutes > 0.0) {
                            // A full-severity fracture heals in fractureMinutes real minutes (1200 ticks/min);
                            // a partial one heals proportionally faster.
                            float rate = (float) (1.0 / (fractureMinutes * 1200.0));
                            t.setSeverity(t.getSeverity() - rate * interval);
                            if (t.getSeverity() <= 0.0F && !t.getType().isPermanent()) {
                                traumas.remove(i);
                            }
                            changed = true;
                        } else if (t.getSeverity() < t.getType().getMaxSeverity()) {
                            t.setSeverity(t.getSeverity() + MAJOR_WORSEN_PER_TICK * interval);
                            changed = true;
                        }
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

    /**
     * Recompute the client overlay's {@code deathProgress} (0..1) from how close the blood pool is to the
     * lethal loss threshold: 0 at (or above) the unconsciousness-loss fraction, ramping to 1 at the
     * death-loss fraction. The unconscious overlay uses this to ramp its extreme vignette into a full-screen
     * blackout ONLY right before a bleed-out death (blood loss nearing the fatal fraction); an overdose
     * unconsciousness that isn't losing blood keeps this at 0 and so never blacks out (it recovers instead).
     *
     * <p>Actual death is enacted by the caller (drop to 0 HP when {@link Physiology} derives {@code DEAD});
     * the fixed bleed-out timer was replaced by this blood-fraction model per the score-based design.</p>
     */
    private static void updateDeathProgress(MedicalProfile profile, PhysiologyParams params) {
        double maxBlood = profile.getMaxBloodMl();
        double lossFraction = maxBlood <= 0.0D ? 0.0D : 1.0D - (profile.getBloodMl() / maxBlood);
        double start = params.bloodUnconsciousLossFraction();
        double span = params.bloodDeathLossFraction() - start;
        float progress;
        if (span <= 0.0D) {
            progress = lossFraction >= params.bloodDeathLossFraction() ? 1.0F : 0.0F;
        } else {
            progress = (float) ((lossFraction - start) / span);
        }
        profile.setDeathProgress(progress); // setter clamps to [0,1] and only dirties on change
    }

    /**
     * Full sync on join/respawn/dimension change: rebuild caches, apply effects, push a snapshot.
     */
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
     *
     * <p>This entry point derives creative-immunity from the LIVE {@code player.isCreative()/isSpectator()}
     * gamemode, which is correct for the tick/join/command callers (where the gamemode is already settled).
     * A gamemode-transition caller, where those flags still reflect the pre-switch mode, must instead use
     * {@link #resync(ServerPlayer, boolean)} with an immunity decision computed from the NEW mode.</p>
     */
    public static void resync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean applyEffects = !((player.isCreative() || player.isSpectator())
                && MedicalConfig.effectImmuneInCreative());
        resync(player, applyEffects);
    }

    /**
     * Authoritative full re-sync with an EXPLICIT decision on whether to reconcile the vanilla body with the
     * derived stats, bypassing the internal {@code isCreative()/isSpectator()} probe.
     *
     * <p>Needed by the gamemode-change handler: {@link net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangeGameModeEvent}
     * fires BEFORE the switch is applied, so at that moment {@code player.isCreative()/isSpectator()} still
     * report the OLD mode. On a creative/spectator&nbsp;-&gt;&nbsp;survival transition the caller has already
     * computed the authoritative immunity from {@code getNewGameMode()} and passes {@code applyEffects=true};
     * the stale live-mode probe used by {@link #resync(ServerPlayer)} would instead wrongly keep treating the
     * player as creative-immune and skip re-adding the +10 MAX_HEALTH modifier, leaving max health stuck at
     * the vanilla 20 for the rest of the session (a healthy player is then skipped by the engine fast-path).</p>
     *
     * <p>When {@code applyEffects} is false the effects are NOT applied (the caller has determined the player
     * is creative/spectator-immune), matching {@link MedicalEffects#clear}-based immunity; the snapshot and
     * downed-broadcast reconciliation still run so the client and trackers stay consistent either way.</p>
     *
     * @param player       the server player to re-sync; a {@code null} player is a safe no-op
     * @param applyEffects {@code true} to reconcile the vanilla body with the derived stats
     *                     ({@code allowRaise=true}); {@code false} to leave the body untouched (immune)
     */
    public static void resync(ServerPlayer player, boolean applyEffects) {
        if (player == null) {
            return;
        }
        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();
        DerivedStats stats = profile.recompute(MedicalConfig.toPhysiologyParams());
        if (applyEffects) {
            // allowRaise=true: set health EXACTLY to the derived current health so a pristine/healed player
            // reads full (30/30) and a freshly-injured one is clamped down to its new derived value.
            MedicalEffects.apply(player, stats, true);
        }
        data.bumpRevision();
        MedicalNetworking.sendFull(player, profile);
        data.markSynced();
        reconcileDownedBroadcast(player, profile);
    }

    /**
     * Cleanup on logout: remove our transient attribute modifiers from the vanilla body.
     */
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
