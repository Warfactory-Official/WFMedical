package com.warfactory.medical.server;

import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.treatment.Treatment;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.network.MedicalNetworking;
import com.warfactory.medical.network.TraumaDeltaPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative application of a {@link Treatment} to a player's medical state.
 *
 * <p>The server picks the target trauma (clients never do); the item only consumes when {@link #apply}
 * returns {@code true}. Selection favours the most relevant untreated wound for the action (bleeding for
 * REDUCE_BLEEDING/SUTURE_WOUND, fractures for STABILIZE_FRACTURE, matching category otherwise), breaking
 * ties by severity.</p>
 */
public final class TreatmentService {

    private static final float DEFAULT_HEAL_MAGNITUDE = 0.25F;
    /**
     * Score bonus applied to a trauma sitting on the UI-selected limb so the hint wins over severity.
     */
    private static final float LIMB_HINT_BONUS = 1000.0F;

    private TreatmentService() {
    }

    /**
     * Apply {@code treatment} to {@code player} with no limb preference (server auto-picks the most
     * relevant wound). Returns whether anything changed so callers only consume the item on success.
     */
    public static boolean apply(ServerPlayer player, Treatment treatment) {
        return applyTargeted(player, treatment, null);
    }

    /**
     * Apply {@code treatment} to {@code player}, strongly preferring trauma on {@code limbHint} when
     * picking the target. Behaves exactly like {@link #apply(ServerPlayer, Treatment)} when
     * {@code limbHint} is {@code null}. Returns whether anything changed so callers only consume the item
     * on success.
     *
     * @param limbHint the limb the player selected in the UI (nullable = no preference)
     */
    public static boolean applyTargeted(ServerPlayer player, Treatment treatment, LimbType limbHint) {
        if (player == null || treatment == null) {
            return false;
        }
        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return false;
        }
        MedicalProfile profile = data.getProfile();
        TreatmentAction action = treatment.action();

        // RESTORE_BLOOD acts on the blood pool, not a specific trauma.
        if (action == TreatmentAction.RESTORE_BLOOD) {
            double before = profile.getBloodMl();
            if (treatment.bloodRestoreMl() <= 0.0D || before >= profile.getMaxBloodMl()) {
                return false;
            }
            profile.setBloodMl(before + treatment.bloodRestoreMl());
            boolean changed = profile.getBloodMl() != before;
            if (changed) {
                data.bumpRevision();
                MedicalNetworking.sendDelta(player,
                        new TraumaDeltaPacket(TraumaDeltaPacket.Op.CHANGED, LimbType.TORSO, "", 0.0F));
            }
            return changed;
        }

        // Generalized: a systemic painkiller / analgesia masks pain across the WHOLE body and ignores any
        // selected limb (contrast NUMB_LIMB below — a locally-applied anesthetic that must be aimed at a limb).
        if (action == TreatmentAction.REDUCE_PAIN) {
            float mag = treatment.magnitude() > 0.0F ? treatment.magnitude() : DEFAULT_HEAL_MAGNITUDE;
            float before = profile.getPainSuppression();
            profile.setPainSuppression(Math.max(before, mag));
            boolean changed = profile.getPainSuppression() != before;
            if (changed) {
                data.bumpRevision();
                MedicalNetworking.sendDelta(player,
                        new TraumaDeltaPacket(TraumaDeltaPacket.Op.CHANGED, LimbType.TORSO, "", 0.0F));
            }
            return changed;
        }

        //Localized
        if (action == TreatmentAction.NUMB_LIMB) {
            // A locally-applied anesthetic must be AIMED: it numbs only the selected limb (no whole-body
            // effect, no auto-pick). With no limb selected it does nothing (and the item is not consumed).
            if (limbHint == null) {
                return false;
            }
            float mag = treatment.magnitude() > 0.0F ? treatment.magnitude() : DEFAULT_HEAL_MAGNITUDE;
            Limb limb = profile.limb(limbHint);
            float before = limb.getLocalNumbing();
            limb.setLocalNumbing(Math.max(before, mag));
            if (limb.getLocalNumbing() == before) {
                return false;
            }
            profile.markDirty();
            data.bumpRevision();
            MedicalNetworking.sendDelta(player,
                    new TraumaDeltaPacket(TraumaDeltaPacket.Op.CHANGED, limbHint, "", 0.0F));
            return true;
        }

        Trauma target = pickTarget(profile, treatment, action, limbHint);
        if (target == null) {
            // A treatment may still restore blood as a secondary effect even with no matching wound.
            if (treatment.bloodRestoreMl() > 0.0D && profile.getBloodMl() < profile.getMaxBloodMl()) {
                double before = profile.getBloodMl();
                profile.setBloodMl(before + treatment.bloodRestoreMl());
                if (profile.getBloodMl() != before) {
                    data.bumpRevision();
                    return true;
                }
            }
            return false;
        }

        float magnitude = treatment.magnitude() > 0.0F ? treatment.magnitude() : DEFAULT_HEAL_MAGNITUDE;
        boolean removed = false;
        boolean changed = false;
        TraumaDeltaPacket.Op op = TraumaDeltaPacket.Op.TREATED;

        switch (action) {
            case REDUCE_BLEEDING -> {
                if (!target.isTreated()) {
                    target.setTreated(true);
                    changed = true;
                }
            }
            case SUTURE_WOUND -> {
                if (!target.isSutured()) {
                    target.setSutured(true);
                    target.setTreated(true);
                    changed = true;
                }
            }
            case STABILIZE_FRACTURE -> {
                if (!target.isStabilized()) {
                    target.setStabilized(true);
                    changed = true;
                }
            }
            // REDUCE_PAIN is handled above (profile-level pain suppression) and never reaches this switch.
            case HEAL_TRAUMA, TREAT_BURN, TREAT_RADIATION -> {
                target.setSeverity(target.getSeverity() - magnitude);
                target.setTreated(true);
                changed = true;
                if (treatment.removesTrauma() || target.getSeverity() <= 0.0F) {
                    removed = true;
                }
            }
            default -> {
                return false;
            }
        }

        if (treatment.removesTrauma() && !removed && changed) {
            removed = true;
        }

        Limb limb = profile.limb(target.getLimb());
        if (removed) {
            limb.removeTrauma(target);
            op = TraumaDeltaPacket.Op.REMOVED;
            changed = true;
        }

        // Optional secondary blood restore (e.g. a medkit that also gives blood).
        if (treatment.bloodRestoreMl() > 0.0D && profile.getBloodMl() < profile.getMaxBloodMl()) {
            profile.setBloodMl(profile.getBloodMl() + treatment.bloodRestoreMl());
            changed = true;
        }

        if (!changed) {
            return false;
        }

        limb.markDirty();
        profile.markDirty();
        data.bumpRevision();
        MedicalNetworking.sendDelta(player,
                new TraumaDeltaPacket(op, target.getLimb(), target.getType().getId(), target.getSeverity()));
        return true;
    }

    /**
     * Choose the best trauma for this action: category must be applicable and the trauma must respond to
     * the action; then score by action-specific priority plus severity. When {@code limbHint} is non-null,
     * trauma on that limb gets a large bonus so a UI-selected limb is strongly preferred (a matching wound
     * always outranks a non-matching one, ties within the limb still break on the usual priority/severity).
     */
    private static Trauma pickTarget(MedicalProfile profile, Treatment treatment, TreatmentAction action,
                                     LimbType limbHint) {
        Trauma best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (LimbType lt : LimbType.VALUES) {
            Limb limb = profile.limb(lt);
            java.util.List<Trauma> traumas = limb.getTraumas();
            for (int i = 0; i < traumas.size(); i++) {
                Trauma t = traumas.get(i);
                if (!treatment.appliesTo(t.getType().getCategory())) {
                    continue;
                }
                if (!t.getType().respondsTo(action)) {
                    continue;
                }
                float score = priority(action, t) + t.getSeverity();
                if (limbHint != null && t.getLimb() == limbHint) {
                    score += LIMB_HINT_BONUS;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = t;
                }
            }
        }
        return best;
    }

    /**
     * Action-specific preference bonus so the most relevant wound wins ties on severity.
     */
    private static float priority(TreatmentAction action, Trauma t) {
        return switch (action) {
            case REDUCE_BLEEDING, SUTURE_WOUND -> {
                float bonus = t.bleeding() > 0.0F ? 2.0F : 0.0F;
                if (t.isTreated() || t.isSutured()) {
                    bonus -= 1.0F; // prefer wounds that are not already handled
                }
                yield bonus;
            }
            case STABILIZE_FRACTURE -> {
                float bonus = t.isFracture() ? 2.0F : 0.0F;
                if (t.isStabilized()) {
                    bonus -= 1.0F;
                }
                yield bonus;
            }
            case HEAL_TRAUMA, TREAT_BURN, TREAT_RADIATION, REDUCE_PAIN -> t.isTreated() ? -0.5F : 0.5F;
            default -> 0.0F;
        };
    }
}
