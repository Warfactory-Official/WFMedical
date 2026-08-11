package com.warfactory.medical.server;

import com.warfactory.medical.attachment.IMedicalData;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.PhysiologyParams;
import com.warfactory.medical.core.substance.Substance;
import com.warfactory.medical.core.substance.SubstanceRegistry;
import com.warfactory.medical.network.MedicalNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class SubstanceService {

    private SubstanceService() {
    }

    /** Self-injection convenience: inject {@code player} with the substance. */
    public static boolean inject(ServerPlayer player, Substance substance) {
        if (player == null) {
            return false;
        }
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return false;
        }
        return inject(player, player, data, substance);
    }

    /**
     * Inject {@code target} with the substance (the {@code actor} is the one performing the jab; for a
     * self-injection actor == target). Effects and sync land on the target, not the actor.
     */
    public static boolean inject(ServerPlayer actor, LivingEntity target, IMedicalData targetData, Substance substance) {
        if (actor == null || target == null || targetData == null || substance == null) {
            return false;
        }
        if (!MedicalConfig.enableInjectables()) {
            return false;
        }
        if (target instanceof ServerPlayer tp
                && (tp.isCreative() || tp.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            return false;
        }
        Substance live = SubstanceRegistry.active().get(substance.itemId());
        if (live != null) {
            substance = live;
        }
        MedicalProfile profile = targetData.getProfile();
        long now = target.level().getGameTime();

        if (substance.antidote()) {
            profile.setDrugLoad(Math.max(0.0F, profile.getDrugLoad() - substance.reversalAmount()));
            profile.setStimulant(0.0F);
            profile.setStimulantEndTick(0L);
            profile.setPainSuppression(0.0F);
            boolean sealed = profile.getBlackoutGraceUntil() > 0L || profile.isUnconsciousLatched()
                    || profile.isOverdoseUnconscious() || profile.isAsphyxiaUnconscious();
            profile.setBlackoutGraceUntil(0L);
            profile.setOverdoseUntilTick(0L);
            profile.setOverdoseUnconscious(false);
            profile.clearAsphyxia();
            profile.setUnconsciousLatched(sealed);
        } else {
            profile.setPainSuppression(Math.max(profile.getPainSuppression(), substance.painSuppression()));
            profile.setDrugLoad(profile.getDrugLoad() + substance.doseLoad());
            if (substance.effectTicks() > 0) {
                long end = now + substance.effectTicks();
                if (substance.clottingBoost() > 0.0F) {
                    profile.setClottingBoost(Math.max(profile.getClottingBoost(), substance.clottingBoost()));
                    if (profile.getClottingBoostEndTick() < end) {
                        profile.setClottingBoostEndTick(end);
                    }
                }
                if (substance.stimulantStrength() > 0.0F) {
                    profile.setStimulant(Math.max(profile.getStimulant(), substance.stimulantStrength()));
                    if (profile.getStimulantEndTick() < end) {
                        profile.setStimulantEndTick(end);
                    }
                }
            }
            if (profile.getDrugLoad() >= substance.overdoseThreshold()) {
                if (shouldStartAsphyxia(target, profile)) {
                    profile.startAsphyxia(now);
                } else {
                    int grace = MedicalConfig.blackoutGraceTicks();
                    if (grace <= 0) {
                        profile.setOverdoseUnconscious(true);
                        profile.setUnconsciousLatched(true);
                    } else if (profile.getBlackoutGraceUntil() <= 0L && !profile.isOverdoseUnconscious()) {
                        profile.setBlackoutGraceUntil(now + grace);
                    }
                }
            }
        }

        if (substance.bloodRestoreMl() > 0.0D && profile.getBloodMl() < profile.getMaxBloodMl()) {
            profile.setBloodMl(profile.getBloodMl() + substance.bloodRestoreMl());
        }

        profile.markDirty();
        targetData.bumpRevision();

        PhysiologyParams params = MedicalConfig.toPhysiologyParams();
        DerivedStats stats = profile.recompute(params);
        if (target instanceof ServerPlayer tp) {
            if (!((tp.isCreative() || tp.isSpectator()) && MedicalConfig.effectImmuneInCreative())) {
                MedicalEffects.apply(tp, stats, MedicalConfig.manageNaturalRegen());
            }
            MedicalNetworking.sendFull(tp, profile);
            targetData.markSynced();
        } else {
            MedicalEffects.applyToBody(target, stats);
        }
        return true;
    }

    private static boolean shouldStartAsphyxia(LivingEntity target, MedicalProfile profile) {
        if (!MedicalConfig.asphyxiaEnabled()) {
            return false;
        }
        if (profile.isAsphyxiating() || profile.isOverdoseUnconscious()
                || profile.getState() == HealthState.UNCONSCIOUS || profile.getState() == HealthState.DEAD) {
            return false;
        }
        double load = profile.getDrugLoad();
        if (load < MedicalConfig.asphyxiaThreshold()) {
            return false;
        }
        if (MedicalConfig.overdoseLethalEnabled() && MedicalConfig.overdoseLethalThreshold() > 0.0D
                && load >= MedicalConfig.overdoseLethalThreshold()) {
            return false;
        }
        return target.getRandom().nextDouble() < MedicalConfig.asphyxiaChance();
    }
}
