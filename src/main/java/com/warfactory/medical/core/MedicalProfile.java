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
    private long bleedoutSinceTick = -1L;
    /**
     * ANALGESIA (0..1) from systemic painkillers/opioids: a body-wide subtractive mask applied to every
     * limb's felt pain (small pains vanish, big ones are lessened). Decays over time, never heals the wound.
     * The LOCAL ANESTHETIC (a single limb) lives per-{@link Limb} instead – that is the local/general split.
     */
    private float painSuppression;
    /**
     * Accumulating injectable-drug load; decays slowly, persisted, drives overdose -> unconsciousness.
     */
    private float drugLoad;
    /**
     * Timed CLOTTING BOOST strength (0..1) from a hemostatic / combat stimulant: raises both the severity a
     * bleeding wound can self-clot at AND the rate it clots. Full strength until {@link #clottingBoostEndTick},
     * then off. Persisted (the absolute end tick survives a reload since world game-time does).
     */
    private float clottingBoost;
    private long clottingBoostEndTick;
    /**
     * Timed STIMULANT strength (0..1) from a combat stimulant: drives strong anesthesia, a movement-speed boost
     * and a cleared jump penalty. Full strength until {@link #stimulantEndTick}, then off. Persisted. Note the
     * beneficial effect ends here, but the injected {@link #drugLoad} (overdose risk) lingers LONGER – the crash.
     */
    private float stimulant;
    private long stimulantEndTick;
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
    /**
     * Entity id of who the ACTIVE treatment is being applied TO ({@code -1} = the actor themself). The timer
     * lives on the actor (this profile); the physiology mutation on completion lands on this target's profile,
     * enabling a medic to treat another player / a downed body. Transient, never persisted.
     */
    private transient int activeTargetId = -1;
    /**
     * Inventory slot the actor's treatment item occupies at start. Re-checked each tick so switching the held
     * item away mid-treatment cancels it (the "cannot change items while applying" rule). Transient.
     */
    private transient int activeSlot = -1;
    /**
     * Client-provided targeting hint (nullable): the limb the player selected in the UI. Transient.
     */
    private transient LimbType preferredLimb;

    // --- Transient overdose-unconsciousness tracking. These are the INTERNAL "overdose" CAUSE markers of the
    //     single externally-visible UNCONSCIOUS state (as opposed to a bleed-out unconsciousness, marked by
    //     bleedoutSinceTick): while overdoseUnconscious is set, Physiology raises the state to UNCONSCIOUS and
    //     the engine runs a WAKE timer (recover) rather than a death timer. Deliberately NOT written to / read
    //     from NBT: an overdose unconsciousness is a live timed state driven by the engine each tick and must
    //     never survive a save/reload or clone. drugLoad (above) IS persisted; the derived unconsciousness is
    //     recomputed from it on the next injection.
    /**
     * Game time at which the current overdose unconsciousness ends ({@code 0} = conscious). Transient.
     */
    private transient long overdoseUntilTick;
    /**
     * Whether the player is currently overdose-unconscious; set by the engine each tick, read by Physiology. Transient.
     */
    private transient boolean overdoseUnconscious;
    /**
     * Whether the player is in the overdose ASPHYXIA phase – the conscious, pre-unconsciousness respiratory
     * depression triggered probabilistically by a heavy overdose. While set, a per-tick engine hook drains the
     * player's air (sped-up drowning) and Physiology reports weakness/no-sprint/blur; when the air is exhausted
     * the player tips into {@link #overdoseUnconscious}. Transient (a live timed state; never persisted).
     */
    private transient boolean asphyxiating;
    /**
     * Whether the player has PASSED OUT from asphyxia (drowning or drug respiratory depression). Unlike an
     * overdose unconsciousness (a wake timer), this is FATAL unless the cause is cleared (surfaced / drug
     * reversed) before {@link #asphyxiaDeadlineTick}. Transient; engine-driven each tick; never persisted.
     */
    private transient boolean asphyxiaUnconscious;
    /**
     * Game time the current (conscious) asphyxia episode began ({@code 0} = not asphyxiating); drives the
     * struggle-before-passout window. Transient, never persisted.
     */
    private transient long asphyxiaSince;
    /**
     * Game time at which asphyxia unconsciousness turns fatal unless the cause is cleared first. Transient.
     */
    private transient long asphyxiaDeadlineTick;

    // --- Transient ADRENALINE bookkeeping for the delayed pain knockout. A pain-driven collapse (one blood
    //     loss alone would not cause) is held off for a grace period, mimicking adrenaline keeping the player
    //     on their feet through the pain before they finally drop. painKoSince records when pain first reached
    //     knockout level (0 = pain is not currently trying to knock them out); adrenalineExhausted is set by
    //     the engine once that grace has elapsed, at which point Physiology lets the pain knockout land. Both
    //     are transient live state driven by the engine each tick and are deliberately never persisted.
    /**
     * Game time at which pain first reached knockout level ({@code 0} = not currently pain-KO-pending). Transient.
     */
    private transient long painKoSince;
    /**
     * Whether the adrenaline grace has run out, so pain may now actually knock the player out. Transient.
     */
    private transient boolean adrenalineExhausted;

    // --- Transient BLACKOUT-GRACE + WAKE-LATCH bookkeeping. A drug/asphyxia blackout no longer lands
    //     instantly: it starts a short "adrenaline-style" grace (blackoutGraceUntil) during which the player
    //     stays conscious and can still be saved (antidote / surfacing), then commits. Once unconscious from
    //     ANY cause the engine LATCHES the state (unconsciousLatched) so improving stats do not snap the player
    //     back up; only the engine's per-recompute wake roll (gated by the wakeup score) clears it. Both are
    //     transient live state driven by the engine each tick and are deliberately never persisted.
    /**
     * Game tick at which a pending drug/asphyxia blackout commits ({@code 0} = none pending). Transient.
     */
    private transient long blackoutGraceUntil;
    /**
     * Latched once the player is unconscious from any cause; {@link Physiology} holds the derived UNCONSCIOUS
     * state while set, and only a successful engine wake roll clears it. Transient, never persisted.
     */
    private transient boolean unconsciousLatched;

    // --- Transient downed-broadcast bookkeeping. Deliberately NOT written to / read from NBT: it mirrors
    //     the last {@link #isDowned()} value the server broadcast to tracking clients so the engine can
    //     detect edges (enter / exit downed) and push a {@code DownedStatePacket} only on change. Starts
    //     {@code false} on a fresh / cloned / respawned profile, which is the correct "not downed" default.
    /**
     * Last {@link #isDowned()} value broadcast to trackers; engine edge-detects against this. Transient.
     */
    private transient boolean lastBroadcastDowned;

    /**
     * Transient 0..1 "closeness to actual death": how far the bleed-out death timer has run (0 = just went
     * down / not dying – e.g. an overdose unconsciousness that will recover, 1 = about to die). Derived by the
     * engine each tick and synced so the client overlay can ramp from an extreme vignette (while merely downed)
     * to a full-screen blackout only right before death. Never persisted (a live, engine-driven value).
     */
    private transient float deathProgress;

    // --- Transient admin-forced state override. Deliberately NOT written to / read from NBT: a forced
    //     state is a live debug/admin override (e.g. /wfmedical unconscious on an uninjured player) and must
    //     never survive a save/reload or clone. Honoured by {@link Physiology}, which pins the derived
    //     state to at least this severity so the forced state, its mobility lock and the downed pose survive
    //     every recompute instead of being clobbered back to the pure physiology-derived state.
    /**
     * Admin-forced health state (nullable = no override); never downgrades a worse derived state. Transient.
     */
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
     * Admin-forced health-state override (nullable). Physiology pins the derived state to at least this
     * severity so an operator-forced UNCONSCIOUS is not clobbered back to HEALTHY on the next recompute.
     * Transient, never persisted.
     */
    public HealthState getForcedState() {
        return forcedState;
    }

    public void setForcedState(HealthState forcedState) {
        this.forcedState = forcedState;
    }

    /**
     * Sets DEAD state and clears the full union of transient downed markers (overdose, asphyxia, bleed-out)
     * in one place so no death-enactment site can miss any. {@code pinForced} pins the forced override to
     * DEAD until the vanilla death event fires; pass {@code false} at final finalization to release it.
     */
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

    /**
     * Whether the player is downed (unconscious). Checks both the state and the overdose/asphyxia markers
     * as belt-and-braces for the tick in which a cause marker is set but the state has not yet been recomputed.
     */
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

    /**
     * Current clotting-boost strength (0..1); active until {@link #getClottingBoostEndTick()}.
     */
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

    /**
     * Current stimulant strength (0..1); active until {@link #getStimulantEndTick()}.
     */
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

    /**
     * Game time the overdose unconsciousness ends (0 = conscious). Transient, never NBT.
     */
    public long getOverdoseUntilTick() {
        return overdoseUntilTick;
    }

    public void setOverdoseUntilTick(long tick) {
        this.overdoseUntilTick = tick;
    }

    /**
     * Whether the player is currently overdose-unconscious (engine-driven). Transient, never NBT.
     */
    public boolean isOverdoseUnconscious() {
        return overdoseUnconscious;
    }

    public void setOverdoseUnconscious(boolean value) {
        this.overdoseUnconscious = value;
    }

    /**
     * Whether the player is in the overdose asphyxia phase (engine-driven). Transient, never NBT.
     */
    public boolean isAsphyxiating() {
        return asphyxiating;
    }

    public void setAsphyxiating(boolean value) {
        this.asphyxiating = value;
    }

    /**
     * Whether the player has passed out from asphyxia (fatal unless cause clears). Transient, never NBT.
     */
    public boolean isAsphyxiaUnconscious() {
        return asphyxiaUnconscious;
    }

    public void setAsphyxiaUnconscious(boolean value) {
        this.asphyxiaUnconscious = value;
    }

    /**
     * Game time the current (conscious) asphyxia episode began (0 = none). Transient, never NBT.
     */
    public long getAsphyxiaSince() {
        return asphyxiaSince;
    }

    public void setAsphyxiaSince(long tick) {
        this.asphyxiaSince = tick;
    }

    /**
     * Game time asphyxia unconsciousness turns fatal unless the cause is cleared. Transient, never NBT.
     */
    public long getAsphyxiaDeadlineTick() {
        return asphyxiaDeadlineTick;
    }

    public void setAsphyxiaDeadlineTick(long tick) {
        this.asphyxiaDeadlineTick = tick;
    }

    /**
     * Begin a conscious asphyxia episode at {@code now} (idempotent: does not restart an ongoing one).
     */
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

    /**
     * Game time pain first reached knockout level (0 = not pending). Transient, never NBT.
     */
    public long getPainKoSince() {
        return painKoSince;
    }

    public void setPainKoSince(long tick) {
        this.painKoSince = tick;
    }

    /**
     * Whether the adrenaline grace has elapsed, letting a pain-driven knockout land. Transient, never NBT.
     */
    public boolean isAdrenalineExhausted() {
        return adrenalineExhausted;
    }

    public void setAdrenalineExhausted(boolean value) {
        this.adrenalineExhausted = value;
    }

    /**
     * Game tick a pending drug/asphyxia blackout commits ({@code 0} = none pending). Transient, never NBT.
     */
    public long getBlackoutGraceUntil() {
        return blackoutGraceUntil;
    }

    public void setBlackoutGraceUntil(long tick) {
        this.blackoutGraceUntil = tick;
    }

    /**
     * Whether the unconscious state is latched (held across recomputes until a wake roll clears it). Transient.
     */
    public boolean isUnconsciousLatched() {
        return unconsciousLatched;
    }

    public void setUnconsciousLatched(boolean value) {
        this.unconsciousLatched = value;
    }

    /**
     * Whether ANY limb currently carries localized numbing (a local anesthetic still in effect). Used by the
     * engine to keep decaying it even when no other physiology condition is active.
     */
    public boolean anyLocalNumbing() {
        for (LimbType lt : LimbType.VALUES) {
            if (limbs.get(lt).getLocalNumbing() > 0.0F) {
                return true;
            }
        }
        return false;
    }

    /**
     * Last isDowned() value broadcast to trackers; engine edge-detects against this. Transient.
     */
    public boolean isLastBroadcastDowned() {
        return lastBroadcastDowned;
    }

    public void setLastBroadcastDowned(boolean value) {
        this.lastBroadcastDowned = value;
    }

    /**
     * Current 0..1 bleed-out death-timer progress (0 = not dying, 1 = about to die). Transient, never NBT.
     */
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

    /**
     * Snapshot of every trauma across all limbs (allocates a new list).
     */
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

    /**
     * @return true while a timed treatment is being applied (server-tracked, never persisted).
     */
    public boolean hasActiveTreatment() {
        return activeTreatment;
    }

    /**
     * Begin tracking a timed treatment. Transient bookkeeping only; physiology mutates on completion.
     *
     * @param targetId entity id of who is being treated ({@code -1} = the actor themself)
     * @param slot     the actor inventory slot holding the treatment item (re-checked to enforce no item swap)
     */
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

    /**
     * Entity id the active treatment is being applied to ({@code -1} = the actor themself).
     */
    public int getActiveTargetId() {
        return activeTargetId;
    }

    /**
     * Actor inventory slot the active treatment item occupies ({@code -1} = none tracked).
     */
    public int getActiveSlot() {
        return activeSlot;
    }

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
        tag.putLong("BleedoutSince", bleedoutSinceTick);
        tag.putFloat("PainSuppression", painSuppression);
        tag.putFloat("DrugLoad", drugLoad);
        tag.putFloat("ClottingBoost", clottingBoost);
        tag.putLong("ClottingBoostEnd", clottingBoostEndTick);
        tag.putFloat("Stimulant", stimulant);
        tag.putLong("StimulantEnd", stimulantEndTick);
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
        if (tag.contains("BleedoutSince")) {
            this.bleedoutSinceTick = tag.getLong("BleedoutSince");
        } else {
            this.bleedoutSinceTick = tag.getLong("KnockdownSince"); // legacy pre-rename saves
        }
        this.painSuppression = Math.max(0.0F, Math.min(tag.getFloat("PainSuppression"), 1.0F));
        // Clamp on load: a hand-edited/corrupt save must not leave drug load negative.
        this.drugLoad = Math.max(0.0F, tag.getFloat("DrugLoad"));
        this.clottingBoost = Math.max(0.0F, Math.min(tag.getFloat("ClottingBoost"), 1.0F));
        this.clottingBoostEndTick = tag.getLong("ClottingBoostEnd");
        this.stimulant = Math.max(0.0F, Math.min(tag.getFloat("Stimulant"), 1.0F));
        this.stimulantEndTick = tag.getLong("StimulantEnd");
        CompoundTag limbTag = tag.getCompound("Limbs");
        for (LimbType lt : LimbType.VALUES) {
            if (limbTag.contains(lt.name())) {
                limbs.get(lt).load(limbTag.getCompound(lt.name()), registry);
            }
        }
        this.dirty = true;
    }
}
