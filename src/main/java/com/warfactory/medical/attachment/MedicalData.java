package com.warfactory.medical.attachment;

import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class MedicalData implements IMedicalData, INBTSerializable<CompoundTag> {

    private MedicalProfile profile = new MedicalProfile();
    private int revision;
    private int lastSyncedRevision = -1;

    @Override
    public MedicalProfile getProfile() {
        return profile;
    }

    @Override
    public void setProfile(MedicalProfile profile) {
        this.profile = profile != null ? profile : new MedicalProfile();
        bumpRevision();
    }

    @Override
    public boolean isDirty() {
        return profile.isDirty();
    }

    @Override
    public int getRevision() {
        return revision;
    }

    @Override
    public int getLastSyncedRevision() {
        return lastSyncedRevision;
    }

    @Override
    public void bumpRevision() {
        revision++;
    }

    @Override
    public void markSynced() {
        lastSyncedRevision = revision;
    }

    @Override
    public boolean needsSync() {
        return revision != lastSyncedRevision;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Profile", profile.save());
        tag.putInt("Revision", revision);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        MedicalProfile loaded = new MedicalProfile();
        if (tag.contains("Profile")) {
            loaded.load(tag.getCompound("Profile"), TraumaRegistry.active());
        }
        this.profile = loaded;
        this.revision = tag.getInt("Revision");
        this.lastSyncedRevision = -1;
    }

    // --- attachment persistence -------------------------------------------------------------
    // The profile NBT holds only primitives and trauma ids, so the registry lookup provider the
    // attachment API hands us is not needed; save()/load() stay the plain-CompoundTag pair the
    // rest of the mod (sync packets, /wfmedical commands) already uses.

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return save();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        load(tag);
    }
}
