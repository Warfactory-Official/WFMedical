package com.warfactory.medical.substance;

import com.warfactory.medical.body.attachment.IMedicalData;
import com.warfactory.medical.treatment.MedicalItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public record Substance(String id, String itemId, float painSuppression, float doseLoad, float overdoseThreshold,
                        int unconsciousTicks, float lethalThreshold, boolean antidote, float reversalAmount,
                        int useDurationTicks, double bloodRestoreMl,
                        float clottingBoost, float stimulantStrength, int effectTicks) implements MedicalItem.Dose {

    public Substance(String id,
                     String itemId,
                     float painSuppression,
                     float doseLoad,
                     float overdoseThreshold,
                     int unconsciousTicks,
                     float lethalThreshold,
                     boolean antidote,
                     float reversalAmount,
                     int useDurationTicks,
                     double bloodRestoreMl,
                     float clottingBoost,
                     float stimulantStrength,
                     int effectTicks) {
        this.id = id;
        this.itemId = itemId;
        this.painSuppression = clamp01(painSuppression);
        this.doseLoad = Math.max(0.0F, doseLoad);
        this.overdoseThreshold = overdoseThreshold;
        this.unconsciousTicks = Math.max(0, unconsciousTicks);
        this.lethalThreshold = lethalThreshold;
        this.antidote = antidote;
        this.reversalAmount = Math.max(0.0F, reversalAmount);
        this.useDurationTicks = Math.max(1, useDurationTicks);
        this.bloodRestoreMl = bloodRestoreMl;
        this.clottingBoost = clamp01(clottingBoost);
        this.stimulantStrength = clamp01(stimulantStrength);
        this.effectTicks = Math.max(0, effectTicks);
    }

    @Override
    public boolean apply(ServerPlayer actor, LivingEntity target, IMedicalData targetData) {
        return SubstanceService.inject(actor, target, targetData, this);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }
}
