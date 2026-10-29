package com.warfactory.medical.server;

import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.PhysiologyParams;
import com.warfactory.medical.core.Stress;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.support.Fixtures;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalEngineStressTest {

    private static final Stress.Params P = new Stress.Params(true, 0.15F, 0.03F, 0.004F, 0.05F, 100, 4, 0.015F, 3.0F, 0.35F, 1.0F);
    private static final int LIT = 15, DARK = 0, INTERVAL = 10;

    private TraumaRegistry registry;
    private MedicalProfile profile;

    @BeforeEach
    void setUp() {
        registry = Fixtures.registry();
        profile = Fixtures.profile();
    }

    @Test
    void calmAndLitRecovers() {
        profile.setStress(0.8F);
        MedicalEngine.stepStress(profile, LIT, INTERVAL, 1000L, P);
        assertTrue(profile.getStress() < 0.8F);
    }

    @Test
    void darknessHoldsStressWhereLightLowersIt() {
        profile.setStress(0.8F);
        MedicalEngine.stepStress(profile, DARK, INTERVAL, 1000L, P);
        assertEquals(0.8F, profile.getStress());
        MedicalEngine.stepStress(profile, 4, INTERVAL, 1000L, P);
        assertEquals(0.8F, profile.getStress());
    }

    @Test
    void recoveryWaitsOutTheDelayAfterARise() {
        profile.addStress(0.5F, 1000L);
        MedicalEngine.stepStress(profile, LIT, INTERVAL, 1099L, P);
        assertEquals(0.5F, profile.getStress());
        MedicalEngine.stepStress(profile, LIT, INTERVAL, 1100L, P);
        assertTrue(profile.getStress() < 0.5F);
    }

    @Test
    void painRaisesStressAndRestartsTheDelay() {
        Fixtures.wound(profile, registry, LimbType.TORSO, "fracture", 1.0F);
        Fixtures.wound(profile, registry, LimbType.HEAD, "fracture", 1.0F);
        profile.recompute(PhysiologyParams.defaults());
        MedicalEngine.stepStress(profile, LIT, INTERVAL, 5000L, P);
        assertTrue(profile.getStress() > 0.0F);
        assertEquals(5000L, profile.getStressRoseTick());
    }

    @Test
    void disabledPinsStressAtZero() {
        profile.setStress(0.7F);
        MedicalEngine.stepStress(profile, LIT, INTERVAL, 1000L,
                new Stress.Params(false, 0.15F, 0.03F, 0.004F, 0.05F, 100, 4, 0.015F, 3.0F, 0.35F, 1.0F));
        assertEquals(0.0F, profile.getStress());
    }

    @Test
    void stressSurvivesTheNbtRoundTrip() {
        profile.setStress(0.62F);
        CompoundTag tag = profile.save();
        MedicalProfile loaded = Fixtures.profile();
        loaded.load(tag, registry);
        assertEquals(0.62F, loaded.getStress(), 1.0e-6F);
    }

    @Test
    void addStressIsClampedAndDirtiesTheProfile() {
        profile.recompute(PhysiologyParams.defaults());
        assertTrue(!profile.isDirty());
        profile.addStress(2.0F, 10L);
        assertEquals(1.0F, profile.getStress());
        assertTrue(profile.isDirty());
    }
}
