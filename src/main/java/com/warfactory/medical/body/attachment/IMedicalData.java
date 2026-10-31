package com.warfactory.medical.body.attachment;

import com.warfactory.medical.body.MedicalProfile;
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
