package com.warfactory.medical.core;

import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.treatment.TreatmentAction;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

public final class MedicalProfile {

    /** Starting bpm for a fresh profile; a live one converges on the configured resting rate within seconds. */
    public static final float DEFAULT_HEART_RATE = 80.0F;

    private final EnumMap<LimbType, Limb> limbs = new EnumMap<>(LimbType.class);
    private double bloodMl;
    private double maxBloodMl;
    private HealthState state = HealthState.HEALTHY;
    private float heartRate = DEFAULT_HEART_RATE;
    private long bleedoutSinceTick = -1L;
    private float painSuppression;
    private float drugLoad;
    private float clottingBoost;
    private long clottingBoostEndTick;
    private float stimulant;
    private long stimulantEndTick;
    private UUID lastDamagingPlayer;
    private long lastDamageTick = Long.MIN_VALUE;
    private boolean dirty = true;
    private DerivedStats cached = DerivedStats.healthy();

    private transient TreatmentAction activeAction;
    private transient LimbType activeLimb;
    private transient String activeItemId = "";
    private transient int activeTotalTicks;
    private transient long activeStartGameTime;
    private transient boolean activeTreatment;
    private transient int activeTargetId = -1;
    private transient int activeSlot = -1;

    private transient long overdoseUntilTick;
    private transient boolean overdoseUnconscious;
    private transient boolean asphyxiating;
    private transient boolean asphyxiaUnconscious;
    private transient long asphyxiaSince;
    private transient long asphyxiaDeadlineTick;

    private transient long painKoSince;
    private transient boolean adrenalineExhausted;
    /** Game tick until which a just-resuscitated casualty is held conscious; 0 = no grace. Engine-cleared. */
    private transient long reviveGraceUntilTick;

    private transient long blackoutGraceUntil;
    private transient boolean unconsciousLatched;

    private transient boolean lastBroadcastDowned;

    private transient float deathProgress;

    private transient HealthState forcedState;

    public MedicalProfile() {
        this(PhysiologyParams.defaults().maxBloodMl());
    }

    public MedicalProfile(double maxBloodMl) {
        this.maxBloodMl = maxBloodMl;
        this.bloodMl = maxBloodMl;
        for (LimbType lt : LimbType.VALUES) {
            limbs.put(lt, new Limb(lt));
        }
    }

    public Limb limb(LimbType type) {
        return limbs.get(type);
    }

    public double getBloodMl() {
        return bloodMl;
    }

    public void setBloodMl(double bloodMl) {
        double clamped = bloodMl < 0.0D ? 0.0D : (bloodMl > maxBloodMl ? maxBloodMl : bloodMl);
        if (clamped != this.bloodMl) {
            this.bloodMl = clamped;
            this.dirty = true;
        }
    }

    public double getMaxBloodMl() {
        return maxBloodMl;
    }

    public float getHeartRate() {
        return heartRate;
    }

    /**
     * Beats per minute. Advanced once per engine interval by {@link Cardio}; it feeds cardiac output, so a
     * change has to dirty the profile for the bleed rate to follow it.
     */
    public void setHeartRate(float bpm) {
        float clamped = bpm < 0.0F ? 0.0F : bpm;
        if (clamped != this.heartRate) {
            this.heartRate = clamped;
            this.dirty = true;
        }
    }

    public void setMaxBloodMl(double maxBloodMl) {
        this.maxBloodMl = maxBloodMl;
        if (this.bloodMl > maxBloodMl) {
            this.bloodMl = maxBloodMl;
        }
        this.dirty = true;
    }

    public HealthState getState() {
        return state;
    }

    public void setState(HealthState state) {
        this.state = state;
    }

    public HealthState getForcedState() {
        return forcedState;
    }

    public void setForcedState(HealthState forcedState) {
        this.forcedState = forcedState;
    }

    public void enterDeadState(boolean pinForced) {
        setState(HealthState.DEAD);
        setForcedState(pinForced ? HealthState.DEAD : null);
        setOverdoseUnconscious(false);
        setOverdoseUntilTick(0L);
        setBleedoutSinceTick(-1L);
        clearAsphyxia();
        setBlackoutGraceUntil(0L);
        setUnconsciousLatched(false);
        markDirty();
    }

    public boolean isDowned() {
        return overdoseUnconscious || asphyxiaUnconscious || state == HealthState.UNCONSCIOUS;
    }

    public long getBleedoutSinceTick() {
        return bleedoutSinceTick;
    }

    public void setBleedoutSinceTick(long tick) {
        this.bleedoutSinceTick = tick;
    }

    public float getPainSuppression() {
        return painSuppression;
    }

    public void setPainSuppression(float value) {
        float clamped = value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
        if (clamped != this.painSuppression) {
            this.painSuppression = clamped;
            this.dirty = true;
        }
    }

    public float getDrugLoad() {
        return drugLoad;
    }

    public void setDrugLoad(float value) {
        float clamped = value < 0.0F ? 0.0F : value;
        if (clamped != this.drugLoad) {
            this.drugLoad = clamped;
            this.dirty = true;
        }
    }

