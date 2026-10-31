package com.warfactory.medical.treatment.gametest;

import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.rig.HumanoidRig;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.treatment.MedicalPoses;
import com.warfactory.medical.treatment.TreatmentContent;
import com.warfactory.medical.treatment.MedicalActionService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.warfactory.medical.body.gametest.TestBodies;

/** {@link MedicalPoses}: a running treatment poses the server rig from its set; done => vanilla again. */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreatmentPoseGameTest {

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = "empty")
    public void bandagingPosesTheRig(GameTestHelper helper) {
        TestBodies.Victim actor = TestBodies.spawn(helper, TestBodies.victim(helper));
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(0, new ItemStack(TreatmentContent.BANDAGE.get(), 2));
        Vec3 idle = HumanoidRig.compute(actor).rightForearm.center();
        if (!MedicalActionService.start(actor, BuiltInRegistries.ITEM.getKey(TreatmentContent.BANDAGE.get()),
                LimbType.LEFT_ARM, -1)) {
            helper.fail("bandage treatment refused");
            return;
        }
        helper.assertTrue(MedicalPoses.BANDAGE.equals(AhfHooks.poseSet(actor)), "set " + AhfHooks.poseSet(actor));
        Vec3 treating = HumanoidRig.compute(actor).rightForearm.center();
        helper.assertTrue(treating.distanceTo(idle) > 0.1, "right forearm " + treating + " still at " + idle);
        MedicalProfile p = TestBodies.profileOf(helper, actor);
        MedicalActionService.tick(actor, p, p.getActiveStartGameTime() + p.getActiveTotalTicks());
        helper.assertTrue(AhfHooks.poseSet(actor) == null, "set after the treatment " + AhfHooks.poseSet(actor));
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = "empty")
    public void injectorsInjectOralsDoNot(GameTestHelper helper) {
        helper.assertTrue(kind(com.warfactory.medical.substance.SubstanceContent.MORPHINE_SYRINGE.get()) == MedicalPoses.Kind.MEDKIT, "syringe");
        helper.assertTrue(kind(TreatmentContent.MEDKIT.get()) == MedicalPoses.Kind.MEDKIT, "medkit");
        helper.assertTrue(kind(TreatmentContent.SPLINT.get()) == MedicalPoses.Kind.BANDAGE, "splint");
        helper.assertTrue(kind(TreatmentContent.PAINKILLERS.get()) == MedicalPoses.Kind.NONE, "painkillers");
        helper.assertTrue(MedicalPoses.kind("wfmedical:resuscitate") == MedicalPoses.Kind.NONE, "resuscitate");
        helper.succeed();
    }

    private static MedicalPoses.Kind kind(Item item) {
        return MedicalPoses.kind(BuiltInRegistries.ITEM.getKey(item).toString());
    }
}
