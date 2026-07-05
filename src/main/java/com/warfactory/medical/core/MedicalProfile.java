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
    /** Accumulating injectable-drug load; decays slowly, persisted, drives overdose -> blackout. */
    private float drugLoad;
    private boolean dirty = true;
    private DerivedStats cached = DerivedStats.healthy();

    // --- Transient active-treatment tracking. These fields are deliberately NOT written to / read from
    //     NBT in save()/load(): a partially-applied timed treatment must never survive a save/reload or a
    //     clone. They power the server-tracked "apply treatment to a chosen limb over N ticks" flow and the
    //     client action-progress overlay.
    private transient TreatmentAction activeAction;
    private transient LimbType activeLimb;
    private transient String activeItemId = "";
    private transient int activeTotalTicks;
    private transient long activeStartGameTime;
    private transient boolean activeTreatment;
    /** Client-provided targeting hint (nullable): the limb the player selected in the UI. Transient. */
    private transient LimbType preferredLimb;

    // --- Transient overdose-blackout tracking. Deliberately NOT written to / read from NBT: a blackout is
    //     a live timed state driven by the engine each tick and must never survive a save/reload or clone.
    //     drugLoad (above) IS persisted; the derived blackout is recomputed from it on the next injection.
    /** Game time at which the current blackout ends ({@code 0} = not blacked out). Transient. */
    private transient long blackoutUntilTick;
    /** Whether the player is currently blacked out; set by the engine each tick, read by Physiology. Transient. */
    private transient boolean blackoutActive;

    // --- Transient downed-broadcast bookkeeping. Deliberately NOT written to / read from NBT: it mirrors
    //     the last {@link #isDowned()} value the server broadcast to tracking clients so the engine can
    //     detect edges (enter / exit downed) and push a {@code DownedStatePacket} only on change. Starts
    //     {@code false} on a fresh / cloned / respawned profile, which is the correct "not downed" default.
    /** Last {@link #isDowned()} value broadcast to trackers; engine edge-detects against this. Transient. */
    private transient boolean lastBroadcastDowned;

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

    /**
     * Whether the player is "downed" — passed out and unable to act — which is true for BOTH an opioid
     * overdose blackout ({@link #isBlackoutActive()}) AND a bleeding-out knockdown
     * ({@link #getState()} == {@link HealthState#KNOCKED_DOWN}). This single predicate composes the two
     * unconscious states so the downed visual experience triggers uniformly for either cause.
     *
     * @return {@code true} while the player is blacked out or knocked down
     */
    public boolean isDowned() {
        return blackoutActive || state == HealthState.KNOCKED_DOWN;
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

    /** Accumulating injectable-drug load (>= 0); persisted, decays slowly, drives overdose -> blackout. */
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

    /** Game time at which the current blackout ends ({@code 0} = not blacked out). Transient, never NBT. */
    public long getBlackoutUntilTick() {
        return blackoutUntilTick;
    }

    public void setBlackoutUntilTick(long tick) {
        this.blackoutUntilTick = tick;
    }

    /** Whether the player is currently blacked out (engine-driven each tick). Transient, never NBT. */
    public boolean isBlackoutActive() {
        return blackoutActive;
    }

    public void setBlackoutActive(boolean value) {
        this.blackoutActive = value;
    }

    /** Last {@link #isDowned()} value the server broadcast to trackers (engine edge-detection). Transient. */
    public boolean isLastBroadcastDowned() {
        return lastBroadcastDowned;
    }

    public void setLastBroadcastDowned(boolean value) {
        this.lastBroadcastDowned = value;
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

    // ------------------------------------------------------------------ transient active treatment

    /** @return true while a timed treatment is being applied (server-tracked, never persisted). */
    public boolean hasActiveTreatment() {
        return activeTreatment;
    }

    /**
     * Begin tracking an active timed treatment. Purely transient bookkeeping; the actual physiology
     * mutation happens when the engine completes the treatment.
     *
     * @param action        the treatment action being applied
     * @param limb          the targeted limb (may be null for "auto pick")
     * @param itemId        registry-name string of the medical item being consumed
     * @param totalTicks    how many game ticks the application takes
     * @param startGameTime the level game time at which application started
     */
    public void setActiveTreatment(TreatmentAction action, LimbType limb, String itemId,
                                   int totalTicks, long startGameTime) {
        this.activeAction = action;
        this.activeLimb = limb;
        this.activeItemId = itemId == null ? "" : itemId;
        this.activeTotalTicks = totalTicks;
        this.activeStartGameTime = startGameTime;
        this.activeTreatment = true;
    }

    /** Clear any active treatment (completion, cancellation, death). Safe to call when none is active. */
    public void clearActiveTreatment() {
        this.activeTreatment = false;
        this.activeAction = null;
        this.activeLimb = null;
        this.activeItemId = "";
        this.activeTotalTicks = 0;
        this.activeStartGameTime = 0L;
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

    /** Client-selected targeting hint (nullable). */
    public LimbType getPreferredLimb() {
        return preferredLimb;
    }

    public void setPreferredLimb(LimbType limb) {
        this.preferredLimb = limb;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("BloodMl", bloodMl);
        tag.putDouble("MaxBloodMl", maxBloodMl);
        tag.putString("State", state.name());
        tag.putLong("KnockdownSince", knockdownSinceTick);
        tag.putFloat("PainSuppression", painSuppression);
        tag.putFloat("DrugLoad", drugLoad);
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
        // Clamp on load: a hand-edited/corrupt save must not leave drug load negative.
        this.drugLoad = Math.max(0.0F, tag.getFloat("DrugLoad"));
        CompoundTag limbTag = tag.getCompound("Limbs");
        for (LimbType lt : LimbType.VALUES) {
            if (limbTag.contains(lt.name())) {
                limbs.get(lt).load(limbTag.getCompound(lt.name()), registry);
            }
        }
        this.dirty = true;
    }
}
