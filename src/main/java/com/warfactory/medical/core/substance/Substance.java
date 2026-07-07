package com.warfactory.medical.core.substance;

/**
 * Immutable description of an injectable substance (opioid analgesic or antidote). Opioids mask pain and
 * accumulate {@code drugLoad} toward overdose/lethal thresholds; antidotes reverse this by draining
 * {@code drugLoad} and ending the unconsciousness.
 */
public record Substance(String id, String itemId, float painSuppression, float doseLoad, float overdoseThreshold,
                        int unconsciousTicks, float lethalThreshold, boolean antidote, float reversalAmount,
                        int useDurationTicks, double bloodRestoreMl,
                        float clottingBoost, float stimulantStrength, int effectTicks) {

    /**
     * @param id                unique substance id
     * @param itemId            registry-name string of the item that injects this substance
     * @param painSuppression   perceived-pain mask (0..1) applied on injection (opioid only)
     * @param doseLoad          amount added to the player's {@code drugLoad} per injection (opioid only)
     * @param overdoseThreshold {@code drugLoad} at/above which an overdose unconsciousness triggers
     * @param unconsciousTicks  unconscious duration in ticks
     * @param lethalThreshold   {@code drugLoad} at/above which the overdose also drains health ({@code <=0} disables)
     * @param antidote          true when this substance reverses an overdose instead of causing one
     * @param reversalAmount    {@code drugLoad} removed by a single antidote injection
     * @param useDurationTicks  injection channel time in ticks
     * @param bloodRestoreMl    optional secondary blood restore in ml (usually 0)
     * @param clottingBoost     timed clotting-boost strength (0..1) granted on injection ({@code 0} = none)
     * @param stimulantStrength timed stimulant strength (0..1): anesthesia + speed boost + jump clear ({@code 0} = none)
     * @param effectTicks       how long the clotting / stimulant effects last, in ticks ({@code 0} = none)
     */
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

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }
}
