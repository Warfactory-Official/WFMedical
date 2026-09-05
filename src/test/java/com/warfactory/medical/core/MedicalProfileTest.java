package com.warfactory.medical.core;

import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.support.Fixtures;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalProfileTest {

    private static final double EPS = 1.0e-6D;

    private TraumaRegistry registry;
    private MedicalProfile profile;

    @BeforeEach
    void setUp() {
        registry = Fixtures.registry();
        profile = Fixtures.profile();
    }

    @Nested
    class Construction {

        @Test
        void aNewProfileStartsFullAndHasEveryLimb() {
            assertEquals(profile.getMaxBloodMl(), profile.getBloodMl(), EPS);
            for (LimbType lt : LimbType.VALUES) {
                assertNotNull(profile.limb(lt), "missing limb " + lt);
                assertEquals(lt, profile.limb(lt).getType());
            }
            assertEquals(HealthState.HEALTHY, profile.getState());
            assertTrue(profile.isDirty(), "a fresh profile must compute once before it is trusted");
        }

        @Test
        void theDefaultConstructorUsesTheDefaultBloodVolume() {
            assertEquals(PhysiologyParams.defaults().maxBloodMl(), new MedicalProfile().getMaxBloodMl(), EPS);
        }
    }

    @Nested
    class Clamping {

        @Test
        void bloodIsClampedToZeroAndTheMaximum() {
            profile.setBloodMl(-100.0D);
            assertEquals(0.0D, profile.getBloodMl(), EPS);
            profile.setBloodMl(Double.MAX_VALUE);
            assertEquals(profile.getMaxBloodMl(), profile.getBloodMl(), EPS);
        }

        @Test
        void loweringTheMaximumAlsoTrimsTheCurrentVolume() {
            profile.setMaxBloodMl(1000.0D);
            assertEquals(1000.0D, profile.getBloodMl(), EPS);
        }

        @Test
        void theUnitRangeFieldsAreClamped() {
            profile.setPainSuppression(5.0F);
            assertEquals(1.0F, profile.getPainSuppression());
            profile.setPainSuppression(-1.0F);
            assertEquals(0.0F, profile.getPainSuppression());

            profile.setClottingBoost(5.0F);
            assertEquals(1.0F, profile.getClottingBoost());

            profile.setStimulant(5.0F);
            assertEquals(1.0F, profile.getStimulant());

            profile.setDeathProgress(5.0F);
            assertEquals(1.0F, profile.getDeathProgress());
            profile.setDeathProgress(-1.0F);
            assertEquals(0.0F, profile.getDeathProgress());
        }

        @Test
        void drugLoadHasNoUpperBoundBecauseOverdoseIsMeasuredAboveOne() {
            profile.setDrugLoad(4.0F);
            assertEquals(4.0F, profile.getDrugLoad());
            profile.setDrugLoad(-1.0F);
            assertEquals(0.0F, profile.getDrugLoad());
        }

        @Test
        void aNoOpWriteDoesNotDirtyTheProfile() {
            profile.recompute(Fixtures.params());
            assertFalse(profile.isDirty());
            profile.setBloodMl(profile.getBloodMl());
            profile.setPainSuppression(0.0F);
            profile.setStimulant(0.0F);
            assertFalse(profile.isDirty(), "an unchanged write must not force a resync");

            profile.setBloodMl(profile.getBloodMl() - 1.0D);
            assertTrue(profile.isDirty());
        }
    }

    @Nested
    class DownedState {

        @Test
        void anyOfTheThreeUnconsciousSourcesCountsAsDowned() {
            assertFalse(profile.isDowned());
            profile.setOverdoseUnconscious(true);
            assertTrue(profile.isDowned());
            profile.setOverdoseUnconscious(false);

            profile.setAsphyxiaUnconscious(true);
            assertTrue(profile.isDowned());
            profile.setAsphyxiaUnconscious(false);

            profile.setState(HealthState.UNCONSCIOUS);
            assertTrue(profile.isDowned());
        }

        @Test
        void enteringDeadClearsEverySurvivalTimer() {
            profile.setOverdoseUnconscious(true);
            profile.setOverdoseUntilTick(500L);
            profile.setBleedoutSinceTick(100L);
            profile.startAsphyxia(10L);
            profile.setBlackoutGraceUntil(900L);
            profile.setUnconsciousLatched(true);

            profile.enterDeadState(true);

            assertEquals(HealthState.DEAD, profile.getState());
            assertEquals(HealthState.DEAD, profile.getForcedState());
            assertFalse(profile.isOverdoseUnconscious());
            assertEquals(0L, profile.getOverdoseUntilTick());
            assertEquals(-1L, profile.getBleedoutSinceTick());
            assertFalse(profile.isAsphyxiating());
            assertFalse(profile.isAsphyxiaUnconscious());
            assertEquals(0L, profile.getBlackoutGraceUntil());
            assertFalse(profile.isUnconsciousLatched());
            assertTrue(profile.isDirty());
        }

        @Test
        void dyingWithoutPinningLeavesTheStateFreeToRecomputeOnRespawn() {
            profile.enterDeadState(false);
            assertEquals(HealthState.DEAD, profile.getState());
            assertNull(profile.getForcedState());
        }
    }

    @Nested
    class Asphyxia {

        @Test
        void startingAsphyxiaRecordsWhenItBegan() {
            profile.startAsphyxia(250L);
            assertTrue(profile.isAsphyxiating());
            assertEquals(250L, profile.getAsphyxiaSince());
        }

        @Test
        void restartingDoesNotResetTheClock() {
            profile.startAsphyxia(250L);
            profile.startAsphyxia(900L);
            assertEquals(250L, profile.getAsphyxiaSince(),
                    "a second trigger must not extend the time to unconsciousness");
        }

        @Test
        void anAlreadyUnconsciousBodyDoesNotRestartTheStruggle() {
            profile.setAsphyxiaUnconscious(true);
            profile.startAsphyxia(250L);
            assertFalse(profile.isAsphyxiating());
        }

        @Test
        void clearingResetsEveryAsphyxiaField() {
            profile.startAsphyxia(250L);
            profile.setAsphyxiaUnconscious(true);
            profile.setAsphyxiaDeadlineTick(800L);
            profile.clearAsphyxia();
            assertFalse(profile.isAsphyxiating());
            assertFalse(profile.isAsphyxiaUnconscious());
            assertEquals(0L, profile.getAsphyxiaSince());
            assertEquals(0L, profile.getAsphyxiaDeadlineTick());
        }
    }

    @Nested
    class Traumas {

        @Test
        void allTraumasWalksEveryLimbInOrder() {
            Fixtures.wound(profile, registry, LimbType.HEAD, "bruise", 0.2F);
            Fixtures.wound(profile, registry, LimbType.RIGHT_LEG, "fracture", 1.0F);
            Fixtures.wound(profile, registry, LimbType.RIGHT_LEG, "puncture", 0.5F);
            assertEquals(3, profile.allTraumas().size());
            assertEquals(LimbType.HEAD, profile.allTraumas().get(0).getLimb());
        }

        @Test
        void anyLocalNumbingScansEveryLimb() {
            assertFalse(profile.anyLocalNumbing());
            profile.limb(LimbType.RIGHT_LEG).setLocalNumbing(0.1F);
            assertTrue(profile.anyLocalNumbing());
        }

        @Test
        void addingATraumaDirtiesTheProfile() {
            profile.recompute(Fixtures.params());
            assertFalse(profile.isDirty());
            Fixtures.wound(profile, registry, LimbType.TORSO, "bruise", 0.2F);
            assertTrue(profile.isDirty());
        }
    }

    @Nested
    class ActiveTreatment {

        @Test
        void aTreatmentIsSetAndClearedAsAUnit() {
            assertFalse(profile.hasActiveTreatment());
            profile.setActiveTreatment(TreatmentAction.SUTURE_WOUND, LimbType.LEFT_ARM,
                    "wfmedical:suture_kit", 60, 1234L, 7, 3);

            assertTrue(profile.hasActiveTreatment());
            assertEquals(TreatmentAction.SUTURE_WOUND, profile.getActiveAction());
            assertEquals(LimbType.LEFT_ARM, profile.getActiveLimb());
            assertEquals("wfmedical:suture_kit", profile.getActiveItemId());
            assertEquals(60, profile.getActiveTotalTicks());
            assertEquals(1234L, profile.getActiveStartGameTime());
            assertEquals(7, profile.getActiveTargetId());
            assertEquals(3, profile.getActiveSlot());

            profile.clearActiveTreatment();
            assertFalse(profile.hasActiveTreatment());
            assertNull(profile.getActiveAction());
            assertNull(profile.getActiveLimb());
            assertEquals("", profile.getActiveItemId());
            assertEquals(-1, profile.getActiveTargetId());
            assertEquals(-1, profile.getActiveSlot());
        }

        @Test
        void aNullItemIdBecomesEmptyNotNull() {
            profile.setActiveTreatment(TreatmentAction.HEAL_TRAUMA, null, null, 20, 0L, -1, -1);
            assertEquals("", profile.getActiveItemId(), "the id goes straight onto the wire; null would NPE");
        }
    }

    @Nested
    class Attribution {

        @Test
        void theLastDamagingPlayerIsRecordedWithItsTick() {
            assertNull(profile.getLastDamagingPlayer());
            assertEquals(Long.MIN_VALUE, profile.getLastDamageTick());

            UUID killer = UUID.randomUUID();
            profile.setLastDamagingPlayer(killer, 900L);
            assertEquals(killer, profile.getLastDamagingPlayer());
            assertEquals(900L, profile.getLastDamageTick());

            profile.clearLastDamagingPlayer();
            assertNull(profile.getLastDamagingPlayer());
            assertEquals(Long.MIN_VALUE, profile.getLastDamageTick(),
                    "the sentinel must be far in the past so a stale credit window never reopens");
        }
    }

    @Nested
    class Persistence {

        @Test
        void aProfileSurvivesASaveLoadRoundTrip() {
            profile.setBloodMl(3210.0D);
            profile.setPainSuppression(0.4F);
            profile.setDrugLoad(1.6F);
            profile.setClottingBoost(0.7F);
            profile.setClottingBoostEndTick(4444L);
            profile.setStimulant(0.9F);
            profile.setStimulantEndTick(5555L);
            profile.setState(HealthState.CRITICAL);
            profile.setBleedoutSinceTick(88L);
            UUID killer = UUID.randomUUID();
            profile.setLastDamagingPlayer(killer, 1000L);
            Fixtures.wound(profile, registry, LimbType.LEFT_LEG, "fracture", 1.0F);
            profile.limb(LimbType.RIGHT_ARM).setTourniquet(true);

            MedicalProfile loaded = new MedicalProfile();
            loaded.load(profile.save(), registry);

            assertEquals(3210.0D, loaded.getBloodMl(), EPS);
            assertEquals(profile.getMaxBloodMl(), loaded.getMaxBloodMl(), EPS);
            assertEquals(0.4F, loaded.getPainSuppression());
            assertEquals(1.6F, loaded.getDrugLoad());
            assertEquals(0.7F, loaded.getClottingBoost());
            assertEquals(4444L, loaded.getClottingBoostEndTick());
            assertEquals(0.9F, loaded.getStimulant());
            assertEquals(5555L, loaded.getStimulantEndTick());
            assertEquals(HealthState.CRITICAL, loaded.getState());
            assertEquals(88L, loaded.getBleedoutSinceTick());
            assertEquals(killer, loaded.getLastDamagingPlayer());
            assertEquals(1000L, loaded.getLastDamageTick());
            assertEquals(1, loaded.limb(LimbType.LEFT_LEG).getTraumas().size());
            assertTrue(loaded.limb(LimbType.RIGHT_ARM).hasTourniquet());
        }

        @Test
        void transientStateIsDeliberatelyNotPersisted() {
            // Drug blackouts, asphyxia timers and the adrenaline latch are all tick-relative; carrying them
            // across a restart would resume a countdown against a game time that has moved.
            profile.setOverdoseUnconscious(true);
            profile.setOverdoseUntilTick(999L);
            profile.startAsphyxia(5L);
            profile.setAdrenalineExhausted(true);
            profile.setUnconsciousLatched(true);
            profile.setDeathProgress(0.9F);

            MedicalProfile loaded = new MedicalProfile();
            loaded.load(profile.save(), registry);

            assertFalse(loaded.isOverdoseUnconscious());
            assertEquals(0L, loaded.getOverdoseUntilTick());
            assertFalse(loaded.isAsphyxiating());
            assertFalse(loaded.isAdrenalineExhausted());
            assertFalse(loaded.isUnconsciousLatched());
            assertEquals(0.0F, loaded.getDeathProgress());
        }

        @Test
        void aLegacyKnockdownTimestampIsStillRead() {
            CompoundTag tag = profile.save();
            tag.remove("BleedoutSince");
            tag.putLong("KnockdownSince", 4242L);
            MedicalProfile loaded = new MedicalProfile();
            loaded.load(tag, registry);
            assertEquals(4242L, loaded.getBleedoutSinceTick(),
                    "pre-rename saves used KnockdownSince; dropping it would reset every downed timer");
        }

        @Test
        void aLegacyKnockedDownStateMapsToUnconscious() {
            CompoundTag tag = profile.save();
            tag.putString("State", "KNOCKED_DOWN");
            MedicalProfile loaded = new MedicalProfile();
            loaded.load(tag, registry);
            assertEquals(HealthState.UNCONSCIOUS, loaded.getState());
        }

        @Test
        void aTamperedBloodValueIsReclampedOnLoad() {
            CompoundTag tag = profile.save();
            tag.putDouble("BloodMl", 99999.0D);
            MedicalProfile loaded = new MedicalProfile();
            loaded.load(tag, registry);
            assertEquals(loaded.getMaxBloodMl(), loaded.getBloodMl(), EPS);

            tag.putDouble("BloodMl", -50.0D);
            loaded.load(tag, registry);
            assertEquals(0.0D, loaded.getBloodMl(), EPS);
        }

        @Test
        void aLoadedProfileIsDirtySoItRecomputesBeforeItIsRead() {
            MedicalProfile loaded = new MedicalProfile();
            loaded.load(profile.save(), registry);
            assertTrue(loaded.isDirty());
        }

        @Test
        void noLastDamagingPlayerLoadsAsNoCredit() {
            CompoundTag tag = profile.save();
            assertFalse(tag.hasUUID("LastDamagingPlayer"));
            MedicalProfile loaded = new MedicalProfile();
            loaded.setLastDamagingPlayer(UUID.randomUUID(), 5L);
            loaded.load(tag, registry);
            assertNull(loaded.getLastDamagingPlayer(), "loading must clear a stale credit, not keep it");
        }
    }

    @Nested
    class HealthStateEnum {

        @Test
        void theLegacyNameMapsForwardAndUnknownNamesUseTheFallback() {
            assertEquals(HealthState.UNCONSCIOUS, HealthState.byName("KNOCKED_DOWN", HealthState.HEALTHY));
            assertEquals(HealthState.CRITICAL, HealthState.byName("CRITICAL", HealthState.HEALTHY));
            assertEquals(HealthState.DEAD, HealthState.byName("nonsense", HealthState.DEAD));
            assertEquals(HealthState.HEALTHY, HealthState.byName(null, HealthState.HEALTHY));
        }

        @Test
        void theOrdinalsAreASeverityLadderBecauseTheCodeComparesThem() {
            // Physiology.compute escalates with `forced.ordinal() > state.ordinal()`; a reorder here would
            // silently invert every "only ever escalate" guard.
            assertTrue(HealthState.HEALTHY.ordinal() < HealthState.CRITICAL.ordinal());
            assertTrue(HealthState.CRITICAL.ordinal() < HealthState.UNCONSCIOUS.ordinal());
            assertTrue(HealthState.UNCONSCIOUS.ordinal() < HealthState.DEAD.ordinal());
        }
    }
}
