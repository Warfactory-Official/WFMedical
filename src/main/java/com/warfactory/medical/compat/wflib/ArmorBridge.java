package com.warfactory.medical.compat.wflib;

import com.wf.wflib.armor.ArmorOutcome;
import com.wf.wflib.armor.ArmorResult;
import com.wf.wflib.armor.ArmorSystem;
import com.warfactory.medical.core.damage.ArmorEvaluation;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The half that names WFLib types. Never loaded unless the mod is there; see
 * {@link WfLibArmorCompat}.
 */
final class ArmorBridge {

    private ArmorBridge() {
    }

    static void claim() {
        ArmorSystem.claimPlayerResolution();
        // Artefact healing too: a vanilla heal is clamped to the medical health value and would do nothing.
        com.wf.wflib.artifact.Artifacts.healer = com.warfactory.medical.api.MedicalHealing::heal;
        // And their wound closing: GAMMA's strongest (Mica, 0.054 a real second) near a full clotting boost; negative
        // thins the blood on the same scale.
        com.wf.wflib.artifact.Artifacts.clotter = (player, rate) -> {
            if (rate > 0.0F) {
                com.warfactory.medical.api.MedicalHealing.clot(player, Math.min(1.0F, rate / 0.06F), 40);
            } else {
                com.warfactory.medical.api.MedicalHealing.thin(player, Math.min(1.0F, -rate / 0.06F), 40);
            }
        };
    }

    static boolean claimed() {
        return ArmorSystem.playerResolutionClaimed();
    }

    static WfLibArmorCompat.Resolved resolve(LivingEntity victim, LimbType limb,
                                                    DamageSource source, float amount, boolean applyWear) {
        EquipmentSlot slot = ArmorEvaluation.slotFor(limb);
        ArmorResult result = ArmorSystem.resolveSlot(victim, slot, source, amount, applyWear);
        return new WfLibArmorCompat.Resolved(outcome(result.outcome()), (float) result.through());
    }

    static void resolveWhole(LivingIncomingDamageEvent event) {
        // applyToEvent rather than onIncomingDamage: the latter honours the claim made above and would
        // skip the player, which is exactly the hit this path exists to cover.
        ArmorSystem.applyToEvent(event);
    }

    /**
     * The three values survive unchanged, so trauma generation does not move; what changes is that they
     * are now read off the arithmetic instead of rolled for.
     */
    private static ArmorEvaluation.Outcome outcome(ArmorOutcome outcome) {
        return switch (outcome) {
            case STOPPED -> ArmorEvaluation.Outcome.BLOCKED;
            case REDUCED -> ArmorEvaluation.Outcome.PARTIAL;
            case THROUGH -> ArmorEvaluation.Outcome.FULL;
        };
    }
}
