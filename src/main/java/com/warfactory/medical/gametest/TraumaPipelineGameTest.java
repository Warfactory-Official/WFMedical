package com.warfactory.medical.gametest;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.config.HitRegMode;
import com.norwood.ahf.part.HitboxPart;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Real {@code hurt()} -> {@code MedicalEventHandler} -> AHF part -> wound on that limb. Rounds are
 * {@link TestRound}s: AHF classifies and gap-tests their own segment.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class TraumaPipelineGameTest {

    private static final String TEMPLATE = "empty";

    private static final double HEAD_Y = 1.62;
    private static final double TORSO_Y = 0.90;
    /** Above the arena lid: the arrow's lane is air. */
    private static final BlockPos ARROW_STAND = new BlockPos(1, 10, 1);

    /** Frontal segment at {@code up}, {@code side} blocks off the victim's centre line. */
    private static TestRound frontal(GameTestHelper helper, TestBodies.Victim v, double side, double up,
                                     HitboxPart part) {
        Vec3 feet = v.position();
        return new TestRound(helper, feet.add(side, up, 2.0), feet.add(side, up, -2.0), part);
    }

    private static TestRound frontal(GameTestHelper helper, TestBodies.Victim v, double up) {
        return frontal(helper, v, 0.0, up, null);
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aVictimStartsUninjured(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        helper.assertTrue(TestBodies.allTraumas(profile).isEmpty(),
                "a fresh player should have no traumas; had " + TestBodies.describe(profile));
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aRoundWoundsTheLimbItsSegmentEntered(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        v.hurt(frontal(helper, v, HEAD_Y), 4.0F);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        helper.assertTrue(!profile.limb(LimbType.HEAD).getTraumas().isEmpty(),
                "a round entering the head should wound the HEAD; got " + TestBodies.describe(profile));
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aChestRoundWoundsOnlyTheTorso(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        v.hurt(frontal(helper, v, TORSO_Y), 4.0F);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        helper.assertTrue(!profile.limb(LimbType.TORSO).getTraumas().isEmpty(),
                "a chest-height round should wound the TORSO; got " + TestBodies.describe(profile));
        helper.assertTrue(profile.limb(LimbType.HEAD).getTraumas().isEmpty(),
                "a chest-height round should NOT wound the head; got " + TestBodies.describe(profile));
        helper.succeed();
    }

    /** Caller-classified part wins over the segment, which here clears the body by 1 (WFLib rounds classify at strike). */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aPreClassifiedPartIsWoundedEvenOffItsSegment(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        v.hurt(frontal(helper, v, 1.0, TORSO_Y, HitboxPart.LEFT_LEG), 4.0F);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        helper.assertTrue(!profile.limb(LimbType.LEFT_LEG).getTraumas().isEmpty()
                        && profile.limb(LimbType.TORSO).getTraumas().isEmpty(),
                "the round's own part (LEFT_LEG) should take the wound; got " + TestBodies.describe(profile));
        helper.succeed();
    }

    /**
     * Real bow arrow (3/tick) from 2.9 in front: vanilla calls {@code onHit} before moving it, so AHF sees a
     * derived segment starting ~2.8 short of the chest; bounded => gap-rejected, bounces. Polled: with WFLib the
     * arrow's first tick comes late.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aFullDrawArrowInFlightWoundsTheTorso(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.spawn(helper, TestBodies.victim(helper, ARROW_STAND, 0.0F));
        TestBodies.Victim archer = TestBodies.attacker(helper, v, 4.0);
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setOwner(archer);
        Vec3 at = v.position().add(0.0, TORSO_Y, 2.9);
        arrow.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        arrow.setDeltaMovement(0.0, 0.0, -3.0);
        helper.getLevel().addFreshEntity(arrow);
        helper.succeedWhen(() -> {
            MedicalProfile profile = TestBodies.profileOf(helper, v);
            helper.assertTrue(!profile.limb(LimbType.TORSO).getTraumas().isEmpty(),
                    "a full-draw arrow at the chest left " + TestBodies.describe(profile) + " after "
                            + arrow.tickCount + " arrow ticks");
            arrow.discard();
            v.discard();
        });
    }

    /** PRECISE: a segment clearing every limb box by more than the tolerance never reaches medical. */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aRoundThroughTheGapBesideTheBodyIsRejected(GameTestHelper helper) {
        helper.assertTrue(AhfConfig.hitRegistrationMode() == HitRegMode.PRECISE,
                "AHF default hitRegistrationMode must be PRECISE; got " + AhfConfig.hitRegistrationMode());
        TestBodies.Victim v = TestBodies.victim(helper);
        float before = v.getHealth();
        boolean hurt = v.hurt(frontal(helper, v, 1.0, TORSO_Y, null), 4.0F);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        helper.assertTrue(!hurt && TestBodies.allTraumas(profile).isEmpty() && v.getHealth() == before,
                "a whiff 1 block beside the torso should be cancelled; hurt=" + hurt + " "
                        + TestBodies.describe(profile));
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void oneRoundLeavesAShortReadableInjuryListOnTheLimbItHit(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        v.hurt(frontal(helper, v, TORSO_Y), 9.0F);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        int torso = profile.limb(LimbType.TORSO).getTraumas().size();
        helper.assertTrue(torso > 0, "the round should have wounded the torso; got " + TestBodies.describe(profile));
        helper.assertTrue(torso <= MedicalConfig.maxTraumasPerHit(), "one round left " + torso
                + " wounds in one limb: " + TestBodies.describe(profile));
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aBallisticWoundBleedsAndHurts(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        v.hurt(frontal(helper, v, TORSO_Y), 5.0F);
        var torso = TestBodies.profileOf(helper, v).limb(LimbType.TORSO);
        torso.rebuildCache();
        helper.assertTrue(!torso.getTraumas().isEmpty(), "expected a torso wound");
        helper.assertTrue(torso.getCachedPain() > 0.0F,
                "a fresh ballistic wound should generate pain; got " + torso.getCachedPain());
        helper.succeed();
    }

    /** Unblocked hit: event amount zeroed; health follows the medical model. */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theMedicalSystemAbsorbsTheVanillaDamage(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        float before = v.getHealth();
        v.hurt(frontal(helper, v, TORSO_Y), 4.0F);
        helper.assertTrue(v.getHealth() >= before - 0.001F,
                "vanilla damage should have been absorbed; health went " + before + " -> " + v.getHealth());
        helper.succeed();
    }
}
