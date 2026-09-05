package com.warfactory.medical.core.limb;

import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimbTypeTest {

    @Test
    void theSixLimbsArePartitionedIntoVitalArmAndLeg() {
        for (LimbType lt : LimbType.VALUES) {
            int roles = (lt.isArm() ? 1 : 0) + (lt.isLeg() ? 1 : 0) + (lt.isVital() ? 1 : 0);
            assertEquals(1, roles, lt + " must be exactly one of vital/arm/leg");
        }
        assertTrue(LimbType.HEAD.isVital());
        assertTrue(LimbType.TORSO.isVital());
        assertTrue(LimbType.LEFT_ARM.isArm());
        assertTrue(LimbType.RIGHT_LEG.isLeg());
    }

    @Test
    void theValuesArrayIsTheEnumInOrder() {
        // VALUES is cached and used as the iteration order everywhere, including the delta packet's limb
        // mask, where a mismatch with ordinal() would send the wrong limb's data.
        assertEquals(LimbType.values().length, LimbType.VALUES.length);
        for (int i = 0; i < LimbType.VALUES.length; i++) {
            assertSame(LimbType.values()[i], LimbType.VALUES[i]);
            assertEquals(i, LimbType.VALUES[i].ordinal());
        }
    }

    @Test
    void theSixLimbMaskFitsInTheDeltaPacketsBitfield() {
        // MedicalDeltaPacket shifts LIMB_BASE (1 << 2) left by the limb ordinal; more than 29 limbs would
        // overflow the int mask. Six is comfortable, but the relationship should be stated.
        assertTrue(LimbType.VALUES.length <= 29, "the delta mask is an int with two flags reserved");
    }

    @Test
    void anOutOfRangeOrdinalFallsBackToTheTorso() {
        // Fed by the NBT loader, where a save from a build with a different limb set must not throw.
        assertSame(LimbType.TORSO, LimbType.byOrdinal(-1));
        assertSame(LimbType.TORSO, LimbType.byOrdinal(99));
        assertSame(LimbType.HEAD, LimbType.byOrdinal(LimbType.HEAD.ordinal()));
    }

    @Test
    void everyLimbHasADisplayNameAndAPositiveHitWeight() {
        for (LimbType lt : LimbType.VALUES) {
            assertFalse(lt.getDisplayName().isBlank(), lt + " has no display name");
            assertTrue(lt.getHitWeight() > 0.0F, lt + " would never be picked by the weighted fallback");
        }
    }

    @Test
    void theTorsoIsTheLikeliestRandomHitAndTheHeadTheLeast() {
        for (LimbType lt : LimbType.VALUES) {
            if (lt != LimbType.TORSO) {
                assertTrue(LimbType.TORSO.getHitWeight() >= lt.getHitWeight(),
                        "the torso should dominate the weighted fallback, not " + lt);
            }
            assertTrue(LimbType.HEAD.getHitWeight() <= lt.getHitWeight(), lt + " is rarer than the head");
        }
    }

    @Test
    void limbStatusCallsALimbDamagedForAnyOfTheFourReasons() {
        assertFalse(LimbStatus.isDamaged(1.0F, 0.0F, 0.0F, false));
        assertTrue(LimbStatus.isDamaged(0.5F, 0.0F, 0.0F, false), "lost health");
        assertTrue(LimbStatus.isDamaged(1.0F, 0.1F, 0.0F, false), "bleeding");
        assertTrue(LimbStatus.isDamaged(1.0F, 0.0F, 0.1F, false), "in pain");
        assertTrue(LimbStatus.isDamaged(1.0F, 0.0F, 0.0F, true), "fractured");
    }

    @Test
    void aFloatingPointFullLimbStillReadsAsHealthy() {
        // Health percent is a division, so an intact limb frequently lands at 0.99999994 rather than 1.
        assertFalse(LimbStatus.isDamaged(0.9999F, 0.0F, 0.0F, false),
                "an epsilon below full must not light up the whole HUD");
    }

    @Test
    void damagedListsExactlyTheInjuredLimbsInOrder() {
        LimbSummary healthy = new LimbSummary(LimbType.HEAD, 1.0F, 0.0F, 0.0F, false, List.of());
        LimbSummary hurt = new LimbSummary(LimbType.LEFT_LEG, 0.3F, 0.0F, 0.0F, false, List.of());
        LimbSummary bleeding = new LimbSummary(LimbType.TORSO, 1.0F, 0.4F, 0.0F, false, List.of());

        assertEquals(List.of(LimbType.LEFT_LEG, LimbType.TORSO),
                LimbStatus.damaged(new LimbSummary[]{healthy, hurt, bleeding}));
    }

    @Test
    void nullSummariesAreToleratedBecauseADeltaLeavesGaps() {
        assertFalse(LimbStatus.isDamaged(null));
        assertTrue(LimbStatus.damaged(null).isEmpty());
        assertTrue(LimbStatus.damaged(new LimbSummary[]{null, null}).isEmpty());
    }
}
