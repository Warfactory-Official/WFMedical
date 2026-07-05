package com.warfactory.medical.core.substance;

import com.warfactory.medical.core.treatment.Treatment;

/**
 * Immutable description of an injectable substance (opioid analgesic or antidote). Data-driven, mirroring
 * {@link Treatment}: the substance / injection modules read these
 * fields to decide how to mutate the player's {@code drugLoad}, pain suppression and unconsciousness state.
 *
 * <p>An opioid ({@link #antidote ()} == false) masks perceived pain by raising the profile's
 * {@code painSuppression} and adds {@link #doseLoad ()} to an accumulating {@code drugLoad}; re-dosing to
 * stay pain-free stacks {@code drugLoad} toward {@link #overdoseThreshold ()} (unconsciousness) and, past
 * {@link #lethalThreshold ()}, a fatal respiratory-depression drain. An antidote
 * ({@link #antidote ()} == true) instead removes {@link #reversalAmount ()} from {@code drugLoad}, ends
 * the unconsciousness and drops pain suppression back to zero.</p>
 */
public record Substance(String id, String itemId, float painSuppression, float doseLoad, float overdoseThreshold,
                        int unconsciousTicks, float lethalThreshold, boolean antidote, float reversalAmount,
                        int useDurationTicks, double bloodRestoreMl) {

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
                     double bloodRestoreMl) {
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
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    /**
     * Unique substance id (e.g. {@code "morphine"}).
     */
    @Override
    public String id() {
        return id;
    }

    /**
     * Registry-name string of the item that injects this substance (e.g. {@code "wfmedical:morphine_syringe"}).
     */
    @Override
    public String itemId() {
        return itemId;
    }

    /**
     * Perceived-pain mask (0..1) applied on injection; ignored for antidotes.
     */
    @Override
    public float painSuppression() {
        return painSuppression;
    }

    /**
     * Amount added to the player's {@code drugLoad} per injection (opioid only).
     */
    @Override
    public float doseLoad() {
        return doseLoad;
    }

    /**
     * {@code drugLoad} at/above which an overdose unconsciousness triggers.
     */
    @Override
    public float overdoseThreshold() {
        return overdoseThreshold;
    }

    /**
     * Unconscious duration in ticks.
     */
    @Override
    public int unconsciousTicks() {
        return unconsciousTicks;
    }

    /**
     * {@code drugLoad} at/above which an overdose additionally drains health ({@code <=0} disables).
     */
    @Override
    public float lethalThreshold() {
        return lethalThreshold;
    }

    /**
     * True when this substance reverses an overdose (antidote) instead of causing one (opioid).
     */
    @Override
    public boolean antidote() {
        return antidote;
    }

    /**
     * {@code drugLoad} removed by a single antidote injection.
     */
    @Override
    public float reversalAmount() {
        return reversalAmount;
    }

    /**
     * Injection channel time in ticks.
     */
    @Override
    public int useDurationTicks() {
        return useDurationTicks;
    }

    /**
     * Optional secondary blood restore in ml (usually 0).
     */
    @Override
    public double bloodRestoreMl() {
        return bloodRestoreMl;
    }
}
