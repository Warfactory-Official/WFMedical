package com.warfactory.medical.damage;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.limb.LimbType;
import com.wf.wflib.armor.api.ArmorResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** Per-limb armour through wflib; this mod answers for players, so wflib skips them. */
public final class ArmorResolution {

    private ArmorResolution() {
    }

    public static void claimPlayers() {
        com.wf.wflib.armor.api.ArmorResolution.PLAYER_RESOLUTION.set(WFMedical.MOD_ID, true);
    }

    /**
     * @param applyWear false for limbs a penetrating round carries on into: one impact => one wear charge per piece
     */
    public static ArmorResult resolve(LivingEntity victim, LimbType limb, DamageSource source, float amount,
                                      boolean applyWear) {
        return com.wf.wflib.armor.api.ArmorResolution.resolveSlot(victim, slotFor(limb), source, amount, applyWear);
    }

    /** Arms sit behind the chestplate (vanilla slot layout). */
    private static EquipmentSlot slotFor(LimbType limb) {
        return switch (limb) {
            case HEAD -> EquipmentSlot.HEAD;
            case LEFT_LEG, RIGHT_LEG -> EquipmentSlot.LEGS;
            case TORSO, LEFT_ARM, RIGHT_ARM -> EquipmentSlot.CHEST;
        };
    }
}
