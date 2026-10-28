package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.MedicalProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** WFLib near-miss rounds raise stress, cigarettes lower it. WFLib types only in {@link WfLibRounds}. Registered by {@link WfLibGameTests}. */
@PrefixGameTestTemplate(false)
public class WfLibStressGameTest {

    private static final String TEMPLATE = "empty";
    private static final BlockPos STAND = new BlockPos(1, 10, 1);

    @WfLibMode(true)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aRoundPassingCloseRaisesStressAndOneFarAwayDoesNot(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.spawn(helper, TestBodies.victim(helper, STAND, 0.0F));
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        Vec3 eye = v.getEyePosition();
        WfLibRounds.fire(helper.getLevel(), eye.add(5.0, 0.0, 2.0), new Vec3(0.0, 0.0, -4.0));
        helper.assertTrue(profile.getStress() == 0.0F, "a round 5 blocks away suppressed: " + profile.getStress());
        WfLibRounds.fire(helper.getLevel(), eye.add(1.0, 0.0, 2.0), new Vec3(0.0, 0.0, -4.0));
        float params = MedicalConfig.stressParams().suppressionGain() * (1.0F - 1.0F / MedicalConfig.stressParams().suppressionRange());
        helper.assertTrue(Math.abs(profile.getStress() - params) < 1.0e-4F,
                "round at 1 block: stress " + profile.getStress() + ", expected " + params);
        helper.succeed();
    }

    @WfLibMode(true)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aRoundThatNeverReachesThePlayerDoesNotSuppress(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.spawn(helper, TestBodies.victim(helper, STAND, 0.0F));
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        Vec3 eye = v.getEyePosition();
        WfLibRounds.fire(helper.getLevel(), eye.add(1.0, 0.0, 6.0), new Vec3(0.0, 0.0, -4.0));
        helper.assertTrue(profile.getStress() == 0.0F, "the segment ended 2 blocks short: " + profile.getStress());
        helper.succeed();
    }

    @WfLibMode(true)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aSmokedCigaretteLowersStress(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.spawn(helper, TestBodies.victim(helper, STAND, 0.0F));
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        profile.setStress(0.8F);
        WfLibRounds.smoked(v);
        float expected = 0.8F - MedicalConfig.stressParams().cigaretteRelief();
        helper.assertTrue(Math.abs(profile.getStress() - expected) < 1.0e-5F,
                "stress " + profile.getStress() + ", expected " + expected);
        helper.succeed();
    }
}
