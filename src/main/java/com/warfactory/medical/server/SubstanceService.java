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
 * Server-authoritative application of an injectable {@link Substance} to a player's medical state.
 *
 * <p>Injection is SYSTEMIC (no limb target). An opioid raises the perceived-pain mask and adds to the
 * accumulating {@code drugLoad}; crossing the substance's overdose threshold renders the player unconscious
 * (locks movement / jump via the derived-movement path and shows a black screen client-side). An antidote
 * slams {@code drugLoad} down, ends the overdose unconsciousness immediately and drops pain suppression to
 * zero (pain rushes back). Every mutation recomputes the derived stats and pushes them onto the vanilla body
 * this instant,
 * then sends a full sync so the client reacts without waiting for the next engine pass.</p>
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
            profile.setAsphyxiating(false);
            profile.setPainSuppression(0.0F);
        } else {
            // Opioid: strong pain immunity + accumulating drug load. Re-dosing to stay pain-free stacks the
            // load toward the overdose threshold, at which point the player goes under for unconsciousTicks —
            // unless a heavy (but not yet lethal) overdose instead trips the asphyxia phase first.
            profile.setPainSuppression(Math.max(profile.getPainSuppression(), substance.painSuppression()));
            profile.setDrugLoad(profile.getDrugLoad() + substance.doseLoad());
            if (profile.getDrugLoad() >= substance.overdoseThreshold()) {
                if (shouldStartAsphyxia(player, profile)) {
                    // Conscious respiratory-depression precursor: the per-tick engine hook drains the player's
                    // air (weakness / no sprint / blur) and tips them into unconsciousness when it runs out.
                    profile.setAsphyxiating(true);
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
     * Whether this heavy overdose injection should trip the conscious ASPHYXIA phase instead of an immediate
     * overdose unconsciousness. Requires the feature enabled, the drug load in the heavy window (at/above
     * {@code asphyxiaThreshold} but below the lethal threshold, since a severe overdose goes straight under),
     * the player still conscious and not already asphyxiating, and a successful {@code asphyxiaChance} roll.
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
