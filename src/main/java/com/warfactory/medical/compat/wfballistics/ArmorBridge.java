package com.warfactory.medical.compat.wfballistics;

import com.wf.wfballistics.armor.ArmorOutcome;
import com.wf.wfballistics.armor.ArmorResult;
import com.wf.wfballistics.armor.ArmorSystem;
import com.warfactory.medical.core.damage.ArmorEvaluation;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The half that names WF-Ballistics types. Never loaded unless the mod is there; see
 * {@link WfBallisticsArmorCompat}.
 */
final class ArmorBridge {

    private ArmorBridge() {
    }

    static void claim() {
        ArmorSystem.claimPlayerResolution();
    }

    static boolean claimed() {
        return ArmorSystem.playerResolutionClaimed();
    }

    static WfBallisticsArmorCompat.Resolved resolve(LivingEntity victim, LimbType limb,
                                                    DamageSource source, float amount, boolean applyWear) {
        EquipmentSlot slot = ArmorEvaluation.slotFor(limb);
        ArmorResult result = ArmorSystem.resolveSlot(victim, slot, source, amount, applyWear);
        return new WfBallisticsArmorCompat.Resolved(outcome(result.outcome()), (float) result.through());
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
