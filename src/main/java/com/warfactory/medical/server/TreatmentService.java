package com.warfactory.medical.server;

import com.warfactory.medical.attachment.IMedicalData;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaResponse;
import com.warfactory.medical.core.treatment.Treatment;
import com.warfactory.medical.core.treatment.TreatmentAction;
import net.minecraft.server.level.ServerPlayer;

public final class TreatmentService {

    private static final float DEFAULT_HEAL_MAGNITUDE = 0.25F;

    private TreatmentService() {
    }

    public static boolean apply(ServerPlayer player, Treatment treatment) {
        return applyTargeted(player, treatment, null);
    }

    public static boolean applyTargeted(ServerPlayer player, Treatment treatment, LimbType limbHint) {
        if (player == null) {
            return false;
        }
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return false;
        }
        return applyTargeted(data, player.level().getGameTime(), treatment, limbHint);
    }

    public static boolean applyTargeted(IMedicalData data, long gameTime, Treatment treatment, LimbType limbHint) {
        if (data == null || treatment == null) {
            return false;
        }
        MedicalProfile profile = data.getProfile();
        TreatmentAction action = treatment.action();

        if (action == TreatmentAction.RESTORE_BLOOD) {
            double before = profile.getBloodMl();
            if (treatment.bloodRestoreMl() <= 0.0D || before >= profile.getMaxBloodMl()) {
                return false;
            }
            profile.setBloodMl(before + treatment.bloodRestoreMl());
            boolean changed = profile.getBloodMl() != before;
            if (changed) {
                data.bumpRevision();
            }
            return changed;
        }

        if (action == TreatmentAction.REDUCE_PAIN) {
            float mag = treatment.magnitude() > 0.0F ? treatment.magnitude() : DEFAULT_HEAL_MAGNITUDE;
            float before = profile.getPainSuppression();
            profile.setPainSuppression(Math.max(before, mag));
            boolean changed = profile.getPainSuppression() != before;
            if (changed) {
                data.bumpRevision();
            }
            return changed;
        }

        if (action == TreatmentAction.NUMB_LIMB) {
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
            return true;
        }

        // BOOST_CLOTTING is a global timed buff, but it ALSO applies a targeted wound response (this is how a
        // hemostatic can slow internal bleeding). Apply the global buff here, then fall through so a wound that
        // declares a BOOST_CLOTTING response gets treated too.
        boolean clottingChanged = false;
        if (action == TreatmentAction.BOOST_CLOTTING) {
            float mag = treatment.magnitude() > 0.0F ? treatment.magnitude() : DEFAULT_HEAL_MAGNITUDE;
            long end = gameTime + MedicalConfig.clottingAgentDurationTicks();
            float beforeBoost = profile.getClottingBoost();
            long beforeEnd = profile.getClottingBoostEndTick();
            float newBoost = Math.max(beforeBoost, mag);
            long newEnd = Math.max(beforeEnd, end);
            if (newBoost != beforeBoost || newEnd != beforeEnd) {
                profile.setClottingBoost(newBoost);
                profile.setClottingBoostEndTick(newEnd);
                profile.markDirty();
                clottingChanged = true;
            }
        }

        Trauma target = pickTarget(profile, treatment, action, limbHint);
        if (target == null) {
            boolean bloodChanged = false;
            if (treatment.bloodRestoreMl() > 0.0D && profile.getBloodMl() < profile.getMaxBloodMl()) {
                double before = profile.getBloodMl();
                profile.setBloodMl(before + treatment.bloodRestoreMl());
                bloodChanged = profile.getBloodMl() != before;
            }
            if (clottingChanged || bloodChanged) {
                data.bumpRevision();
                return true;
            }
            return false;
        }

        float magnitude = treatment.magnitude() > 0.0F ? treatment.magnitude() : DEFAULT_HEAL_MAGNITUDE;
        boolean removed = false;
        boolean changed = false;

        // Apply the trauma's data-driven response for this action (fully modular per trauma type).
        TraumaResponse resp = target.getType().response(action);
        if (resp == null) {
            return false;
        }
        switch (resp.effect()) {
            case STOP_BLEED -> {
                if (target.getBleedFactor() > 0.0F) {
                    target.setBleedFactor(0.0F);
                    changed = true;
                }
            }
            case REDUCE_BLEED -> {
                float f = resp.factor();
                if (target.getBleedFactor() > f) {
                    target.setBleedFactor(f);
                    changed = true;
                }
            }
            case SUTURE -> {
                if (target.getBleedFactor() > 0.0F) {
                    target.setBleedFactor(0.0F);
                    changed = true;
                }
                if (!target.isClosed()) {
                    target.setClosed(true);
                    changed = true;
                }
            }
            case STABILIZE -> {
                if (!target.isStabilized()) {
                    target.setStabilized(true);
                    changed = true;
                }
            }
            case HEAL -> {
                target.setSeverity(target.getSeverity() - magnitude);
                changed = true;
                if (treatment.removesTrauma() || target.getSeverity() <= 0.0F) {
                    removed = true;
                }
            }
        }
        // Effects that "care for" the wound (suture / heal) also mark it treated so it mends on its own.
        if (resp.heals() && !target.isTreated()) {
            target.setTreated(true);
            changed = true;
        }

        if (treatment.removesTrauma() && !removed && changed) {
            removed = true;
        }

        Limb limb = profile.limb(target.getLimb());
        if (removed) {
            limb.removeTrauma(target);
            changed = true;
        }

        if (treatment.bloodRestoreMl() > 0.0D && profile.getBloodMl() < profile.getMaxBloodMl()) {
            profile.setBloodMl(profile.getBloodMl() + treatment.bloodRestoreMl());
            changed = true;
        }

        if (!changed) {
            if (clottingChanged) {
                data.bumpRevision();
                return true;
            }
            return false;
        }

        limb.markDirty();
        profile.markDirty();
        data.bumpRevision();
        return true;
    }

    private static Trauma pickTarget(MedicalProfile profile, Treatment treatment, TreatmentAction action,
                                     LimbType limbHint) {
        Trauma best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (LimbType lt : LimbType.VALUES) {
            if (limbHint != null && lt != limbHint) {
                continue;
            }
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
                if (score > bestScore) {
                    bestScore = score;
                    best = t;
                }
            }
        }
        return best;
    }

    public static boolean canTreatLimb(MedicalProfile profile, Treatment treatment, LimbType limbType) {
        if (profile == null || treatment == null || limbType == null) {
            return false;
        }
        TreatmentAction action = treatment.action();
        if (action.isGlobal()) {
            return true;
        }
        Limb limb = profile.limb(limbType);
        if (action == TreatmentAction.NUMB_LIMB) {
            return limb.getCachedPain() > 0.0F;
        }
        if (action == TreatmentAction.APPLY_TOURNIQUET) {
            return (limbType.isArm() || limbType.isLeg()) && !limb.hasTourniquet();
        }
        for (Trauma t : limb.getTraumas()) {
            if (treatment.appliesTo(t.getType().getCategory()) && t.getType().respondsTo(action)) {
                // A dressing is only useful while the wound is actually bleeding.
                if (action == TreatmentAction.REDUCE_BLEEDING && t.bleeding() <= 0.0F) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    public static int treatableMask(MedicalProfile profile, Treatment treatment) {
        int mask = 0;
        for (LimbType lt : LimbType.VALUES) {
            if (canTreatLimb(profile, treatment, lt)) {
                mask |= (1 << lt.ordinal());
            }
        }
        return mask;
    }

    private static float priority(TreatmentAction action, Trauma t) {
        return switch (action) {
            case REDUCE_BLEEDING, SUTURE_WOUND, BOOST_CLOTTING -> {
                float bonus = t.bleeding() > 0.0F ? 2.0F : 0.0F;
                if (t.isTreated() || t.getBleedFactor() < 1.0F) {
                    bonus -= 1.0F;
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
