package com.warfactory.medical.capability;

import com.warfactory.medical.core.MedicalProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

/**
 * Per-player medical capability contract: owns the {@link MedicalProfile} plus the revision bookkeeping
 * used to drive delta-based network sync.
 *
 * <p>Two independent "dirty" concepts are tracked:</p>
 * <ul>
 *     <li>{@link #isDirty()} — the underlying {@link MedicalProfile} has un-recomputed physiology
 *     (trauma/blood changed); consumed by the scheduled server physiology pass.</li>
 *     <li>{@link #needsSync()} — the authoritative state has advanced ({@link #getRevision()} moved
 *     past {@link #getLastSyncedRevision()}) and the client copy is stale; consumed by networking.</li>
 * </ul>
 */
@AutoRegisterCapability
public interface IMedicalData {

    MedicalProfile getProfile();

    void setProfile(MedicalProfile profile);

    /**
     * @return true when the profile's physiology needs to be recomputed.
     */
    boolean isDirty();

    /**
     * Monotonic counter bumped whenever the authoritative state changes and clients must be told.
     */
    int getRevision();

    int getLastSyncedRevision();

    /**
     * Advance the revision counter, flagging the state as needing a network delta.
     */
    void bumpRevision();

    /**
     * Record that the current {@link #getRevision()} has been transmitted to the client.
     */
    void markSynced();

    /**
     * @return true when {@link #getRevision()} is ahead of {@link #getLastSyncedRevision()}.
     */
    boolean needsSync();

    CompoundTag save();

    void load(CompoundTag tag);
}
