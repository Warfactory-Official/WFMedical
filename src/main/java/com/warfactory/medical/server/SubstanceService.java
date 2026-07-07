package com.warfactory.medical.server;

import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.PhysiologyParams;
import com.warfactory.medical.core.substance.Substance;
import com.warfactory.medical.core.substance.SubstanceRegistry;
import com.warfactory.medical.network.MedicalNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative application of an injectable {@link Substance} to a player's medical state. Injection
 * is systemic (no limb target); every mutation recomputes derived stats and pushes a full sync immediately.
 */
public final class SubstanceService {

    private SubstanceService() {
    }

    /**
     * Inject {@code substance} into {@code player}. Skips creative/spectator-immune players and honours the
     * {@code enableInjectables} master toggle. Returns whether anything changed so callers only consume the
     * item on success.
     *
     * @return {@code true} when the injection mutated the medical state.
     */
    public static boolean inject(ServerPlayer player, Substance substance) {
        if (player == null || substance == null) {
            return false;
        }
        if (!MedicalConfig.enableInjectables()) {
            return false;
        }
        if ((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            return false;
        }
        // Resolve the live, config-loaded definition (populated from wfmedical_definitions.toml) keyed by the
        // item's registry name so TOML retuning (thresholds, doses, unconsciousness/durations, reversal, ...) actually
        // takes effect. The Substance captured on the item is only the load-order safety net used when the
        // active registry has no matching entry.
        Substance live = SubstanceRegistry.active().get(substance.itemId());
        if (live != null) {
            substance = live;
        }
        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return false;
        }
        MedicalProfile profile = data.getProfile();
        long now = player.level().getGameTime();

        if (substance.antidote()) {
            // Counter-play: reverse the overdose. Drug load drops, the overdose unconsciousness / asphyxia end
            // immediately, and the pain mask collapses to zero so all the previously-suppressed pain comes rushing back.
            profile.setDrugLoad(Math.max(0.0F, profile.getDrugLoad() - substance.reversalAmount()));
            profile.setOverdoseUntilTick(0L);
            profile.setOverdoseUnconscious(false);
            profile.clearAsphyxia();
            profile.setPainSuppression(0.0F);
        } else {
            // Opioid: strong pain immunity + accumulating drug load. Re-dosing to stay pain-free stacks the
            // load toward the overdose threshold, at which point the player goes under for unconsciousTicks —
            // unless a heavy (but not yet lethal) overdose instead trips the asphyxia phase first.
            profile.setPainSuppression(Math.max(profile.getPainSuppression(), substance.painSuppression()));
            profile.setDrugLoad(profile.getDrugLoad() + substance.doseLoad());
            // Timed stimulant / clotting effects (e.g. Combat Stimulant): full strength until now+effectTicks.
            // The beneficial window ends there while the drug LOAD above lingers much longer (the come-down).
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
                if (shouldStartAsphyxia(player, profile)) {
                    // Conscious respiratory-depression precursor: the per-tick breathing hook then drains the
                    // player's air (weakness / heavy slow / blur) and tips them into a FATAL unconsciousness
                    // unless the drug is reversed (naloxone) or decays back below the asphyxia threshold in time.
                    profile.startAsphyxia(now);
                } else {
                    profile.setOverdoseUntilTick(now + substance.unconsciousTicks());
                    profile.setOverdoseUnconscious(true);
                }
            }
        }

        // Optional secondary blood restore.
        if (substance.bloodRestoreMl() > 0.0D && profile.getBloodMl() < profile.getMaxBloodMl()) {
            profile.setBloodMl(profile.getBloodMl() + substance.bloodRestoreMl());
        }

        profile.markDirty();
        data.bumpRevision();

        // Recompute + push onto the vanilla body immediately so the overdose movement lock / pain mask (or,
        // for an antidote, the movement unlock) take effect this instant, then full-sync the client.
        PhysiologyParams params = MedicalConfig.toPhysiologyParams();
        DerivedStats stats = profile.recompute(params);
        if (!((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative())) {
            MedicalEffects.apply(player, stats);
        }
        MedicalNetworking.sendFull(player, profile);
        data.markSynced();
        return true;
    }

    /**
     * True if this overdose should enter the conscious asphyxia phase instead of immediate unconsciousness
     * (requires feature enabled, load in the asphyxia window but below lethal threshold, and a chance roll).
     */
    private static boolean shouldStartAsphyxia(ServerPlayer player, MedicalProfile profile) {
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
        // A severe (lethal-threshold) overdose skips asphyxia and blacks out immediately, keeping the fatal
        // respiratory-depression drain path intact.
        if (MedicalConfig.overdoseLethalEnabled() && MedicalConfig.overdoseLethalThreshold() > 0.0D
                && load >= MedicalConfig.overdoseLethalThreshold()) {
            return false;
        }
        return player.getRandom().nextDouble() < MedicalConfig.asphyxiaChance();
    }
}
