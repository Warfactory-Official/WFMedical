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
public final class MedicalSyncPacket {

    /** Compact per-limb summary carried in the snapshot; enough for a HUD, not the raw trauma list. */
    public record LimbSummary(LimbType limb, float healthPercent, float bleeding, float pain, boolean fracture) {
    }

    private final DerivedStats stats;
    private final LimbSummary[] limbs;
    private final double bloodMl;
    private final double maxBloodMl;
    private final HealthState state;

    public MedicalSyncPacket(DerivedStats stats, LimbSummary[] limbs, double bloodMl,
                             double maxBloodMl, HealthState state) {
        this.stats = stats;
        this.limbs = limbs;
        this.bloodMl = bloodMl;
        this.maxBloodMl = maxBloodMl;
        this.state = state;
    }

    /** Build a snapshot from the authoritative server-side profile (reads cached limb aggregates). */
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
                profile.getState());
    }

    public DerivedStats stats() {
        return stats;
    }

    public LimbSummary[] limbs() {
        return limbs;
    }

    public double bloodMl() {
        return bloodMl;
    }

    public double maxBloodMl() {
        return maxBloodMl;
    }

    public HealthState state() {
        return state;
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
        return new MedicalSyncPacket(stats, limbs, bloodMl, maxBloodMl, state);
    }

    /** Client-thread handler: overwrite the local cache with this authoritative snapshot. */
    public void handleClient() {
        ClientMedicalCache.set(this);
    }
}
