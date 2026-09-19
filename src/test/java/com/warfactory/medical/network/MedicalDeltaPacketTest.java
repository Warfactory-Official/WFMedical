package com.warfactory.medical.network;

import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import com.warfactory.medical.network.MedicalSyncPacket.WoundView;
import com.warfactory.medical.support.TestConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The delta protocol: the server keeps the last snapshot it sent a player and ships only what changed.
 *
 * <p>This is the one place a bug is invisible in play-testing and permanent afterwards -- a limb that stops
 * being included in the mask silently freezes on the victim's HUD, and there is no resync to correct it
 * until the periodic full snapshot. The property that matters is the composition one:
 * {@code diff(prev, cur).applyTo(prev)} must reproduce {@code cur} exactly, for every pair.
 */
class MedicalDeltaPacketTest {

    @BeforeAll
    static void loadConfig() {
        // ClientMedicalCache consults logMedicalSync on every apply.
        TestConfig.load();
    }

    @AfterEach
    void clearCache() {
        ClientMedicalCache.clear();
    }

    private static DerivedStats stats(float maxHealth, HealthState state) {
        return new DerivedStats(maxHealth, 30.0F - maxHealth, maxHealth, 0.0D, 0.0F, 0.0F, 1.0F, false, 1.0F,
                state, false, false, false, false, false, false, false, 1.0D, 80.0F);
    }

    private static LimbSummary limb(LimbType lt, float health) {
        return new LimbSummary(lt, health, 0.0F, 0.0F, false, List.of());
    }

    private static LimbSummary[] allHealthy() {
        LimbSummary[] out = new LimbSummary[LimbType.VALUES.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = limb(LimbType.VALUES[i], 1.0F);
        }
        return out;
    }

    private static MedicalSyncPacket snapshot(LimbSummary[] limbs, double blood, HealthState state) {
        return new MedicalSyncPacket(stats(30.0F, state), limbs, blood, 5000.0D, 0.0F, 0.0F, state, 0.0F);
    }

    private static MedicalSyncPacket healthy() {
        return snapshot(allHealthy(), 5000.0D, HealthState.HEALTHY);
    }

    private static void assertSyncEquals(MedicalSyncPacket expected, MedicalSyncPacket actual) {
        assertEquals(expected.stats(), actual.stats());
        assertEquals(expected.bloodMl(), actual.bloodMl());
        assertEquals(expected.maxBloodMl(), actual.maxBloodMl());
        assertEquals(expected.painSuppression(), actual.painSuppression());
        assertEquals(expected.drugLoad(), actual.drugLoad());
        assertEquals(expected.state(), actual.state());
        assertEquals(expected.deathProgress(), actual.deathProgress());
        assertArrayEquals(expected.limbs(), actual.limbs());
    }

