package com.warfactory.medical.gametest;

import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.stance.AhfStance;
import com.norwood.ahf.stance.Posture;
import com.norwood.ahf.stance.StanceState;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.server.MedicalEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Downed / leg crawl / sway exports through AHF. */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class MedicalStanceGameTest {

    private static final String TEMPLATE = "empty";

    /** Template stone sits one block up: y=1 is inside it, so a stand-up space check needs y=2. */
    private static TestBodies.Victim victim(GameTestHelper helper) {
        return TestBodies.victim(helper, new BlockPos(1, 2, 1), 0.0F);
    }

    private static void wound(MedicalProfile p, LimbType limb, String id, float sev, int n) {
        for (int i = 0; i < n; i++) {
            p.addTrauma(limb, new Trauma(TraumaRegistry.active().get(id), limb, sev, 0L));
        }
        p.limb(limb).rebuildCache();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void downedLocksTheAhfPostureAndReleasesIt(GameTestHelper helper) {
        TestBodies.Victim v = victim(helper);
        MedicalProfile p = TestBodies.profileOf(helper, v);
        p.setOverdoseUnconscious(true);
        MedicalEngine.resync(v, true);

        helper.assertTrue(MedicalState.isDowned(v), "setup: overdose should down the victim");
        helper.assertTrue(AhfStance.posture(v) == Posture.DOWNED, "posture " + AhfStance.posture(v));
        helper.assertTrue(AhfStance.weaponsBlocked(v), "downed must block weapons");
        helper.assertTrue(v.getBbHeight() < 1.0F, "downed height " + v.getBbHeight());
        helper.assertTrue(!HitboxApi.isUprightHumanoid(v), "downed rig must not be upright");

        p.setOverdoseUnconscious(false);
        p.setState(HealthState.HEALTHY);
        p.setForcedState(null);
        MedicalEngine.resync(v, true);
        helper.assertTrue(!MedicalState.isDowned(v), "setup: recovery should clear downed");
        helper.assertTrue(AhfStance.posture(v) == Posture.STAND && !AhfStance.weaponsBlocked(v),
                "posture after recovery " + AhfStance.posture(v));
        helper.assertTrue(v.getBbHeight() > 1.5F, "standing height " + v.getBbHeight());
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void twoDisabledLegsCrawlWithoutDowning(GameTestHelper helper) {
        TestBodies.Victim v = victim(helper);
        MedicalProfile p = TestBodies.profileOf(helper, v);
        wound(p, LimbType.LEFT_LEG, "fracture", 1.0F, 1);
        wound(p, LimbType.RIGHT_LEG, "fracture", 1.0F, 1);
        wound(p, LimbType.LEFT_LEG, "internal_bleeding", 1.0F, 6);
        wound(p, LimbType.RIGHT_LEG, "internal_bleeding", 1.0F, 6);
        MedicalEngine.resync(v, true);

        helper.assertTrue(MedicalState.isBothLegsDisabled(v) && !MedicalState.isDowned(v),
                "setup: legs disabled " + MedicalState.isBothLegsDisabled(v) + ", downed " + MedicalState.isDowned(v));
        StanceState s = AhfStance.get(v);
        helper.assertTrue(s != null && s.crawl() && AhfStance.posture(v) == Posture.PRONE,
                "crawl lock " + (s != null && s.crawl()) + ", posture " + AhfStance.posture(v));
        helper.assertTrue(!AhfStance.weaponsBlocked(v), "crawling must not block weapons");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aHealthyResyncCreatesNoStanceState(GameTestHelper helper) {
        TestBodies.Victim v = victim(helper);
        MedicalEngine.resync(v, true);
        helper.assertTrue(AhfStance.get(v) == null, "an unlocked player should not get (and sync) stance state");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void swayExportsReflectArmTourniquetAndFracture(GameTestHelper helper) {
        TestBodies.Victim v = victim(helper);
        MedicalProfile p = TestBodies.profileOf(helper, v);
        p.recompute(MedicalConfig.toPhysiologyParams());
        helper.assertTrue(MedicalState.painSwayIntensity(v) == 0.0F && !MedicalState.hasArmTourniquet(v)
                && !MedicalState.hasArmFracture(v), "a healthy player sways");

        p.limb(LimbType.LEFT_ARM).setTourniquet(true);
        p.recompute(MedicalConfig.toPhysiologyParams());
        helper.assertTrue(MedicalState.hasArmTourniquet(v) && !MedicalState.hasArmFracture(v),
                "tourniquet only: tourniquet " + MedicalState.hasArmTourniquet(v));
        helper.assertTrue(MedicalState.painSwayIntensity(v) >= (float) MedicalConfig.tourniquetArmSway(),
                "tourniquet floor not applied: " + MedicalState.painSwayIntensity(v));

        wound(p, LimbType.RIGHT_ARM, "fracture", 1.0F, 1);
        p.recompute(MedicalConfig.toPhysiologyParams());
        helper.assertTrue(MedicalState.hasArmFracture(v), "arm fracture not exported");
        helper.succeed();
    }
}
