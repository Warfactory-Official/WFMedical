package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record MedicalSyncPacket(DerivedStats stats, LimbSummary[] limbs, double bloodMl, double maxBloodMl,
                                float painSuppression, float drugLoad, HealthState state, float deathProgress) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MedicalSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "medical_sync"));

    public static final StreamCodec<FriendlyByteBuf, MedicalSyncPacket> STREAM_CODEC =
            CustomPacketPayload.codec(MedicalSyncPacket::encode, MedicalSyncPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


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
                    limb.hasCachedFracture(),
                    woundsOf(limb));
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
        buf.writeDouble(s.cardiacOutput());
        buf.writeFloat(s.heartRate());
    }

    static DerivedStats readStats(FriendlyByteBuf buf) {
        return new DerivedStats(
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readFloat(),
                buf.readEnum(HealthState.class),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readDouble(), buf.readFloat());
    }

    private static List<WoundView> woundsOf(Limb limb) {
        List<Trauma> traumas = limb.getTraumas();
        if (traumas.isEmpty()) {
            return List.of();
        }
        List<WoundView> out = new ArrayList<>(traumas.size());
        for (Trauma t : traumas) {
            float sev = t.getSeverity();
            int sevPct = Math.round((sev < 0.0F ? 0.0F : Math.min(sev, 1.0F)) * 100.0F);
            int flags = 0;
            if (t.bleeding() > 0.0F) {
                flags |= WoundView.FLAG_BLEEDING;
            }
            if (t.getBleedFactor() < 1.0F) {
                flags |= WoundView.FLAG_CONTROLLED;
            }
            if (t.isTreated()) {
                flags |= WoundView.FLAG_TREATED;
            }
            if (t.isStabilized()) {
                flags |= WoundView.FLAG_STABILIZED;
            }
            if (t.isClosed()) {
                flags |= WoundView.FLAG_CLOSED;
            }
            if (t.getType().isMajor()) {
                flags |= WoundView.FLAG_MAJOR;
            }
            out.add(new WoundView(t.getType().getId(), sevPct, flags));
        }
        return out;
    }

    static void writeLimb(FriendlyByteBuf buf, LimbSummary s) {
        buf.writeEnum(s.limb());
        buf.writeFloat(s.healthPercent());
        buf.writeFloat(s.bleeding());
        buf.writeFloat(s.pain());
        buf.writeBoolean(s.fracture());
        List<WoundView> wounds = s.wounds();
        buf.writeVarInt(wounds.size());
        for (WoundView w : wounds) {
            buf.writeUtf(w.typeId());
            buf.writeByte(w.severity());
            buf.writeByte(w.flags());
        }
    }


    static LimbSummary readLimb(FriendlyByteBuf buf) {
        LimbType limb = buf.readEnum(LimbType.class);
        float healthPercent = buf.readFloat();
        float bleeding = buf.readFloat();
        float pain = buf.readFloat();
        boolean fracture = buf.readBoolean();
        int count = buf.readVarInt();
        List<WoundView> wounds = count == 0 ? List.of() : new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            wounds.add(new WoundView(buf.readUtf(), buf.readByte() & 0xFF, buf.readByte() & 0xFF));
        }
        return new LimbSummary(limb, healthPercent, bleeding, pain, fracture, wounds);
    }

    @Override
    public float painSuppression() {
        return painSuppression;
    }

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

    public void handleClient() {
        ClientMedicalCache.set(this);
    }

    public record LimbSummary(LimbType limb, float healthPercent, float bleeding, float pain, boolean fracture,
                              List<WoundView> wounds) {
    }

    /**
     * A compact client-facing view of a single wound. Rides the per-limb delta inside {@link LimbSummary}
     * (a limb is only re-sent when its content changes), so no full resyncs are needed.
     *
     * @param typeId   the trauma type id (resolved to a display name / registry entry on the client)
     * @param severity 0..100 (quantised so continuous self-heal doesn't spam the delta)
     * @param flags    bitfield of the FLAG_* constants
     */
    public record WoundView(String typeId, int severity, int flags) {
        public static final int FLAG_BLEEDING = 1;
        public static final int FLAG_CONTROLLED = 1 << 1;
        public static final int FLAG_TREATED = 1 << 2;
        public static final int FLAG_STABILIZED = 1 << 3;
        public static final int FLAG_CLOSED = 1 << 4;
        public static final int FLAG_MAJOR = 1 << 5;

        public boolean bleeding() {
            return (flags & FLAG_BLEEDING) != 0;
        }

        public boolean bleedControlled() {
            return (flags & FLAG_CONTROLLED) != 0;
        }

        public boolean treated() {
            return (flags & FLAG_TREATED) != 0;
        }

        public boolean stabilized() {
            return (flags & FLAG_STABILIZED) != 0;
        }

        public boolean closed() {
            return (flags & FLAG_CLOSED) != 0;
        }

        public boolean major() {
            return (flags & FLAG_MAJOR) != 0;
        }
    }
}
