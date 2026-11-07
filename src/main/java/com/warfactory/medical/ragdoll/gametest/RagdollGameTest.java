package com.warfactory.medical.ragdoll.gametest;

import com.norwood.ahf.stance.AhfStance;
import com.norwood.ahf.stance.Posture;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.HealthState;
import com.warfactory.medical.body.MedicalEngine;
import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.api.MedicalState;
import com.warfactory.medical.body.gametest.TestBodies;
import com.wf.wflib.ragdoll.api.Ragdolls;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Downing a patient hands the body to WFLib's ragdoll; recovery stands it up again. */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RagdollGameTest {
    private static final String TEMPLATE = "empty";

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE, timeoutTicks = 200)
    public void aDownedPatientGoesLimpAndRecoveryStandsThemUp(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper, new BlockPos(1, 2, 1), 0.0F);
        MedicalProfile p = TestBodies.profileOf(helper, v);
        p.setOverdoseUnconscious(true);
        MedicalEngine.resync(v, true);
        helper.assertTrue(MedicalState.isDowned(v), "setup: overdose should down the patient");

        helper.runAfterDelay(8, () -> {
            helper.assertTrue(Ragdolls.active(v), "the downed patient is not a ragdoll");
            p.setOverdoseUnconscious(false);
            p.setState(HealthState.HEALTHY);
            p.setForcedState(null);
            MedicalEngine.resync(v, true);
            helper.assertTrue(!MedicalState.isDowned(v), "setup: recovery should clear downed");
            helper.runAfterDelay(60, () -> {
                helper.assertTrue(!Ragdolls.active(v), "the ragdoll never released");
                helper.assertTrue(AhfStance.posture(v) == Posture.STAND, "posture after recovery " + AhfStance.posture(v));
                helper.succeed();
            });
        });
    }
}
