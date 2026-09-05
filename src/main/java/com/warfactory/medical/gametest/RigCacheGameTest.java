package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.damage.MedicalHitReg;
import com.warfactory.medical.core.damage.rig.HumanoidRig;
import com.warfactory.medical.core.damage.rig.Obb;
import com.warfactory.medical.core.damage.rig.RigCache;
import com.warfactory.medical.core.damage.rig.RigTuning;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The rig cache and the hit envelope.
 *
 * <p>Two independent things live here because both are about the rig's surroundings rather than its
 * geometry: the per-tick memoisation that keeps one shot from rebuilding the rig five times, and the
 * plausibility bound that decides whether a client-streamed pose may be trusted.
 *
 * <p>The pose-hint path is opt-in ({@code hitAuthority = CLIENT_HINT}) and a gametest cannot flip a
 * config value, so {@link RigCache#resolve} always takes the server-rebuild branch here. The validation
 * predicate is exercised directly instead -- it is the actual trust boundary, and leaving it untested
 * because the switch is off by default is how an exploit ships.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class RigCacheGameTest {

    private static final String TEMPLATE = "empty";

    private static Obb box(Vec3 center, double half) {
        return new Obb(center, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                new Vec3(half, half, half), LimbType.TORSO);
    }

    /** A rig whose six boxes are all the same plausible size, sitting near the feet origin. */
    private static HumanoidRig.LocalRig uniformRig(Vec3 center, double half) {
        HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            slot.set(rig, box(center, half));
        }
        return rig;
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theRigIsBuiltOncePerTickAndReused(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        HumanoidRig.LocalRig first = RigCache.get(v);
        HumanoidRig.LocalRig second = RigCache.get(v);
        if (first != second) {
            helper.fail("the rig was rebuilt within one tick -- the cache is not hitting, and one shot "
                    + "rebuilds it once per pellet plus once per debug frame");
        }
        if (RigCache.resolve(v) != first) {
            helper.fail("resolve should return the cached rig when no client hint applies");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void twoVictimsDoNotShareARig(GameTestHelper helper) {
        TestBodies.Victim a = TestBodies.victim(helper);
        TestBodies.Victim b = TestBodies.victim(helper, new net.minecraft.core.BlockPos(2, 1, 2), 0.0F);
        if (a.getId() == b.getId()) {
            helper.fail("the two victims share an entity id, so this proves nothing");
        }
        if (RigCache.get(a) == RigCache.get(b)) {
            helper.fail("two players got the same rig instance -- the cache key is not per-entity");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theCachedRigIsTheSameOneComputeWouldBuild(GameTestHelper helper) {
        // Guard: a cache that returned a stale or empty rig would still satisfy the identity test above.
        TestBodies.Victim v = TestBodies.victim(helper);
        HumanoidRig.LocalRig cached = RigCache.get(v);
        HumanoidRig.LocalRig fresh = HumanoidRig.compute(v);
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            Obb c = slot.get(cached);
            Obb f = slot.get(fresh);
            if (c == null || f == null) {
                helper.fail("missing box for " + slot);
                return;
            }
            if (c.center().distanceTo(f.center()) > 1.0e-6) {
                helper.fail("cached " + slot + " at " + c.center() + " but a fresh build says " + f.center());
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aNormalPoseIsAccepted(GameTestHelper helper) {
        // Guard for the rejection tests below: if this were false they would all pass vacuously.
        TestBodies.Victim v = TestBodies.victim(helper);
        if (!RigCache.plausible(v, HumanoidRig.compute(v))) {
            helper.fail("the server's own rig was rejected as implausible");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aMissingBoxIsRejected(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        HumanoidRig.LocalRig rig = uniformRig(new Vec3(0.0, 1.0, 0.0), 0.2);
        HumanoidRig.LocalRig.Slot.HEAD.set(rig, null);
        if (RigCache.plausible(v, rig)) {
            helper.fail("a client that deleted its own head box was accepted");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aBoxFlungAwayFromTheBodyIsRejected(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        if (RigCache.plausible(v, uniformRig(new Vec3(0.0, 500.0, 0.0), 0.2))) {
            helper.fail("a pose with its hitboxes 500 blocks away was accepted");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aShrunkOrBallooningBoxIsRejected(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        if (RigCache.plausible(v, uniformRig(new Vec3(0.0, 1.0, 0.0), 0.0001))) {
            helper.fail("a pose shrunk to nothing was accepted -- that is an unhittable player");
        }
        if (RigCache.plausible(v, uniformRig(new Vec3(0.0, 1.0, 0.0), 50.0))) {
            helper.fail("a pose ballooned to 50 blocks was accepted -- that is a player who catches "
                    + "every shot on the map");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aProneBodyIsStillJudgedAgainstAFullBodyLength(GameTestHelper helper) {
        // A downed or swimming body's AABB is short, but the rig still spans a full body from the feet
        // origin. Bounding by the current AABB height would reject every legitimate prone pose.
        TestBodies.Victim v = TestBodies.victim(helper);
        v.setPose(Pose.SWIMMING);
        if (!RigCache.plausible(v, HumanoidRig.compute(v))) {
            helper.fail("a prone player's own rig was rejected as implausible");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void hintsCanBeSubmittedAndClearedWithoutDisturbingTheServerRig(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        HumanoidRig.LocalRig server = RigCache.get(v);

        // A wildly wrong hint. With hitAuthority defaulting to SERVER it must be ignored outright; the
        // plausibility check would reject it anyway, and this pins both layers of that defence.
        RigCache.submitHint(v.getId(), uniformRig(new Vec3(0.0, 500.0, 0.0), 40.0),
                helper.getLevel().getGameTime());
        if (RigCache.resolve(v) != server) {
            helper.fail("a client hint displaced the server rig under the default SERVER authority");
        }
        RigCache.clearHint(v.getId());
        if (RigCache.resolve(v) != server) {
            helper.fail("clearing a hint disturbed the cached server rig");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theHitEnvelopeWidensAPlayersBoxButNotAnArbitraryEntitys(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        AABB raw = v.getBoundingBox();
        AABB envelope = MedicalHitReg.registrationBox(v);

        RigTuning.RigPose pose = HumanoidRig.resolvePose(v);
        double h = com.warfactory.medical.config.MedicalConfig.hitEnvelopeReachHorizontal(pose);
        double vert = com.warfactory.medical.config.MedicalConfig.hitEnvelopeReachVertical(pose);

        if (h > 0.0 || vert > 0.0) {
            if (envelope.getSize() <= raw.getSize()) {
                helper.fail("the envelope did not widen the box although reach is h=" + h + " v=" + vert);
            }
        } else if (envelope != raw) {
            helper.fail("with zero reach the envelope should be the untouched box");
        }

        if (!MedicalHitReg.isEnvelopeTarget(v)) {
            helper.fail("a player must be an envelope target");
        }
        var pig = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
        if (pig != null && MedicalHitReg.isEnvelopeTarget(pig)) {
            helper.fail("a pig is not a medical target and must not get a widened hit box");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theEnvelopeCanBeAppliedToACallerSuppliedBox(GameTestHelper helper) {
        // The lag-compensation path hands in its own rewound box; it must be widened by the same margins
        // rather than being silently replaced by the entity's current box.
        TestBodies.Victim v = TestBodies.victim(helper);
        AABB elsewhere = new AABB(100.0, 60.0, 100.0, 100.6, 61.8, 100.6);
        AABB widened = MedicalHitReg.registrationBox(v, elsewhere);
        if (widened.getCenter().distanceTo(elsewhere.getCenter()) > 1.0e-9) {
            helper.fail("the supplied box was recentred onto the entity's live position: "
                    + widened.getCenter() + " vs " + elsewhere.getCenter());
        }
        if (widened.getSize() < elsewhere.getSize()) {
            helper.fail("the supplied box shrank");
        }
        helper.succeed();
    }
}
