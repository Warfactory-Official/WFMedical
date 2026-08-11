package com.warfactory.medical.server;

import com.warfactory.medical.attachment.IMedicalData;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.damage.HitDetectionDebug;
import com.warfactory.medical.core.PhysiologyParams;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.damage.DamageSources;
import com.warfactory.medical.network.MedicalNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;

import java.util.List;
import java.util.UUID;

public final class MedicalEngine {

    private static final float DEFAULT_MINOR_REGEN_PER_TICK = 0.0006F;
    private static final float MAJOR_WORSEN_PER_TICK = 0.00015F;
    // Systemic pain suppression wears off linearly: morphine (1.0) over 10 min, painkillers (0.5) over 5 min (20 ticks/s).
    private static final float PAIN_SUPPRESSION_DECAY_PER_TICK = 1.0F / (10 * 60 * 20);
    // Local anesthetic (0.9) limb numbing wears off over ~8 min.
    private static final float LOCAL_NUMB_DECAY_PER_TICK = 0.9F / (8 * 60 * 20);

    private static int tickCounter;

    private MedicalEngine() {
    }

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
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();

        if ((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            MedicalEffects.clear(player);
            return;
        }

        if (player.getHealth() <= 0.0F) {
            return;
        }

        if (!isActive(profile)) {
            return;
        }

        long nowTick = player.level().getGameTime();

        if (profile.hasActiveTreatment()) {
            MedicalActionService.tick(player, profile, nowTick);
        }

        double bleeding = MedicalConfig.enableBleeding() ? profile.cached().totalBleeding() : 0.0D;
        if (bleeding > 0.0D) {
            profile.setBloodMl(profile.getBloodMl() - bleeding * interval);
        }

        double bloodRegenPerSecond = MedicalConfig.bloodRegenMlPerSecond();
        // Only regenerate once bleeding is fully under control: any active haemorrhage (even one merely
        // slowed by a tourniquet, which never drops to zero) pauses regen entirely. Stop the bleed first,
        // then the body rebuilds volume. setBloodMl clamps at max, so this stops cleanly once topped up.
        if (bloodRegenPerSecond > 0.0D && bleeding <= 0.0D && profile.getBloodMl() < profile.getMaxBloodMl()) {
            // interval is ticks since the last update; 20 ticks = 1s.
            profile.setBloodMl(profile.getBloodMl() + bloodRegenPerSecond / 20.0D * interval);
        }

        advanceTrauma(profile, interval);

        advanceSubstances(player, profile, nowTick, interval);

        attemptWake(player, profile);

        boolean wasDirty = profile.isDirty();
        HealthState beforeState = profile.getState();
        DerivedStats stats = wasDirty ? profile.recompute(params) : profile.cached();
        if (MedicalConfig.logHitDetection() && stats.state() != beforeState) {
            HitDetectionDebug.logStateTransition(player, beforeState, stats.state(), nowTick);
        }

        if (stats.state() == HealthState.UNCONSCIOUS && profile.getForcedState() == null
                && !profile.isUnconsciousLatched()) {
            profile.setUnconsciousLatched(true);
        }

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

        updateDeathProgress(profile, params);
        if (stats.state() == HealthState.DEAD && player.getHealth() > 0.0F) {
            killByBleedingOut(player, profile);
        }

        if (wasDirty) {
            // When WFMedical owns regen it drives health UP to the medical value too (not just down), so it
            // no longer relies on vanilla natural regen for recovery. See MedicalEventHandler#onLivingHeal.
            MedicalEffects.apply(player, stats, MedicalConfig.manageNaturalRegen());
        }

        int armWeakness = MedicalConfig.brokenArmMeleeWeaknessLevel();
        if (armWeakness > 0 && stats.anyArmFracture()) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, armWeakness - 1, true, false, true));
        }

        if (wasDirty) {
            data.bumpRevision();
        }
        if (data.needsSync()) {
            MedicalNetworking.syncTo(player, profile);
            data.markSynced();
        }

        reconcileDownedBroadcast(player, profile);
    }

    private static void reconcileDownedBroadcast(ServerPlayer player, MedicalProfile profile) {
        boolean nowDowned = profile.isDowned();
        if (nowDowned != profile.isLastBroadcastDowned()) {
            MedicalNetworking.broadcastDowned(player, nowDowned);
            profile.setLastBroadcastDowned(nowDowned);
            player.refreshDimensions();
        }
    }

    private static void advanceSubstances(ServerPlayer player, MedicalProfile profile, long nowTick, int interval) {
        if (profile.getStimulant() > 0.0F && nowTick >= profile.getStimulantEndTick()) {
            profile.setStimulant(0.0F);
            profile.markDirty();
        }
        if (profile.getClottingBoost() > 0.0F && nowTick >= profile.getClottingBoostEndTick()) {
            profile.setClottingBoost(0.0F);
            profile.markDirty();
        }

        boolean severeOverdose = MedicalConfig.overdoseLethalEnabled()
                && MedicalConfig.overdoseLethalThreshold() > 0.0D
                && profile.getDrugLoad() >= MedicalConfig.overdoseLethalThreshold();

        long grace = profile.getBlackoutGraceUntil();
        if (grace > 0L) {
            if (nowTick >= grace) {
                profile.setBlackoutGraceUntil(0L);
                profile.setOverdoseUnconscious(true);
                profile.setUnconsciousLatched(true);
                profile.markDirty();
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, true, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, true, false, true));
            }
        }

        float load = profile.getDrugLoad();
        if (load > 0.0F) {
            float decayed = (float) Math.max(0.0D, load - MedicalConfig.drugDecayPerTick() * interval);
            profile.setDrugLoad(decayed);
        }

        if (severeOverdose && profile.isOverdoseUnconscious() && profile.getState() != HealthState.DEAD) {
            float drain = (float) (MedicalConfig.overdoseLethalDrainPerTick() * interval);
            if (drain > 0.0F) {
                float current = player.getHealth();
                float next = current - drain;
                if (next > 1.0F) {
                    player.setHealth(next);
                } else {
                    killByOverdose(player, profile);
                }
            }
        }
    }

    private static void attemptWake(ServerPlayer player, MedicalProfile profile) {
        if (!profile.isUnconsciousLatched()) {
            return;
        }
        if (profile.cached().state() != HealthState.UNCONSCIOUS) {
            return;
        }
        boolean severeOverdose = MedicalConfig.overdoseLethalEnabled()
                && MedicalConfig.overdoseLethalThreshold() > 0.0D
                && profile.getDrugLoad() >= MedicalConfig.overdoseLethalThreshold();
        if (severeOverdose || profile.isAsphyxiaUnconscious()) {
            return;
        }
        DerivedStats stats = profile.cached();
        if (stats.effectiveMaxHealth() <= 0.0F) {
            return;
        }
        if (wakeupScore(profile, stats) > MedicalConfig.wakeupScoreThreshold()) {
            return;
        }
        if (player.getRandom().nextFloat() < (float) MedicalConfig.wakeChance()) {
            wakeUp(profile);
        }
    }

    private static void wakeUp(MedicalProfile profile) {
        profile.setUnconsciousLatched(false);
        profile.setOverdoseUnconscious(false);
        profile.setOverdoseUntilTick(0L);
        profile.setBlackoutGraceUntil(0L);
        profile.clearAsphyxia();
        profile.setPainKoSince(0L);
        profile.setAdrenalineExhausted(false);
        profile.markDirty();
    }

    public static double wakeupScore(MedicalProfile profile, DerivedStats stats) {
        double maxBlood = profile.getMaxBloodMl();
        double lossFraction = maxBlood <= 0.0D ? 0.0D : 1.0D - (profile.getBloodMl() / maxBlood);
        if (lossFraction < 0.0D) {
            lossFraction = 0.0D;
        }
        double bloodUnc = MedicalConfig.bloodUnconsciousLossFraction();
        double bloodScore = bloodUnc <= 0.0D ? 0.0D : clamp01(lossFraction / bloodUnc);

        float painShock = MedicalConfig.painShockThreshold();
        float painUnc = MedicalConfig.painUnconsciousThreshold();
        double painScore = 0.0D;
        float painSpan = painUnc - painShock;
        if (painSpan > 0.0F && stats.systemicPain() > painShock) {
            painScore = clamp01((stats.systemicPain() - painShock) / painSpan);
        }

        double lethal = MedicalConfig.overdoseLethalThreshold();
        double drugScore = lethal <= 0.0D ? 0.0D : clamp01(profile.getDrugLoad() / lethal);

        double bleedRef = MedicalConfig.wakeupBleedReference();
        double bleedScore = bleedRef <= 0.0D ? 0.0D : clamp01(stats.totalBleeding() / bleedRef);

        return MedicalConfig.wakeupBloodWeight() * bloodScore
                + MedicalConfig.wakeupPainWeight() * painScore
                + MedicalConfig.wakeupDrugWeight() * drugScore
                + MedicalConfig.wakeupBleedWeight() * bleedScore;
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
    }

    public static void tickBreathing(ServerPlayer player, MedicalProfile profile) {
        if ((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            if (profile.isAsphyxiating() || profile.isAsphyxiaUnconscious()) {
                profile.clearAsphyxia();
                profile.markDirty();
                resync(player);
            }
            return;
        }
        if (player.getHealth() <= 0.0F) {
            return;
        }

        boolean drowning = MedicalConfig.drowningAsphyxiaEnabled()
                && player.isUnderWater() && player.getAirSupply() <= 0;

        if (!profile.isAsphyxiating() && !profile.isAsphyxiaUnconscious() && !drowning) {
            return;
        }

        long now = player.level().getGameTime();
        boolean drugCause = MedicalConfig.asphyxiaEnabled()
                && profile.getDrugLoad() >= (float) MedicalConfig.asphyxiaThreshold();
        boolean causeActive = drowning || drugCause;

        if (profile.isAsphyxiaUnconscious()) {
            player.setAirSupply(0);
            if (!causeActive) {
                profile.clearAsphyxia();
                profile.markDirty();
                resync(player);
                return;
            }
            if (now >= profile.getAsphyxiaDeadlineTick()) {
                killByAsphyxia(player, profile);
            }
            return;
        }

        if (profile.isAsphyxiating()) {
            if (!causeActive) {
                profile.clearAsphyxia();
                profile.markDirty();
                resync(player);
                return;
            }
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40,
                    MedicalConfig.asphyxiaWeaknessAmplifier(), true, false, true));
            int air = player.getAirSupply();
            player.setAirSupply(air > 0 ? Math.max(0, air - MedicalConfig.asphyxiaAirLossPerTick()) : 0);
            if (now - profile.getAsphyxiaSince()
                    >= MedicalConfig.asphyxiaStruggleTicks() + MedicalConfig.blackoutGraceTicks()) {
                player.setAirSupply(0);
                profile.setAsphyxiating(false);
                profile.setAsphyxiaUnconscious(true);
                profile.setUnconsciousLatched(true);
                profile.setAsphyxiaDeadlineTick(now + MedicalConfig.asphyxiaUnconsciousTicks());
                profile.markDirty();
                resync(player);
            }
            return;
        }

        if (drowning) {
            profile.startAsphyxia(now);
            profile.markDirty();
            resync(player);
        }
    }

    public static void giveUp(ServerPlayer player) {
        if (player == null || !MedicalConfig.enableGiveUp()) {
            return;
        }
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();
        if (!profile.isDowned() || player.getHealth() <= 0.0F) {
            return;
        }
        killByGivingUp(player, profile);
    }

    private static void killByBleedingOut(ServerPlayer player, MedicalProfile profile) {
        kill(player, profile, DamageSources.bleedingOut(player.level()));
    }

    private static void killByGivingUp(ServerPlayer player, MedicalProfile profile) {
        kill(player, profile, DamageSources.givingUp(player.level()));
    }

    private static void killByAsphyxia(ServerPlayer player, MedicalProfile profile) {
        kill(player, profile, DamageSources.asphyxiation(player.level()));
    }

    private static void killByOverdose(ServerPlayer player, MedicalProfile profile) {
        kill(player, profile, DamageSources.overdose(player.level()));
    }

    private static void kill(ServerPlayer player, MedicalProfile profile, DamageSource source) {
        profile.enterDeadState(true);
        if (profile.hasActiveTreatment()) {
            MedicalActionService.cancel(player, "dead");
        }
        creditLastDamagingPlayer(player, profile);
        player.hurt(source, Float.MAX_VALUE);
    }

    private static void creditLastDamagingPlayer(ServerPlayer player, MedicalProfile profile) {
        UUID uuid = profile.getLastDamagingPlayer();
        if (uuid == null) {
            return;
        }
        if (player.level().getGameTime() - profile.getLastDamageTick() > MedicalConfig.deathAttributionWindowTicks()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerPlayer killer = server.getPlayerList().getPlayer(uuid);
        if (killer != null && killer != player) {
            player.setLastHurtByPlayer(killer);
        }
    }

    private static boolean isActive(MedicalProfile profile) {
        if (profile.isDirty() || profile.getPainSuppression() > 0.0F || profile.hasActiveTreatment()) {
            return true;
        }
        if (profile.getDrugLoad() > 0.0F || profile.isOverdoseUnconscious() || profile.getOverdoseUntilTick() > 0L
                || profile.isUnconsciousLatched() || profile.getBlackoutGraceUntil() > 0L
                || profile.anyLocalNumbing() || profile.getPainKoSince() > 0L || profile.isAdrenalineExhausted()
                || profile.isAsphyxiating() || profile.isAsphyxiaUnconscious()
                || profile.getStimulant() > 0.0F || profile.getClottingBoost() > 0.0F) {
            return true;
        }
        DerivedStats c = profile.cached();
        if (c.totalBleeding() > 0.0D || c.totalPain() > 0.0F || c.healthModifier() > 0.0F) {
            return true;
        }
        if (profile.getBloodMl() < MedicalConfig.bloodLowFraction() * profile.getMaxBloodMl()) {
            return true;
        }
        // Keep ticking a not-yet-full player so natural blood regen can top them back up to max (the
        // blood-low check above only covers the deep-loss band; regen also needs to run in 60%..100%).
        if (MedicalConfig.bloodRegenMlPerSecond() > 0.0D && profile.getBloodMl() < profile.getMaxBloodMl()) {
            return true;
        }
        return c.state() != HealthState.HEALTHY;
    }

    private static void advanceTrauma(MedicalProfile profile, int interval) {
        if (profile.getPainSuppression() > 0.0F) {
            profile.setPainSuppression(profile.getPainSuppression() - PAIN_SUPPRESSION_DECAY_PER_TICK * interval);
        }
        float clot = profile.getClottingBoost();
        double selfHealThreshold = MedicalConfig.bleedingSelfHealThreshold();
        double selfHealRate = MedicalConfig.bleedingSelfHealRate();
        if (clot > 0.0F) {
            selfHealThreshold = Math.min(1.0, selfHealThreshold + clot * MedicalConfig.clottingBoostThresholdBonus());
            selfHealRate *= (1.0 + clot * MedicalConfig.clottingBoostRateMultiplier());
        }
        for (LimbType lt : LimbType.VALUES) {
            Limb limb = profile.limb(lt);
            if (limb.getLocalNumbing() > 0.0F) {
                limb.setLocalNumbing(limb.getLocalNumbing() - LOCAL_NUMB_DECAY_PER_TICK * interval);
                profile.markDirty();
            }
            List<Trauma> traumas = limb.getTraumas();
            if (traumas.isEmpty()) {
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
                    boolean handled = t.isTreated() || t.isStabilized();
                    if (handled) {
                        if (typeHeal > 0.0F) {
                            t.setSeverity(t.getSeverity() - typeHeal);
                            if (t.getSeverity() <= 0.0F && !t.getType().isPermanent()) {
                                traumas.remove(i);
                            }
                            changed = true;
                        }
                    } else if (t.isBleedControlledOnly()) {
                        // Bleeding is dressed but the wound itself is untreated (e.g. a crush): it is
                        // frozen -- no self-clot, no worsening, no regeneration -- and keeps contributing
                        // pain and health reduction until a suture or medkit actually treats it.
                        continue;
                    } else {
                        boolean bleeds = t.getType().getBleedingPerSeverity() > 0.0F;
                        double fractureMinutes = MedicalConfig.fractureSelfHealMinutes();
                        if (bleeds && t.getSeverity() <= (float) selfHealThreshold) {
                            t.setSeverity(t.getSeverity() - (float) selfHealRate * interval);
                            if (t.getSeverity() <= 0.0F && !t.getType().isPermanent()) {
                                traumas.remove(i);
                            }
                            changed = true;
                        } else if (t.isFracture() && fractureMinutes > 0.0) {
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
        profile.setDeathProgress(progress);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        resync(player);
    }

    public static void resync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean applyEffects = !((player.isCreative() || player.isSpectator())
                && MedicalConfig.effectImmuneInCreative());
        resync(player, applyEffects);
    }

    public static void resync(ServerPlayer player, boolean applyEffects) {
        if (player == null) {
            return;
        }
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();
        DerivedStats stats = profile.recompute(MedicalConfig.toPhysiologyParams());
        if (applyEffects) {
            MedicalEffects.apply(player, stats, true);
        }
        data.bumpRevision();
        MedicalNetworking.sendFull(player, profile);
        data.markSynced();
        reconcileDownedBroadcast(player, profile);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        MedicalEffects.clear(player);
        IMedicalData data = MedicalAttachments.get(player);
        if (data != null && data.getProfile().isLastBroadcastDowned()) {
            MedicalNetworking.broadcastDowned(player, false);
            data.getProfile().setLastBroadcastDowned(false);
        }
    }
}
