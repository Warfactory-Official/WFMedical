package com.warfactory.medical.core;

import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * The complete medical state of a single player: one {@link Limb} per body part, a blood pool, the
 * current {@link HealthState}, and a cached {@link DerivedStats} snapshot.
 *
 * <p>Marked {@code dirty} whenever trauma/blood changes so the scheduled physiology update can skip
 * unchanged players entirely.</p>
 */
public final class MedicalProfile {

    private final EnumMap<LimbType, Limb> limbs = new EnumMap<>(LimbType.class);
    private double bloodMl;
    private double maxBloodMl;
    private HealthState state = HealthState.HEALTHY;
    private long knockdownSinceTick = -1L;
    /** Perceived-pain suppression (0..1) from painkillers; decays over time, never heals the wound. */
    private float painSuppression;
    private boolean dirty = true;
    private DerivedStats cached = DerivedStats.healthy();

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

    public long getKnockdownSinceTick() {
        return knockdownSinceTick;
    }

    public void setKnockdownSinceTick(long tick) {
        this.knockdownSinceTick = tick;
    }

    /** Current perceived-pain suppression fraction (0..1). */
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

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    /** Attach a new trauma to a limb, marking both the limb and this profile dirty. */
    public void addTrauma(LimbType limbType, Trauma trauma) {
        limbs.get(limbType).addTrauma(trauma);
        this.dirty = true;
    }

    /** Snapshot of every trauma across all limbs (allocates a new list). */
    public List<Trauma> allTraumas() {
        List<Trauma> out = new ArrayList<>();
        for (LimbType lt : LimbType.VALUES) {
            out.addAll(limbs.get(lt).getTraumas());
        }
        return out;
    }

    /**
     * Rebuild any dirty limb caches, run the pure {@link Physiology} pass, store and return the
     * resulting snapshot, then clear all dirty flags. Idempotent for a clean profile.
     */
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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("BloodMl", bloodMl);
        tag.putDouble("MaxBloodMl", maxBloodMl);
        tag.putString("State", state.name());
        tag.putLong("KnockdownSince", knockdownSinceTick);
        tag.putFloat("PainSuppression", painSuppression);
        CompoundTag limbTag = new CompoundTag();
        for (LimbType lt : LimbType.VALUES) {
            limbTag.put(lt.name(), limbs.get(lt).save());
        }
        tag.put("Limbs", limbTag);
        return tag;
    }

    public void load(CompoundTag tag, TraumaRegistry registry) {
        this.maxBloodMl = tag.contains("MaxBloodMl") ? tag.getDouble("MaxBloodMl") : this.maxBloodMl;
        // Clamp on load: a config change or hand-edited/corrupt save must not leave blood out of range.
        double loadedBlood = tag.getDouble("BloodMl");
        this.bloodMl = Math.max(0.0D, Math.min(loadedBlood, this.maxBloodMl));
        this.state = HealthState.byName(tag.getString("State"), HealthState.HEALTHY);
        this.knockdownSinceTick = tag.getLong("KnockdownSince");
        this.painSuppression = Math.max(0.0F, Math.min(tag.getFloat("PainSuppression"), 1.0F));
        CompoundTag limbTag = tag.getCompound("Limbs");
        for (LimbType lt : LimbType.VALUES) {
            if (limbTag.contains(lt.name())) {
                limbs.get(lt).load(limbTag.getCompound(lt.name()), registry);
            }
        }
        this.dirty = true;
    }
}
