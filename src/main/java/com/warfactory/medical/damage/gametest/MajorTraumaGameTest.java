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

import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.body.trauma.Trauma;
import com.warfactory.medical.body.trauma.TraumaType;
import com.warfactory.medical.body.trauma.TraumaTypes;
import java.util.List;

/** Ballistic major trauma: vital => dead (once wounded; a healthy casualty is downed by resolve); limb below {@code majorTraumaLimbMultiplier} => downed, above => dead. */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class MajorTraumaGameTest {

    private static final String TEMPLATE = "empty";

    private static HealthState hit(GameTestHelper helper, HitboxPart part, double lethalMultiple) {
        return hit(helper, part, lethalMultiple, false);
    }

    /** @param wounded start below the resolveHealthFraction line, where a lethal hit really does kill */
    private static HealthState hit(GameTestHelper helper, HitboxPart part, double lethalMultiple, boolean wounded) {
        TestBodies.Victim v = TestBodies.victim(helper);
        if (wounded) {
            MedicalProfile p = TestBodies.profileOf(helper, v);
            TraumaType bruise = TraumaTypes.LIVE.get("blunt_force_trauma");
            for (LimbType lt : new LimbType[]{LimbType.TORSO, LimbType.LEFT_LEG}) {
                p.addTrauma(lt, new Trauma(bruise, lt, 1.0F, 0L));
            }
            p.recompute(MedicalConfig.toPhysiologyParams());
        }
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

    /** Resolve (STALKER GAMMA): a healthy casualty is downed by a hit that would kill, never one-tapped. */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aHealthyCasualtyIsDownedNotKilledByOneHit(GameTestHelper helper) {
        HealthState torso = hit(helper, HitboxPart.TORSO, 1.25);
        helper.assertTrue(torso == HealthState.UNCONSCIOUS, "torso at 1.25x lethal on a healthy casualty: " + torso);
        HealthState arm = hit(helper, HitboxPart.RIGHT_ARM, MedicalConfig.majorTraumaLimbMultiplier() * 1.05);
        helper.assertTrue(arm == HealthState.UNCONSCIOUS, "arm past the limb multiplier on a healthy casualty: " + arm);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aLethalLegHitDownsInsteadOfKilling(GameTestHelper helper) {
        HealthState s = hit(helper, HitboxPart.LEFT_LEG, belowLimbKill());
        helper.assertTrue(s == HealthState.UNCONSCIOUS, "leg at " + belowLimbKill() + "x lethal: " + s);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void theSameHitToTheTorsoKillsAWoundedCasualty(GameTestHelper helper) {
        HealthState s = hit(helper, HitboxPart.TORSO, belowLimbKill(), true);
        helper.assertTrue(s == HealthState.DEAD, "torso at " + belowLimbKill() + "x lethal: " + s);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aLimbHitPastTheLimbMultiplierKillsAWoundedCasualty(GameTestHelper helper) {
        double m = MedicalConfig.majorTraumaLimbMultiplier() * 1.05;
        HealthState s = hit(helper, HitboxPart.RIGHT_ARM, m, true);
        helper.assertTrue(s == HealthState.DEAD, "arm at " + m + "x lethal: " + s);
        helper.succeed();
    }
}
