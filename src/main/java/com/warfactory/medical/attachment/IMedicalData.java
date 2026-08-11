package com.warfactory.medical.attachment;

import com.warfactory.medical.core.MedicalProfile;
import net.minecraft.nbt.CompoundTag;

public interface IMedicalData {

    MedicalProfile getProfile();

    void setProfile(MedicalProfile profile);

    boolean isDirty();

    int getRevision();

    int getLastSyncedRevision();

    void bumpRevision();

    void markSynced();

    boolean needsSync();

    CompoundTag save();

    void load(CompoundTag tag);
}
