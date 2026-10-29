package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** WFLib artefact hooks as WFMedical claims them. WFLib types only in {@link WfLibRounds}. */
@PrefixGameTestTemplate(false)
public class WfLibArtifactGameTest {

    private static final String TEMPLATE = "empty";
    private static final BlockPos STAND = new BlockPos(1, 10, 1);

    @WfLibMode(true)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aDrainingArtefactLaysBluntTraumaOnTheTorso(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.spawn(helper, TestBodies.victim(helper, STAND, 0.0F));
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        WfLibRounds.artifactTick(v, -1.0F, 0.0F);
        helper.assertTrue(profile.limb(LimbType.TORSO).getTraumas().stream()
                        .anyMatch(t -> t.getType().getId().equals("blunt_force_trauma")),
                "no blunt trauma: " + profile.limb(LimbType.TORSO).getTraumas());
        WfLibRounds.artifactTick(v, -1.0F, 0.0F);
        helper.assertTrue(profile.limb(LimbType.TORSO).getTraumas().size() == 1, "drains stack into one trauma");
        helper.succeed();
    }

    @WfLibMode(true)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void bleedingRestoreClotsAndItsNegativeThins(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.spawn(helper, TestBodies.victim(helper, STAND, 0.0F));
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        WfLibRounds.artifactTick(v, 0.0F, 0.03F);
        helper.assertTrue(Math.abs(profile.getClottingBoost() - 0.5F) < 1.0e-5F,
                "clotting " + profile.getClottingBoost());
        helper.assertTrue(profile.getThinning() == 0.0F, "clotting thinned");
        WfLibRounds.artifactTick(v, 0.0F, -0.012F);
        helper.assertTrue(Math.abs(profile.getThinning() - 0.2F) < 1.0e-5F, "thinning " + profile.getThinning());
        helper.assertTrue(profile.getThinningEndTick() > helper.getLevel().getGameTime(), "thinning already over");
        helper.succeed();
    }
}
