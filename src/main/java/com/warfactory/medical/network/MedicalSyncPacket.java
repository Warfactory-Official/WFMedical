package com.warfactory.medical.network;

import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * FULL medical snapshot, server -> client. Carries the player's {@link DerivedStats}, the blood pool,
 * the current {@link HealthState} and a compact per-limb summary (health%, bleeding, pain, fracture).
 *
 * <p>Sent as a fresh baseline on login / respawn (and whenever the client has no prior snapshot); once a
 * baseline exists the engine sends the smaller {@link MedicalDeltaPacket} carrying only the changed
 * components. The per-component (de)serialization is SHARED with that delta via the
 * {@link #writeStats}/{@link #readStats}/{@link #writeLimb}/{@link #readLimb} helpers – keep write and read
 * in lockstep, as the field order is the wire contract.</p>
 */
public record MedicalSyncPacket(DerivedStats stats, LimbSummary[] limbs, double bloodMl, double maxBloodMl,
                                float painSuppression, float drugLoad, HealthState state, float deathProgress) {

    /**
     * Build a snapshot from the authoritative server-side profile (reads cached limb aggregates).
     */
    public static MedicalSyncPacket fromProfile(MedicalProfile profile) {
        LimbType[] all = LimbType.VALUES;
        LimbSummary[] summaries = new LimbSummary[all.length];
        for (int i = 0; i < all.length; i++) {
            Limb limb = profile.limb(all[i]);
            float max = limb.getMaxHealth();
            float remaining = max - limb.getCachedHealthReduction() - limb.getMinorDamage();
            float pct = max <= 0.0F ? 0.0F : remaining / max;
            if (pct < 0.0F) {
                pct = 0.0F;
            } else if (pct > 1.0F) {
                pct = 1.0F;
            }
            summaries[i] = new LimbSummary(
                    all[i],
                    pct,
                    (float) limb.getCachedBleeding(),
                    limb.getCachedPain(),
                    limb.hasCachedFracture());
        }
        return new MedicalSyncPacket(
                profile.cached(),
                summaries,
                profile.getBloodMl(),
                profile.getMaxBloodMl(),
                profile.getPainSuppression(),
                profile.getDrugLoad(),
                profile.getState(),
                profile.getDeathProgress());
    }

    public static MedicalSyncPacket decode(FriendlyByteBuf buf) {
        DerivedStats stats = readStats(buf);
        double bloodMl = buf.readDouble();
        double maxBloodMl = buf.readDouble();
        float painSuppression = buf.readFloat();
        float drugLoad = buf.readFloat();
        HealthState state = buf.readEnum(HealthState.class);
        int count = buf.readVarInt();
        LimbSummary[] limbs = new LimbSummary[count];
        for (int i = 0; i < count; i++) {
            limbs[i] = readLimb(buf);
        }
        float deathProgress = buf.readFloat();
        return new MedicalSyncPacket(stats, limbs, bloodMl, maxBloodMl, painSuppression, drugLoad, state,
                deathProgress);
    }

    static void writeStats(FriendlyByteBuf buf, DerivedStats s) {
        buf.writeFloat(s.effectiveMaxHealth());
        buf.writeFloat(s.healthModifier());
        buf.writeFloat(s.effectiveCurrentHealth());
        buf.writeDouble(s.totalBleeding());
        buf.writeFloat(s.totalPain());
        buf.writeFloat(s.systemicPain());
        buf.writeFloat(s.movementMultiplier());
        buf.writeBoolean(s.sprintBlocked());
        buf.writeFloat(s.jumpMultiplier());
        buf.writeEnum(s.state());
        buf.writeBoolean(s.anyLegFracture());
        buf.writeBoolean(s.anyArmFracture());
        buf.writeBoolean(s.asphyxiating());
        buf.writeBoolean(s.painKoPending());
        buf.writeBoolean(s.bothArmsDisabled());
        buf.writeBoolean(s.bothLegsDisabled());
        buf.writeBoolean(s.anyArmTourniquet());
    }

    static DerivedStats readStats(FriendlyByteBuf buf) {
        return new DerivedStats(
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readFloat(),
                buf.readEnum(HealthState.class),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    static void writeLimb(FriendlyByteBuf buf, LimbSummary s) {
        buf.writeEnum(s.limb());
        buf.writeFloat(s.healthPercent());
        buf.writeFloat(s.bleeding());
        buf.writeFloat(s.pain());
        buf.writeBoolean(s.fracture());
    }

    // ---- Reusable component (de)serialization, SHARED with MedicalDeltaPacket. The field order below is the
    // wire contract; keep each write/read pair in lockstep or the stream silently corrupts. ----

    static LimbSummary readLimb(FriendlyByteBuf buf) {
        return new LimbSummary(buf.readEnum(LimbType.class), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readBoolean());
    }

    /**
     * Perceived-pain suppression fraction (0..1) from painkillers at snapshot time.
     */
    @Override
    public float painSuppression() {
        return painSuppression;
    }

    /**
     * Accumulating injectable-drug load at snapshot time (0..); drives the overdose UI.
     */
    @Override
    public float drugLoad() {
        return drugLoad;
    }

    public void encode(FriendlyByteBuf buf) {
        writeStats(buf, stats);
        buf.writeDouble(bloodMl);
        buf.writeDouble(maxBloodMl);
        buf.writeFloat(painSuppression);
        buf.writeFloat(drugLoad);
        buf.writeEnum(state);
        buf.writeVarInt(limbs.length);
        for (LimbSummary s : limbs) {
            writeLimb(buf, s);
        }
        buf.writeFloat(deathProgress);
    }

    /**
     * Client-thread handler: overwrite the local cache with this authoritative snapshot.
     */
    public void handleClient() {
        ClientMedicalCache.set(this);
    }

    /**
     * Compact per-limb summary carried in the snapshot; enough for a HUD, not the raw trauma list.
     */
    public record LimbSummary(LimbType limb, float healthPercent, float bleeding, float pain, boolean fracture) {
    }
}
