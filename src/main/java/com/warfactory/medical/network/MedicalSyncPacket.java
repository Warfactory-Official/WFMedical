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
 * <p>Sent on login / respawn / dimension change and whenever a delta would be insufficient. Cheaper
 * incremental updates go through {@link TraumaDeltaPacket}.</p>
 */
public record MedicalSyncPacket(DerivedStats stats, LimbSummary[] limbs, double bloodMl, double maxBloodMl,
                                float painSuppression, float drugLoad, HealthState state) {

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
                profile.getState());
    }

    public static MedicalSyncPacket decode(FriendlyByteBuf buf) {
        DerivedStats stats = new DerivedStats(
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readEnum(HealthState.class),
                buf.readBoolean(),
                buf.readBoolean());
        double bloodMl = buf.readDouble();
        double maxBloodMl = buf.readDouble();
        float painSuppression = buf.readFloat();
        float drugLoad = buf.readFloat();
        HealthState state = buf.readEnum(HealthState.class);
        int count = buf.readVarInt();
        LimbSummary[] limbs = new LimbSummary[count];
        for (int i = 0; i < count; i++) {
            limbs[i] = new LimbSummary(
                    buf.readEnum(LimbType.class),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean());
        }
        return new MedicalSyncPacket(stats, limbs, bloodMl, maxBloodMl, painSuppression, drugLoad, state);
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
        // DerivedStats
        buf.writeFloat(stats.effectiveMaxHealth());
        buf.writeFloat(stats.healthModifier());
        buf.writeFloat(stats.effectiveCurrentHealth());
        buf.writeDouble(stats.totalBleeding());
        buf.writeFloat(stats.totalPain());
        buf.writeFloat(stats.movementMultiplier());
        buf.writeBoolean(stats.sprintBlocked());
        buf.writeFloat(stats.jumpMultiplier());
        buf.writeEnum(stats.state());
        buf.writeBoolean(stats.anyLegFracture());
        buf.writeBoolean(stats.anyArmFracture());
        // Blood + high-level state
        buf.writeDouble(bloodMl);
        buf.writeDouble(maxBloodMl);
        buf.writeFloat(painSuppression);
        buf.writeFloat(drugLoad);
        buf.writeEnum(state);
        // Per-limb summary
        buf.writeVarInt(limbs.length);
        for (LimbSummary s : limbs) {
            buf.writeEnum(s.limb());
            buf.writeFloat(s.healthPercent());
            buf.writeFloat(s.bleeding());
            buf.writeFloat(s.pain());
            buf.writeBoolean(s.fracture());
        }
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