    public float getClottingBoost() {
        return clottingBoost;
    }

    public void setClottingBoost(float value) {
        float clamped = value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
        if (clamped != this.clottingBoost) {
            this.clottingBoost = clamped;
            this.dirty = true;
        }
    }

    public long getClottingBoostEndTick() {
        return clottingBoostEndTick;
    }

    public void setClottingBoostEndTick(long tick) {
        this.clottingBoostEndTick = tick;
    }

    public float getStimulant() {
        return stimulant;
    }

    public void setStimulant(float value) {
        float clamped = value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
        if (clamped != this.stimulant) {
            this.stimulant = clamped;
            this.dirty = true;
        }
    }

    public long getStimulantEndTick() {
        return stimulantEndTick;
    }

    public void setStimulantEndTick(long tick) {
        this.stimulantEndTick = tick;
    }

    public long getOverdoseUntilTick() {
        return overdoseUntilTick;
    }

    public void setOverdoseUntilTick(long tick) {
        this.overdoseUntilTick = tick;
    }

    public boolean isOverdoseUnconscious() {
        return overdoseUnconscious;
    }

    public void setOverdoseUnconscious(boolean value) {
        this.overdoseUnconscious = value;
    }

    public boolean isAsphyxiating() {
        return asphyxiating;
    }

    public void setAsphyxiating(boolean value) {
        this.asphyxiating = value;
    }

    public boolean isAsphyxiaUnconscious() {
        return asphyxiaUnconscious;
    }

    public void setAsphyxiaUnconscious(boolean value) {
        this.asphyxiaUnconscious = value;
    }

    public long getAsphyxiaSince() {
        return asphyxiaSince;
    }

    public void setAsphyxiaSince(long tick) {
        this.asphyxiaSince = tick;
    }

    public long getAsphyxiaDeadlineTick() {
        return asphyxiaDeadlineTick;
    }

    public void setAsphyxiaDeadlineTick(long tick) {
        this.asphyxiaDeadlineTick = tick;
    }

    public void startAsphyxia(long now) {
        if (!asphyxiating && !asphyxiaUnconscious) {
            this.asphyxiating = true;
            this.asphyxiaSince = now;
        }
    }

    public void clearAsphyxia() {
        this.asphyxiating = false;
        this.asphyxiaUnconscious = false;
        this.asphyxiaSince = 0L;
        this.asphyxiaDeadlineTick = 0L;
    }

    public long getPainKoSince() {
        return painKoSince;
    }

    public void setPainKoSince(long tick) {
        this.painKoSince = tick;
    }

    public long getReviveGraceUntilTick() {
        return reviveGraceUntilTick;
    }

    public void setReviveGraceUntilTick(long tick) {
        this.reviveGraceUntilTick = tick;
    }

    public boolean isReviveGraceActive() {
        return reviveGraceUntilTick > 0L;
    }

    public boolean isAdrenalineExhausted() {
        return adrenalineExhausted;
    }

    public void setAdrenalineExhausted(boolean value) {
        this.adrenalineExhausted = value;
    }

    public long getBlackoutGraceUntil() {
        return blackoutGraceUntil;
    }

    public void setBlackoutGraceUntil(long tick) {
        this.blackoutGraceUntil = tick;
    }

    public boolean isUnconsciousLatched() {
        return unconsciousLatched;
    }

    public void setUnconsciousLatched(boolean value) {
        this.unconsciousLatched = value;
    }

    public boolean anyLocalNumbing() {
        for (LimbType lt : LimbType.VALUES) {
            if (limbs.get(lt).getLocalNumbing() > 0.0F) {
                return true;
            }
        }
        return false;
    }

    public boolean isLastBroadcastDowned() {
        return lastBroadcastDowned;
    }

    public void setLastBroadcastDowned(boolean value) {
        this.lastBroadcastDowned = value;
    }

    public float getDeathProgress() {
        return deathProgress;
    }

