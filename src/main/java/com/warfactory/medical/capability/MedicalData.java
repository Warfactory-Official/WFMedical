package com.warfactory.medical.capability;

import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import net.minecraft.nbt.CompoundTag;

/**
 * Default {@link IMedicalData} implementation: wraps a single {@link MedicalProfile} and tracks the
 * revision counters used for delta sync.
 */
public final class MedicalData implements IMedicalData {

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
        // Force a re-sync to any watching client after a load.
        this.lastSyncedRevision = -1;
    }
}
