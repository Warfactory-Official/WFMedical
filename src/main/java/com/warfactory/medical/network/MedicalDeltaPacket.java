package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import net.minecraft.network.FriendlyByteBuf;

public record MedicalDeltaPacket(int mask, DerivedStats stats, LimbSummary[] limbs, double bloodMl,
                                 double maxBloodMl, float painSuppression, float drugLoad, HealthState state,
                                 float deathProgress) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MedicalDeltaPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "medical_delta"));

    public static final StreamCodec<FriendlyByteBuf, MedicalDeltaPacket> STREAM_CODEC =
            CustomPacketPayload.codec(MedicalDeltaPacket::encode, MedicalDeltaPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    private static final int STATS = 1;
    private static final int SCALARS = 1 << 1;
    private static final int LIMB_BASE = 1 << 2;

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

    public boolean isEmpty() {
        return mask == 0;
    }

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

    public void handleClient() {
        ClientMedicalCache.applyDelta(this);
    }
}