    private static MedicalDeltaPacket overTheWire(MedicalDeltaPacket in) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        in.encode(buf);
        int written = buf.writerIndex();
        MedicalDeltaPacket out = MedicalDeltaPacket.decode(buf);
        assertEquals(written, buf.readerIndex(), "delta encode/decode length mismatch");
        return out;
    }

    @Nested
    class Diffing {

        @Test
        void anUnchangedSnapshotProducesAnEmptyDelta() {
            MedicalSyncPacket s = healthy();
            MedicalDeltaPacket d = MedicalDeltaPacket.diff(s, s);
            assertTrue(d.isEmpty(), "mask was " + Integer.toBinaryString(d.mask()));
            assertEquals(0, d.mask());
        }

        @Test
        void onlyTheLimbThatChangedIsIncluded() {
            MedicalSyncPacket prev = healthy();
            LimbSummary[] next = allHealthy();
            next[LimbType.LEFT_LEG.ordinal()] = limb(LimbType.LEFT_LEG, 0.4F);
            MedicalDeltaPacket d = MedicalDeltaPacket.diff(prev, snapshot(next, 5000.0D, HealthState.HEALTHY));

            for (LimbType lt : LimbType.VALUES) {
                if (lt == LimbType.LEFT_LEG) {
                    assertTrue(d.limbs()[lt.ordinal()] != null, "the changed limb must be sent");
                } else {
                    assertNull(d.limbs()[lt.ordinal()], lt + " was resent although it did not change");
                }
            }
        }

        @Test
        void aWoundListChangeCountsAsALimbChange() {
            MedicalSyncPacket prev = healthy();
            LimbSummary[] next = allHealthy();
            next[LimbType.TORSO.ordinal()] = new LimbSummary(LimbType.TORSO, 1.0F, 0.0F, 0.0F, false,
                    List.of(new WoundView("puncture", 50, WoundView.FLAG_BLEEDING)));
            MedicalDeltaPacket d = MedicalDeltaPacket.diff(prev, snapshot(next, 5000.0D, HealthState.HEALTHY));
            assertFalse(d.isEmpty(), "a new wound with unchanged health/bleed/pain must still be sent");
            assertTrue(d.limbs()[LimbType.TORSO.ordinal()] != null);
        }

        @Test
        void scalarsTravelAsOneGroup() {
            MedicalSyncPacket prev = healthy();
            MedicalSyncPacket cur = snapshot(allHealthy(), 4000.0D, HealthState.HEALTHY);
            MedicalDeltaPacket d = MedicalDeltaPacket.diff(prev, cur);
            assertFalse(d.isEmpty());
            assertEquals(4000.0D, d.applyTo(prev).bloodMl());
        }

        @Test
        void aStatsChangeAloneIsCarried() {
            MedicalSyncPacket prev = healthy();
            MedicalSyncPacket cur = new MedicalSyncPacket(stats(22.0F, HealthState.HEALTHY), allHealthy(),
                    5000.0D, 5000.0D, 0.0F, 0.0F, HealthState.HEALTHY, 0.0F);
            MedicalDeltaPacket d = MedicalDeltaPacket.diff(prev, cur);
            assertFalse(d.isEmpty());
            assertEquals(22.0F, d.applyTo(prev).stats().effectiveMaxHealth());
        }
    }

    @Nested
    class Composition {

        @Test
        void applyingADeltaToItsBaseReproducesTheTarget() {
            MedicalSyncPacket prev = healthy();
            LimbSummary[] next = allHealthy();
            next[LimbType.HEAD.ordinal()] = new LimbSummary(LimbType.HEAD, 0.2F, 1.5F, 0.8F, true,
                    List.of(new WoundView("laceration_large", 80, WoundView.FLAG_BLEEDING)));
            next[LimbType.RIGHT_ARM.ordinal()] = limb(LimbType.RIGHT_ARM, 0.6F);
            MedicalSyncPacket cur = new MedicalSyncPacket(stats(18.0F, HealthState.CRITICAL), next,
                    2400.0D, 5000.0D, 0.4F, 1.1F, HealthState.CRITICAL, 0.33F);

            assertSyncEquals(cur, MedicalDeltaPacket.diff(prev, cur).applyTo(prev));
        }

        @Test
        void thePropertyHoldsAcrossRandomSnapshotPairs() {
            // The composition law is what keeps a client in sync over a long fight; spot-checking one
            // transition would not catch a mask bit that is off by one only for the sixth limb.
            Random rng = new Random(20260905L);
            for (int iter = 0; iter < 500; iter++) {
                MedicalSyncPacket prev = randomSnapshot(rng);
                MedicalSyncPacket cur = randomSnapshot(rng);
                MedicalDeltaPacket d = MedicalDeltaPacket.diff(prev, cur);
                assertSyncEquals(cur, d.applyTo(prev));
                // And it must survive the wire, not just the in-process object.
                assertSyncEquals(cur, overTheWire(d).applyTo(prev));
            }
        }

        @Test
        void anEmptyDeltaLeavesTheBaseUntouched() {
            MedicalSyncPacket prev = healthy();
            MedicalSyncPacket after = MedicalDeltaPacket.diff(prev, prev).applyTo(prev);
            assertSyncEquals(prev, after);
        }

        private MedicalSyncPacket randomSnapshot(Random rng) {
            LimbSummary[] limbs = new LimbSummary[LimbType.VALUES.length];
            for (int i = 0; i < limbs.length; i++) {
                List<WoundView> wounds = new ArrayList<>();
                int n = rng.nextInt(3);
                for (int w = 0; w < n; w++) {
                    wounds.add(new WoundView(rng.nextBoolean() ? "puncture" : "bruise",
                            rng.nextInt(101), rng.nextInt(64)));
                }
                limbs[i] = new LimbSummary(LimbType.VALUES[i],
                        Math.round(rng.nextFloat() * 100.0F) / 100.0F,
                        Math.round(rng.nextFloat() * 100.0F) / 100.0F,
                        Math.round(rng.nextFloat() * 100.0F) / 100.0F,
                        rng.nextBoolean(), List.copyOf(wounds));
            }
            HealthState state = HealthState.values()[rng.nextInt(HealthState.values().length)];
            return new MedicalSyncPacket(stats(rng.nextInt(31), state), limbs,
                    rng.nextInt(5001), 5000.0D,
                    Math.round(rng.nextFloat() * 100.0F) / 100.0F,
                    Math.round(rng.nextFloat() * 100.0F) / 100.0F,
                    state, Math.round(rng.nextFloat() * 100.0F) / 100.0F);
        }
    }

    @Nested
    class ClientApplication {

        @Test
        void aDeltaWithNoBaselineIsDroppedRatherThanGuessed() {
            ClientMedicalCache.clear();
            assertNull(ClientMedicalCache.get());
            MedicalSyncPacket prev = healthy();
            ClientMedicalCache.applyDelta(MedicalDeltaPacket.diff(prev, snapshot(allHealthy(), 100.0D,
                    HealthState.CRITICAL)));
            assertNull(ClientMedicalCache.get(),
                    "applying a delta with no baseline would invent a snapshot out of half a diff");
        }

        @Test
        void aFullSnapshotThenDeltasTracksTheServer() {
            MedicalSyncPacket a = healthy();
            ClientMedicalCache.set(a);
            assertSame(a, ClientMedicalCache.get());

            MedicalSyncPacket b = snapshot(allHealthy(), 3000.0D, HealthState.CRITICAL);
            ClientMedicalCache.applyDelta(MedicalDeltaPacket.diff(a, b));
            assertSyncEquals(b, ClientMedicalCache.get());

            LimbSummary[] hurt = allHealthy();
            hurt[LimbType.LEFT_ARM.ordinal()] = limb(LimbType.LEFT_ARM, 0.1F);
            MedicalSyncPacket c = snapshot(hurt, 2000.0D, HealthState.CRITICAL);
            ClientMedicalCache.applyDelta(MedicalDeltaPacket.diff(b, c));
            assertSyncEquals(c, ClientMedicalCache.get());
        }

        @Test
        void theAccessorsFallBackToHealthyWithNoSnapshot() {
            ClientMedicalCache.clear();
            assertEquals(DerivedStats.healthy(), ClientMedicalCache.stats());
            assertEquals(HealthState.HEALTHY, ClientMedicalCache.state());
            assertEquals(0.0F, ClientMedicalCache.painSuppression());
            assertEquals(0.0F, ClientMedicalCache.drugLoad());
            assertEquals(0.0F, ClientMedicalCache.deathProgress());
            assertFalse(ClientMedicalCache.hasActiveTreatment());
        }

        @Test
        void debugAndSelectionStateIsClearedWithTheSnapshot() {
            ClientMedicalCache.setSelectedLimb(LimbType.HEAD);
            assertTrue(ClientMedicalCache.toggleDebug());
            ClientMedicalCache.clear();
            assertNull(ClientMedicalCache.selectedLimb());
            assertFalse(ClientMedicalCache.isDebug());
        }
    }
}
