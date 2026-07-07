package com.warfactory.medical.network;

import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import net.minecraft.network.FriendlyByteBuf;

/**
 * INCREMENTAL medical update, server -> client. Carries only the components of a {@link MedicalSyncPacket}
 * that CHANGED since the last snapshot sent to that client, selected by a bitmask, and is applied on top of
 * the client's cached baseline ({@link #applyTo}). The baseline itself is a full {@link MedicalSyncPacket}
 * (sent on login / respawn), so a delta always has something to patch.
 *
 * <p>Because {@code DerivedStats} is a global aggregate it usually changes on any update, but the six per-limb
 * summaries (the bulk of the payload) rarely all change at once, so a delta typically ships one limb instead
 * of six. The reliable ordered channel (SimpleChannel over the connection) guarantees deltas apply in order to
 * a matching baseline, so no acknowledgement is needed.</p>
 *
 * <p>{@code limbs} is a sparse array the length of the full limb set: an entry is non-null exactly when its
 * bit is set in {@code mask}. The (de)serialization reuses {@link MedicalSyncPacket}'s shared component
 * helpers so the wire format stays identical to the full packet's.</p>
 */
public record MedicalDeltaPacket(int mask, DerivedStats stats, LimbSummary[] limbs, double bloodMl,
                                 double maxBloodMl, float painSuppression, float drugLoad, HealthState state,
                                 float deathProgress) {

    /**
     * {@code DerivedStats} changed.
     */
    private static final int STATS = 1;
    /**
     * Any of the top-level scalars (blood, painSuppression, drugLoad, state, deathProgress) changed.
     */
    private static final int SCALARS = 1 << 1;
    /**
     * Base bit for limb {@code i}: {@code LIMB_BASE << i}.
     */
    private static final int LIMB_BASE = 1 << 2;

    /**
     * Build the delta from {@code prev} (what the client already has) to {@code cur} (the new snapshot). An
     * empty result ({@link #isEmpty()}) means nothing observable changed.
     */
    public static MedicalDeltaPacket diff(MedicalSyncPacket prev, MedicalSyncPacket cur) {
        int mask = 0;
        DerivedStats stats = null;
        if (!cur.stats().equals(prev.stats())) {
            mask |= STATS;
            stats = cur.stats();
        }
        if (cur.bloodMl() != prev.bloodMl() || cur.maxBloodMl() != prev.maxBloodMl()
                || cur.painSuppression() != prev.painSuppression() || cur.drugLoad() != prev.drugLoad()
                || cur.state() != prev.state() || cur.deathProgress() != prev.deathProgress()) {
            mask |= SCALARS;
        }
        LimbSummary[] curLimbs = cur.limbs();
        LimbSummary[] prevLimbs = prev.limbs();
        LimbSummary[] limbs = new LimbSummary[curLimbs.length];
        for (int i = 0; i < curLimbs.length; i++) {
            if (i >= prevLimbs.length || !curLimbs[i].equals(prevLimbs[i])) {
                mask |= (LIMB_BASE << i);
                limbs[i] = curLimbs[i];
            }
        }
        return new MedicalDeltaPacket(mask, stats, limbs, cur.bloodMl(), cur.maxBloodMl(),
                cur.painSuppression(), cur.drugLoad(), cur.state(), cur.deathProgress());
    }

    public static MedicalDeltaPacket decode(FriendlyByteBuf buf) {
        int mask = buf.readVarInt();
        DerivedStats stats = (mask & STATS) != 0 ? MedicalSyncPacket.readStats(buf) : null;
        double bloodMl = 0.0D;
        double maxBloodMl = 0.0D;
        float painSuppression = 0.0F;
        float drugLoad = 0.0F;
        HealthState state = HealthState.HEALTHY;
        float deathProgress = 0.0F;
        if ((mask & SCALARS) != 0) {
            bloodMl = buf.readDouble();
            maxBloodMl = buf.readDouble();
            painSuppression = buf.readFloat();
            drugLoad = buf.readFloat();
            state = buf.readEnum(HealthState.class);
            deathProgress = buf.readFloat();
        }
        int n = LimbType.VALUES.length;
        LimbSummary[] limbs = new LimbSummary[n];
        for (int i = 0; i < n; i++) {
            if ((mask & (LIMB_BASE << i)) != 0) {
                limbs[i] = MedicalSyncPacket.readLimb(buf);
            }
        }
        return new MedicalDeltaPacket(mask, stats, limbs, bloodMl, maxBloodMl, painSuppression, drugLoad,
                state, deathProgress);
    }

    /**
     * Whether nothing changed (no component is present); such a delta is not worth sending.
     */
    public boolean isEmpty() {
        return mask == 0;
    }

    /**
     * Produce the new full snapshot by overlaying this delta's present components onto {@code base}.
     */
    public MedicalSyncPacket applyTo(MedicalSyncPacket base) {
        DerivedStats newStats = (mask & STATS) != 0 ? stats : base.stats();
        double newBlood = base.bloodMl();
        double newMaxBlood = base.maxBloodMl();
        float newPainSuppression = base.painSuppression();
        float newDrugLoad = base.drugLoad();
        HealthState newState = base.state();
        float newDeathProgress = base.deathProgress();
        if ((mask & SCALARS) != 0) {
            newBlood = bloodMl;
            newMaxBlood = maxBloodMl;
            newPainSuppression = painSuppression;
            newDrugLoad = drugLoad;
            newState = state;
            newDeathProgress = deathProgress;
        }
        LimbSummary[] baseLimbs = base.limbs();
        LimbSummary[] newLimbs = new LimbSummary[baseLimbs.length];
        for (int i = 0; i < baseLimbs.length; i++) {
            newLimbs[i] = (i < limbs.length && limbs[i] != null) ? limbs[i] : baseLimbs[i];
        }
        return new MedicalSyncPacket(newStats, newLimbs, newBlood, newMaxBlood, newPainSuppression,
                newDrugLoad, newState, newDeathProgress);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mask);
        if ((mask & STATS) != 0) {
            MedicalSyncPacket.writeStats(buf, stats);
        }
        if ((mask & SCALARS) != 0) {
            buf.writeDouble(bloodMl);
            buf.writeDouble(maxBloodMl);
            buf.writeFloat(painSuppression);
            buf.writeFloat(drugLoad);
            buf.writeEnum(state);
            buf.writeFloat(deathProgress);
        }
        for (int i = 0; i < limbs.length; i++) {
            if ((mask & (LIMB_BASE << i)) != 0) {
                MedicalSyncPacket.writeLimb(buf, limbs[i]);
            }
        }
    }

    /**
     * Client-thread handler: patch the cached baseline with this delta.
     */
    public void handleClient() {
        ClientMedicalCache.applyDelta(this);
    }
}
