package com.warfactory.medical.damage.gametest;

import com.norwood.ahf.part.HitboxPart;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.HealthState;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.gametest.TestBodies;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Ballistic major trauma: vital => dead; limb below {@code majorTraumaLimbMultiplier} => downed, above => dead. */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class MajorTraumaGameTest {

    private static final String TEMPLATE = "empty";

    private static HealthState hit(GameTestHelper helper, HitboxPart part, double lethalMultiple) {
        TestBodies.Victim v = TestBodies.victim(helper);
        Vec3 feet = v.position();
        DamageSource s = WfLibRounds.strike(helper.getLevel(), "SMALL_ARMS", feet.add(0.0, 1.0, 2.0),
                feet.add(0.0, 1.0, -2.0), List.of(part));
        float lethal = MedicalConfig.maxHealthPoints() * (float) MedicalConfig.majorTraumaFractionBallistic();
        v.hurt(s, (float) (lethal * lethalMultiple));
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        return profile.getState();
    }

    private static double belowLimbKill() {
        return (1.0 + MedicalConfig.majorTraumaLimbMultiplier()) / 2.0;
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aLethalLegHitDownsInsteadOfKilling(GameTestHelper helper) {
        HealthState s = hit(helper, HitboxPart.LEFT_LEG, belowLimbKill());
        helper.assertTrue(s == HealthState.UNCONSCIOUS, "leg at " + belowLimbKill() + "x lethal: " + s);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void theSameHitToTheTorsoStillKills(GameTestHelper helper) {
        HealthState s = hit(helper, HitboxPart.TORSO, belowLimbKill());
        helper.assertTrue(s == HealthState.DEAD, "torso at " + belowLimbKill() + "x lethal: " + s);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aLimbHitPastTheLimbMultiplierKills(GameTestHelper helper) {
        double m = MedicalConfig.majorTraumaLimbMultiplier() * 1.05;
        HealthState s = hit(helper, HitboxPart.RIGHT_ARM, m);
        helper.assertTrue(s == HealthState.DEAD, "arm at " + m + "x lethal: " + s);
        helper.succeed();
    }
}
