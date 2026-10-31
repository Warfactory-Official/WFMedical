package com.warfactory.medical.body.limb;

import com.warfactory.medical.body.trauma.Trauma;
import com.warfactory.medical.body.trauma.TraumaTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public final class Limb {

    private final LimbType type;
    private final List<Trauma> traumas = new ArrayList<>();
    private float maxHealth;
    private float minorDamage;
    private float localNumbing;
    private boolean tourniquet;
    private double cachedBleeding;
    private float cachedPain;
    private float cachedHealthReduction;
    private float cachedCurrentHealthReduction;
    private float cachedMovementMultiplier = 1.0F;
    private boolean cachedFracture;
    private boolean dirty;

    public Limb(LimbType type) {
        this(type, 10.0F);
    }

    public Limb(LimbType type, float maxHealth) {
        this.type = type;
        this.maxHealth = maxHealth;
    }

    public LimbType getType() {
        return type;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
    }

    public float getMinorDamage() {
        return minorDamage;
    }

    public void setMinorDamage(float minorDamage) {
        this.minorDamage = minorDamage < 0.0F ? 0.0F : minorDamage;
    }

    public float getLocalNumbing() {
        return localNumbing;
    }

    public void setLocalNumbing(float value) {
        float clamped = value < 0.0F ? 0.0F : (Math.min(value, 1.0F));
        if (clamped != this.localNumbing) {
            this.localNumbing = clamped;
            this.dirty = true;
        }
    }

    public boolean hasTourniquet() {
        return tourniquet;
    }

    public void setTourniquet(boolean value) {
        if (value != this.tourniquet) {
            this.tourniquet = value;
            this.dirty = true;
        }
    }

    public List<Trauma> getTraumas() {
        return traumas;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void addTrauma(Trauma trauma) {
        traumas.add(trauma);
        dirty = true;
    }

    public boolean removeTrauma(Trauma trauma) {
        boolean removed = traumas.remove(trauma);
        if (removed) {
            dirty = true;
        }
        return removed;
    }

    public void tryMerge(Trauma incoming, int maxPerLimb) {
        Trauma mergeTarget = null;
        for (int i = 0; i < traumas.size(); i++) {
            Trauma t = traumas.get(i);
            if (t.canMergeWith(incoming)) {
                mergeTarget = t;
                break;
            }
        }
        if (mergeTarget != null) {
            mergeTarget.mergeIn(incoming);
        } else {
            traumas.add(incoming);
        }
        enforceCap(maxPerLimb);
        dirty = true;
    }

    private void enforceCap(int maxPerLimb) {
        if (maxPerLimb <= 0) {
            return;
        }
        while (traumas.size() > maxPerLimb) {
            int dropIdx = -1;
            for (int i = 0; i < traumas.size(); i++) {
                if (!traumas.get(i).isMinor()) {
                    continue;
                }
                if (dropIdx < 0 || traumas.get(i).getSeverity() < traumas.get(dropIdx).getSeverity()) {
                    dropIdx = i;
                }
            }
            if (dropIdx < 0) {
                for (int i = 0; i < traumas.size(); i++) {
                    if (dropIdx < 0 || traumas.get(i).getSeverity() < traumas.get(dropIdx).getSeverity()) {
                        dropIdx = i;
                    }
                }
            }
            if (dropIdx < 0) {
                break;
            }
            Trauma drop = traumas.get(dropIdx);

            int keepIdx = -1;
            for (int i = 0; i < traumas.size(); i++) {
                if (i == dropIdx) {
                    continue;
                }
                if (keepIdx < 0) {
                    keepIdx = i;
                    continue;
                }
                boolean compat = traumas.get(i).canMergeWith(drop);
                boolean keepCompat = traumas.get(keepIdx).canMergeWith(drop);
                if ((compat && !keepCompat)
                        || (compat == keepCompat && traumas.get(i).getSeverity() > traumas.get(keepIdx).getSeverity())) {
                    keepIdx = i;
                }
            }
            if (keepIdx < 0) {
                break;
            }
            traumas.get(keepIdx).mergeIn(drop);
            traumas.remove(dropIdx);
        }
    }

    public void rebuildCache() {
        double bleeding = 0.0D;
        float pain = 0.0F;
        float healthReduction = 0.0F;
        float currentHealthReduction = 0.0F;
        float movement = 1.0F;
        boolean fracture = false;
        for (int i = 0; i < traumas.size(); i++) {
            Trauma t = traumas.get(i);
            bleeding += t.bleeding();
            pain += t.pain();
            healthReduction += t.healthReduction();
            currentHealthReduction += t.currentHealthReduction();
            if (type.isLeg()) {
                movement *= t.getType().getMovementModifier();
            }
            if (t.isFracture() && !t.isStabilized()) {
                fracture = true;
            }
        }
        this.cachedBleeding = bleeding;
        this.cachedPain = pain;
        this.cachedHealthReduction = healthReduction;
        this.cachedCurrentHealthReduction = currentHealthReduction;
        this.cachedMovementMultiplier = movement;
        this.cachedFracture = fracture;
        this.dirty = false;
    }

    public double getCachedBleeding() {
        return cachedBleeding;
    }

    public float getCachedPain() {
        return cachedPain;
    }

    public float getCachedHealthReduction() {
        return cachedHealthReduction;
    }

    public float getCachedCurrentHealthReduction() {
        return cachedCurrentHealthReduction;
    }

    public float getCachedMovementMultiplier() {
        return cachedMovementMultiplier;
    }

    public boolean hasCachedFracture() {
        return cachedFracture;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Type", type.ordinal());
        tag.putFloat("MaxHealth", maxHealth);
        tag.putFloat("MinorDamage", minorDamage);
        tag.putFloat("LocalNumb", localNumbing);
        tag.putBoolean("Tourniquet", tourniquet);
        ListTag list = new ListTag();
        for (Trauma t : traumas) {
            list.add(t.save());
        }
        tag.put("Traumas", list);
        return tag;
    }

    public void load(CompoundTag tag, TraumaTypes.Lookup registry) {
        this.maxHealth = tag.getFloat("MaxHealth");
        this.minorDamage = tag.getFloat("MinorDamage");
        this.localNumbing = Math.max(0.0F, Math.min(tag.getFloat("LocalNumb"), 1.0F));
        this.tourniquet = tag.getBoolean("Tourniquet");
        traumas.clear();
        ListTag list = tag.getList("Traumas", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Trauma t = Trauma.load(list.getCompound(i), registry);
            if (t != null) {
                traumas.add(t);
            }
        }
        this.dirty = true;
    }
}
