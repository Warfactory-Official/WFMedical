package com.warfactory.medical.core.substance;

/**
 * Immutable description of an injectable substance (opioid analgesic or antidote). Data-driven, mirroring
 * {@link com.warfactory.medical.core.treatment.Treatment}: the substance / injection modules read these
 * fields to decide how to mutate the player's {@code drugLoad}, pain suppression and blackout state.
 *
 * <p>An opioid ({@link #isAntidote()} == false) masks perceived pain by raising the profile's
 * {@code painSuppression} and adds {@link #getDoseLoad()} to an accumulating {@code drugLoad}; re-dosing to
 * stay pain-free stacks {@code drugLoad} toward {@link #getOverdoseThreshold()} (blackout) and, past
 * {@link #getLethalThreshold()}, a fatal respiratory-depression drain. An antidote
 * ({@link #isAntidote()} == true) instead removes {@link #getReversalAmount()} from {@code drugLoad}, ends
 * the blackout and drops pain suppression back to zero.</p>
 */
public final class Substance {

    private final String id;
    private final String itemId;
    private final float painSuppression;
    private final float doseLoad;
    private final float overdoseThreshold;
    private final int blackoutTicks;
    private final float lethalThreshold;
    private final boolean antidote;
    private final float reversalAmount;
    private final int useDurationTicks;
    private final double bloodRestoreMl;

    /**
     * @param id                unique substance id
     * @param itemId            registry-name string of the item that injects this substance
     * @param painSuppression   perceived-pain mask (0..1) applied on injection (opioid only)
     * @param doseLoad          amount added to the player's {@code drugLoad} per injection (opioid only)
     * @param overdoseThreshold {@code drugLoad} at/above which an overdose blackout triggers
     * @param blackoutTicks     blackout (unconscious) duration in ticks
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
                     int blackoutTicks,
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
        this.blackoutTicks = Math.max(0, blackoutTicks);
        this.lethalThreshold = lethalThreshold;
        this.antidote = antidote;
        this.reversalAmount = Math.max(0.0F, reversalAmount);
        this.useDurationTicks = Math.max(1, useDurationTicks);
        this.bloodRestoreMl = bloodRestoreMl;
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    /** Unique substance id (e.g. {@code "morphine"}). */
    public String getId() {
        return id;
    }

    /** Registry-name string of the item that injects this substance (e.g. {@code "wfmedical:morphine_syringe"}). */
    public String getItemId() {
        return itemId;
    }

    /** Perceived-pain mask (0..1) applied on injection; ignored for antidotes. */
    public float getPainSuppression() {
        return painSuppression;
    }

    /** Amount added to the player's {@code drugLoad} per injection (opioid only). */
    public float getDoseLoad() {
        return doseLoad;
    }

    /** {@code drugLoad} at/above which an overdose blackout triggers. */
    public float getOverdoseThreshold() {
        return overdoseThreshold;
    }

    /** Blackout (unconscious) duration in ticks. */
    public int getBlackoutTicks() {
        return blackoutTicks;
    }

    /** {@code drugLoad} at/above which an overdose additionally drains health ({@code <=0} disables). */
    public float getLethalThreshold() {
        return lethalThreshold;
    }

    /** True when this substance reverses an overdose (antidote) instead of causing one (opioid). */
    public boolean isAntidote() {
        return antidote;
    }

    /** {@code drugLoad} removed by a single antidote injection. */
    public float getReversalAmount() {
        return reversalAmount;
    }

    /** Injection channel time in ticks. */
    public int getUseDurationTicks() {
        return useDurationTicks;
    }

    /** Optional secondary blood restore in ml (usually 0). */
    public double getBloodRestoreMl() {
        return bloodRestoreMl;
    }
}
