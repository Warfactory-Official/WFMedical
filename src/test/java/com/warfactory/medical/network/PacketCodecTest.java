package com.warfactory.medical.network;

import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.damage.rig.HumanoidRig;
import com.warfactory.medical.core.damage.rig.Obb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.network.MedicalSyncPacket.LimbSummary;
import com.warfactory.medical.network.MedicalSyncPacket.WoundView;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire round-trips for every payload the mod sends.
 *
 * <p>An encode/decode pair is the classic place for a silent desync: writing a {@code varInt} and reading
 * an {@code int} still "works" for small values, and adding a field to only one side shifts every later
 * field without any compile error. So each case asserts both that the value survives <em>and</em> that the
 * buffer is fully drained -- a length mismatch is the symptom that actually reaches production.
 */
class PacketCodecTest {

    private static <T> T roundTrip(T packet, Consumer<FriendlyByteBuf> encode,
                                   Function<FriendlyByteBuf, T> decode) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        encode.accept(buf);
        int written = buf.writerIndex();
        T out = decode.apply(buf);
        assertEquals(written, buf.readerIndex(),
                "encode wrote " + written + " bytes but decode consumed " + buf.readerIndex());
        assertEquals(0, buf.readableBytes(), "decode left bytes on the wire");
        return out;
    }

    private static DerivedStats stats() {
        return new DerivedStats(21.5F, 8.5F, 19.0F, 2.75D, 0.4F, 0.62F, 0.55F, true, 0.3F,
                HealthState.CRITICAL, true, false, true, false, true, false, true, 0.42D, 80.0F);
    }

    private static LimbSummary[] limbs() {
        LimbSummary[] out = new LimbSummary[LimbType.VALUES.length];
        for (int i = 0; i < out.length; i++) {
            LimbType lt = LimbType.VALUES[i];
            out[i] = new LimbSummary(lt, 1.0F - i * 0.1F, i * 0.5F, i * 0.25F, i % 2 == 0,
                    i == 0 ? List.of() : List.of(new WoundView("laceration_large", 10 * i, i)));
        }
        return out;
    }

    private static MedicalSyncPacket sync() {
        return new MedicalSyncPacket(stats(), limbs(), 3200.5D, 5000.0D, 0.35F, 1.25F,
                HealthState.CRITICAL, 0.42F);
    }

    private static void assertSyncEquals(MedicalSyncPacket a, MedicalSyncPacket b) {
        assertEquals(a.stats(), b.stats());
        assertEquals(a.bloodMl(), b.bloodMl());
        assertEquals(a.maxBloodMl(), b.maxBloodMl());
        assertEquals(a.painSuppression(), b.painSuppression());
        assertEquals(a.drugLoad(), b.drugLoad());
        assertEquals(a.state(), b.state());
        assertEquals(a.deathProgress(), b.deathProgress());
        assertArrayEquals(a.limbs(), b.limbs());
    }

    @Nested
    class Sync {

        @Test
        void aFullSnapshotSurvivesTheWire() {
            MedicalSyncPacket in = sync();
            MedicalSyncPacket out = roundTrip(in, in::encode, MedicalSyncPacket::decode);
            assertSyncEquals(in, out);
        }

        @Test
        void derivedStatsFieldsDoNotShiftPosition() {
            // Seventeen fields, most of them booleans: a reordering between writeStats and readStats
            // round-trips the size but swaps meanings. Distinct values per field catch that.
            DerivedStats in = stats();
            MedicalSyncPacket out = roundTrip(sync(), sync()::encode, MedicalSyncPacket::decode);
            DerivedStats s = out.stats();
            assertEquals(in.effectiveMaxHealth(), s.effectiveMaxHealth());
            assertEquals(in.healthModifier(), s.healthModifier());
            assertEquals(in.effectiveCurrentHealth(), s.effectiveCurrentHealth());
            assertEquals(in.totalBleeding(), s.totalBleeding());
            assertEquals(in.totalPain(), s.totalPain());
            assertEquals(in.systemicPain(), s.systemicPain());
            assertEquals(in.movementMultiplier(), s.movementMultiplier());
            assertEquals(in.sprintBlocked(), s.sprintBlocked());
            assertEquals(in.jumpMultiplier(), s.jumpMultiplier());
            assertEquals(in.state(), s.state());
            assertEquals(in.anyLegFracture(), s.anyLegFracture());
            assertEquals(in.anyArmFracture(), s.anyArmFracture());
            assertEquals(in.asphyxiating(), s.asphyxiating());
            assertEquals(in.painKoPending(), s.painKoPending());
            assertEquals(in.bothArmsDisabled(), s.bothArmsDisabled());
            assertEquals(in.bothLegsDisabled(), s.bothLegsDisabled());
            assertEquals(in.anyArmTourniquet(), s.anyArmTourniquet());
        }

        @Test
        void woundFlagsAndSeverityFitInTheirBytes() {
            // Severity is quantised to 0..100 and flags to a 6-bit field, both written as a single byte and
            // read back masked to 0xFF. A seventh flag would still fit; a 300 severity would not.
            int allFlags = WoundView.FLAG_BLEEDING | WoundView.FLAG_CONTROLLED | WoundView.FLAG_TREATED
                    | WoundView.FLAG_STABILIZED | WoundView.FLAG_CLOSED | WoundView.FLAG_MAJOR;
            LimbSummary[] one = {new LimbSummary(LimbType.HEAD, 0.5F, 1.0F, 2.0F, true,
                    List.of(new WoundView("puncture", 100, allFlags)))};
            MedicalSyncPacket in = new MedicalSyncPacket(stats(), one, 1.0D, 2.0D, 0.0F, 0.0F,
                    HealthState.HEALTHY, 0.0F);
            MedicalSyncPacket out = roundTrip(in, in::encode, MedicalSyncPacket::decode);
            WoundView w = out.limbs()[0].wounds().get(0);
            assertEquals(100, w.severity());
            assertEquals(allFlags, w.flags());
            assertTrue(w.bleeding() && w.bleedControlled() && w.treated()
                    && w.stabilized() && w.closed() && w.major());
        }

        @Test
        void aLimbWithNoWoundsRoundTripsAsAnEmptyList() {
            LimbSummary[] one = {new LimbSummary(LimbType.TORSO, 1.0F, 0.0F, 0.0F, false, List.of())};
            MedicalSyncPacket in = new MedicalSyncPacket(stats(), one, 1.0D, 2.0D, 0.0F, 0.0F,
                    HealthState.HEALTHY, 0.0F);
            assertTrue(roundTrip(in, in::encode, MedicalSyncPacket::decode).limbs()[0].wounds().isEmpty());
        }
    }

    @Nested
    class SmallPayloads {

        @Test
        void activeTreatmentRoundTripsWhenActive() {
            ActiveTreatmentPacket in = new ActiveTreatmentPacket(true, TreatmentAction.SUTURE_WOUND,
                    LimbType.RIGHT_LEG, 60, 123456789L, 42);
            assertEquals(in, roundTrip(in, in::encode, ActiveTreatmentPacket::decode));
        }

        @Test
        void anInactiveTreatmentWritesOnlyItsFlag() {
            // The record carries a null action when inactive; encode must short-circuit before writeEnum or
            // it NPEs on every treatment that ends.
            ActiveTreatmentPacket in = ActiveTreatmentPacket.inactive();
            ActiveTreatmentPacket out = roundTrip(in, in::encode, ActiveTreatmentPacket::decode);
            assertEquals(in, out);
            assertNull(out.action());
            assertNull(out.limb());
            assertEquals(-1, out.targetEntityId());
        }

        @Test
        void anActiveTreatmentWithNoLimbRoundTrips() {
            ActiveTreatmentPacket in = new ActiveTreatmentPacket(true, TreatmentAction.RESTORE_BLOOD,
                    null, 120, 5L, -1);
            ActiveTreatmentPacket out = roundTrip(in, in::encode, ActiveTreatmentPacket::decode);
            assertEquals(in, out);
            assertNull(out.limb(), "a global treatment has no limb, and null must survive the wire");
        }

        @Test
        void emptyPayloadsRoundTrip() {
            CancelTreatmentPacket cancel = new CancelTreatmentPacket();
            assertEquals(cancel, roundTrip(cancel, cancel::encode, CancelTreatmentPacket::decode));
            GiveUpPacket give = new GiveUpPacket();
            assertEquals(give, roundTrip(give, give::encode, GiveUpPacket::decode));
        }

        @Test
        void scalarPayloadsRoundTrip() {
            DownedStatePacket downed = new DownedStatePacket(77, true);
            assertEquals(downed, roundTrip(downed, downed::encode, DownedStatePacket::decode));

            HitAuthorityPacket auth = new HitAuthorityPacket(true);
            assertEquals(auth, roundTrip(auth, auth::encode, HitAuthorityPacket::decode));

            TourniquetStatePacket tq = new TourniquetStatePacket(9, 0b101010);
            assertEquals(tq, roundTrip(tq, tq::encode, TourniquetStatePacket::decode));

            TargetSheetRequestPacket req = new TargetSheetRequestPacket(31);
            assertEquals(req, roundTrip(req, req::encode, TargetSheetRequestPacket::decode));

            RemoveTourniquetPacket rm = new RemoveTourniquetPacket(LimbType.LEFT_ARM, 12);
            assertEquals(rm, roundTrip(rm, rm::encode, RemoveTourniquetPacket::decode));
        }

        @Test
        void resourceLocationPayloadsRoundTrip() {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("wfmedical", "suture_kit");

            MedicalActionPacket act = new MedicalActionPacket(id, LimbType.HEAD, 5);
            assertEquals(act, roundTrip(act, act::encode, MedicalActionPacket::decode));

            MedicalActionPacket noLimb = new MedicalActionPacket(id, null, -1);
            assertEquals(noLimb, roundTrip(noLimb, noLimb::encode, MedicalActionPacket::decode));

            TreatmentTargetRequestPacket req = new TreatmentTargetRequestPacket(8, id);
            assertEquals(req, roundTrip(req, req::encode, TreatmentTargetRequestPacket::decode));
        }

        @Test
        void compositePayloadsCarryTheirNestedSnapshot() {
            TargetSheetInfoPacket sheet = new TargetSheetInfoPacket(4, sync(), 0b010101);
            TargetSheetInfoPacket out = roundTrip(sheet, sheet::encode, TargetSheetInfoPacket::decode);
            assertEquals(sheet.targetEntityId(), out.targetEntityId());
            assertEquals(sheet.tourniquetMask(), out.tourniquetMask());
            assertSyncEquals(sheet.snapshot(), out.snapshot());
        }

        @Test
        void treatmentTargetInfoCarriesItsLimbTable() {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("wfmedical", "bandage");
            TreatmentTargetInfoPacket in = new TreatmentTargetInfoPacket(6, id, limbs(), 0b001100);
            TreatmentTargetInfoPacket out = roundTrip(in, in::encode, TreatmentTargetInfoPacket::decode);
            assertEquals(in.targetEntityId(), out.targetEntityId());
            assertEquals(in.itemId(), out.itemId());
            assertEquals(in.treatableMask(), out.treatableMask());
            assertArrayEquals(in.limbs(), out.limbs());
        }
    }

    @Nested
    class Pose {

        /**
         * Every component is a dyadic rational, so it is exactly representable as a float. The pose wire
         * format is float-per-component (see {@code poseIsQuantisedToFloatOnTheWire}); using values that
         * survive that conversion keeps the identity test about slot order rather than about rounding.
         */
        private Obb obb(int seed, LimbType limb) {
            return new Obb(new Vec3(seed, seed + 1, seed + 2),
                    new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                    new Vec3(0.25 + seed * 0.03125, 0.5, 0.125), limb);
        }

        @Test
        void poseIsQuantisedToFloatOnTheWire() {
            // Halving the pose packet's size is worth a float; the classification tolerance is ~1cm and
            // float carries ~7 digits, so this is deliberate. Pinned so a later switch to double is a
            // conscious change rather than a silent bandwidth regression.
            HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
            for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
                slot.set(rig, new Obb(new Vec3(0.1, 0.2, 0.3),
                        new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                        new Vec3(0.1, 0.1, 0.1), slot.limb));
            }
            PoseStreamPacket out = roundTrip(new PoseStreamPacket(rig),
                    new PoseStreamPacket(rig)::encode, PoseStreamPacket::decode);
            Vec3 c = out.rig().head.center();
            assertEquals(0.1, c.x, 1.0e-6, "float precision should still be well inside a centimetre");
            assertEquals((double) 0.1F, c.x, "the wire value is the float, not the original double");
        }

        @Test
        void aStreamedPoseKeepsItsSlotOrderAndServerAssignedLimbs() {
            HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
            int i = 0;
            for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
                slot.set(rig, obb(i++, slot.limb));
            }
            PoseStreamPacket in = new PoseStreamPacket(rig);
            PoseStreamPacket out = roundTrip(in, in::encode, PoseStreamPacket::decode);

            i = 0;
            for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
                Obb sent = obb(i++, slot.limb);
                Obb got = slot.get(out.rig());
                assertEquals(sent.center(), got.center(), "centre for " + slot);
                assertEquals(sent.half(), got.half(), "half-extent for " + slot);
                assertEquals(slot.limb, got.limb(),
                        "the limb tag must come from the slot, never from the client");
            }
        }

        @Test
        void theLimbTagIsIgnoredOnTheWireSoAClientCannotRelabelItsOwnBoxes() {
            // A malicious client could otherwise send "this box is my left leg" for its head box and take
            // head shots as leg hits. The encoder writes no limb at all; decode fills it from the slot.
            HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
            for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
                slot.set(rig, obb(0, LimbType.LEFT_LEG));
            }
            PoseStreamPacket out = roundTrip(new PoseStreamPacket(rig),
                    new PoseStreamPacket(rig)::encode, PoseStreamPacket::decode);
            assertEquals(LimbType.HEAD, out.rig().head.limb());
            assertEquals(LimbType.TORSO, out.rig().torso.limb());
        }

        @Test
        void theCachedAllArrayIsRebuiltAfterDecode() {
            HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
            for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
                slot.set(rig, obb(1, slot.limb));
            }
            PoseStreamPacket out = roundTrip(new PoseStreamPacket(rig),
                    new PoseStreamPacket(rig)::encode, PoseStreamPacket::decode);
            Obb[] all = out.rig().all();
            assertEquals(6, all.length);
            for (Obb o : all) {
                assertTrue(o != null, "a decoded rig must have all six boxes populated");
            }
        }
    }
}