    public void setDeathProgress(float value) {
        float clamped = value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
        if (clamped != this.deathProgress) {
            this.deathProgress = clamped;
            this.dirty = true;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void addTrauma(LimbType limbType, Trauma trauma) {
        limbs.get(limbType).addTrauma(trauma);
        this.dirty = true;
    }

    public List<Trauma> allTraumas() {
        List<Trauma> out = new ArrayList<>();
        for (LimbType lt : LimbType.VALUES) {
            out.addAll(limbs.get(lt).getTraumas());
        }
        return out;
    }

    public DerivedStats recompute(PhysiologyParams cfg) {
        for (LimbType lt : LimbType.VALUES) {
            Limb limb = limbs.get(lt);
            if (limb.isDirty()) {
                limb.rebuildCache();
            }
        }
        DerivedStats stats = Physiology.compute(this, cfg);
        this.cached = stats;
        this.state = stats.state();
        this.dirty = false;
        return stats;
    }

    public DerivedStats cached() {
        return cached;
    }


    public boolean hasActiveTreatment() {
        return activeTreatment;
    }

    public void setActiveTreatment(TreatmentAction action, LimbType limb, String itemId,
                                   int totalTicks, long startGameTime, int targetId, int slot) {
        this.activeAction = action;
        this.activeLimb = limb;
        this.activeItemId = itemId == null ? "" : itemId;
        this.activeTotalTicks = totalTicks;
        this.activeStartGameTime = startGameTime;
        this.activeTargetId = targetId;
        this.activeSlot = slot;
        this.activeTreatment = true;
    }

    public void clearActiveTreatment() {
        this.activeTreatment = false;
        this.activeAction = null;
        this.activeLimb = null;
        this.activeItemId = "";
        this.activeTotalTicks = 0;
        this.activeStartGameTime = 0L;
        this.activeTargetId = -1;
        this.activeSlot = -1;
    }

    public TreatmentAction getActiveAction() {
        return activeAction;
    }

    public LimbType getActiveLimb() {
        return activeLimb;
    }

    public String getActiveItemId() {
        return activeItemId;
    }

    public int getActiveTotalTicks() {
        return activeTotalTicks;
    }

    public long getActiveStartGameTime() {
        return activeStartGameTime;
    }

    public int getActiveTargetId() {
        return activeTargetId;
    }

    public int getActiveSlot() {
        return activeSlot;
    }

    public UUID getLastDamagingPlayer() {
        return lastDamagingPlayer;
    }

    public long getLastDamageTick() {
        return lastDamageTick;
    }

    public void setLastDamagingPlayer(UUID uuid, long gameTick) {
        this.lastDamagingPlayer = uuid;
        this.lastDamageTick = gameTick;
    }

    public void clearLastDamagingPlayer() {
        this.lastDamagingPlayer = null;
        this.lastDamageTick = Long.MIN_VALUE;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("BloodMl", bloodMl);
        tag.putDouble("MaxBloodMl", maxBloodMl);
        tag.putString("State", state.name());
        tag.putFloat("HeartRate", heartRate);
        tag.putLong("BleedoutSince", bleedoutSinceTick);
        tag.putFloat("PainSuppression", painSuppression);
        tag.putFloat("DrugLoad", drugLoad);
        tag.putFloat("ClottingBoost", clottingBoost);
        tag.putLong("ClottingBoostEnd", clottingBoostEndTick);
        tag.putFloat("Stimulant", stimulant);
        tag.putLong("StimulantEnd", stimulantEndTick);
        if (lastDamagingPlayer != null) {
            tag.putUUID("LastDamagingPlayer", lastDamagingPlayer);
            tag.putLong("LastDamageTick", lastDamageTick);
        }
        CompoundTag limbTag = new CompoundTag();
        for (LimbType lt : LimbType.VALUES) {
            limbTag.put(lt.name(), limbs.get(lt).save());
        }
        tag.put("Limbs", limbTag);
        return tag;
    }

    public void load(CompoundTag tag, TraumaRegistry registry) {
        this.maxBloodMl = tag.contains("MaxBloodMl") ? tag.getDouble("MaxBloodMl") : this.maxBloodMl;
        double loadedBlood = tag.getDouble("BloodMl");
        this.bloodMl = Math.max(0.0D, Math.min(loadedBlood, this.maxBloodMl));
        this.state = HealthState.byName(tag.getString("State"), HealthState.HEALTHY);
        this.heartRate = tag.contains("HeartRate") ? Math.max(0.0F, tag.getFloat("HeartRate")) : DEFAULT_HEART_RATE;
        if (tag.contains("BleedoutSince")) {
            this.bleedoutSinceTick = tag.getLong("BleedoutSince");
        } else {
            this.bleedoutSinceTick = tag.getLong("KnockdownSince");
        }
        this.painSuppression = Math.max(0.0F, Math.min(tag.getFloat("PainSuppression"), 1.0F));
        this.drugLoad = Math.max(0.0F, tag.getFloat("DrugLoad"));
        this.clottingBoost = Math.max(0.0F, Math.min(tag.getFloat("ClottingBoost"), 1.0F));
        this.clottingBoostEndTick = tag.getLong("ClottingBoostEnd");
        this.stimulant = Math.max(0.0F, Math.min(tag.getFloat("Stimulant"), 1.0F));
        this.stimulantEndTick = tag.getLong("StimulantEnd");
        if (tag.hasUUID("LastDamagingPlayer")) {
            this.lastDamagingPlayer = tag.getUUID("LastDamagingPlayer");
            this.lastDamageTick = tag.getLong("LastDamageTick");
        } else {
            this.lastDamagingPlayer = null;
            this.lastDamageTick = Long.MIN_VALUE;
        }
        CompoundTag limbTag = tag.getCompound("Limbs");
        for (LimbType lt : LimbType.VALUES) {
            if (limbTag.contains(lt.name())) {
                limbs.get(lt).load(limbTag.getCompound(lt.name()), registry);
            }
        }
        this.dirty = true;
    }
}
