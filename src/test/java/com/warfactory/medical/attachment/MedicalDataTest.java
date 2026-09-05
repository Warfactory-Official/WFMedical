package com.warfactory.medical.attachment;

import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.support.Fixtures;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The data attachment's revision bookkeeping, which is what decides whether a player gets a sync packet.
 * A revision that fails to move means the client keeps showing stale wounds; one that moves every tick
 * means a full snapshot per tick per player.
 */
class MedicalDataTest {

    private MedicalData data;

    @BeforeEach
    void setUp() {
        // MedicalData.load resolves trauma ids against the process-wide active registry.
        TraumaRegistry.setActive(Fixtures.registry());
        data = new MedicalData();
    }

    @AfterEach
    void restoreActive() {
        TraumaRegistry.setActive(Fixtures.registry());
    }

    @Test
    void aFreshAttachmentHasAProfileAndNeedsItsFirstSync() {
        assertNotNull(data.getProfile());
        assertEquals(0, data.getRevision());
        assertEquals(-1, data.getLastSyncedRevision());
        assertTrue(data.needsSync(), "revision 0 has never been sent");
    }

    @Test
    void markingSyncedStopsFurtherSendsUntilSomethingChanges() {
        data.markSynced();
        assertFalse(data.needsSync());
        data.bumpRevision();
        assertTrue(data.needsSync());
        data.markSynced();
        assertFalse(data.needsSync());
    }

    @Test
    void replacingTheProfileBumpsTheRevision() {
        data.markSynced();
        assertFalse(data.needsSync());
        data.setProfile(new MedicalProfile());
        assertTrue(data.needsSync());
    }

    @Test
    void aNullProfileIsReplacedRatherThanStored() {
        data.setProfile(null);
        assertNotNull(data.getProfile(), "every read site dereferences this without a null check");
    }

    @Test
    void dirtyIsDelegatedToTheProfile() {
        assertTrue(data.isDirty());
        data.getProfile().recompute(Fixtures.params());
        assertFalse(data.isDirty());
        data.getProfile().markDirty();
        assertTrue(data.isDirty());
    }

    @Test
    void aRoundTripCarriesTheProfileAndTheRevision() {
        Fixtures.wound(data.getProfile(), TraumaRegistry.active(), LimbType.HEAD, "puncture", 0.8F);
        data.getProfile().setBloodMl(1234.0D);
        data.bumpRevision();
        data.bumpRevision();

        CompoundTag tag = data.save();
        MedicalData loaded = new MedicalData();
        loaded.load(tag);

        assertEquals(2, loaded.getRevision());
        assertEquals(1234.0D, loaded.getProfile().getBloodMl(), 1.0e-6D);
        assertEquals(1, loaded.getProfile().limb(LimbType.HEAD).getTraumas().size());
        assertEquals("puncture", loaded.getProfile().limb(LimbType.HEAD).getTraumas().get(0).getType().getId());
    }

    @Test
    void aLoadedAttachmentAlwaysNeedsResyncing() {
        data.bumpRevision();
        data.markSynced();
        CompoundTag tag = data.save();

        MedicalData loaded = new MedicalData();
        loaded.markSynced();
        loaded.load(tag);

        assertTrue(loaded.needsSync(),
                "after a relog the client has nothing, so the first tick must send a full snapshot");
        assertEquals(-1, loaded.getLastSyncedRevision());
    }

    @Test
    void loadingReplacesTheProfileObjectRatherThanMutatingTheOldOne() {
        MedicalProfile before = data.getProfile();
        data.load(data.save());
        assertNotSame(before, data.getProfile(),
                "callers hold the profile across a load; sharing it would resurrect cleared state");
    }

    @Test
    void loadingAnEmptyTagYieldsAFreshProfileRatherThanThrowing() {
        data.load(new CompoundTag());
        assertNotNull(data.getProfile());
        assertEquals(0, data.getRevision());
    }

    @Test
    void theNbtSerializableFacadeDelegatesToSaveAndLoad() {
        // The attachment API hands a HolderLookup.Provider; the profile NBT holds only primitives and
        // trauma ids, so it is deliberately unused and null must be accepted.
        data.getProfile().setBloodMl(999.0D);
        CompoundTag tag = data.serializeNBT(null);
        MedicalData loaded = new MedicalData();
        loaded.deserializeNBT(null, tag);
        assertEquals(999.0D, loaded.getProfile().getBloodMl(), 1.0e-6D);
    }

    @Test
    void theProfileIsHandedOutByReferenceSoMutationsAreVisible() {
        MedicalProfile p = data.getProfile();
        p.setBloodMl(42.0D);
        assertSame(p, data.getProfile());
        assertEquals(42.0D, data.getProfile().getBloodMl(), 1.0e-6D);
    }
}
