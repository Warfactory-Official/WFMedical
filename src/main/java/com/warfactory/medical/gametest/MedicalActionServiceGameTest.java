package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.item.ModItems;
import com.warfactory.medical.server.MedicalActionService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The item-use flow: holding a bandage and using it, from the network packet down to the wound.
 *
 * <p>{@code TreatmentServiceTest} covers the rules -- which wound an item picks and what it does. This
 * covers the plumbing in front of them, which is where the failures are boring and total: an item that
 * is not in the hotbar, a treatment that never completes, an item consumed for a no-op, a lock that is
 * never released so the player can never treat anything again.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class MedicalActionServiceGameTest {

    private static final String TEMPLATE = "empty";

    private static ResourceLocation idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /** Give the actor {@code item} in the selected hotbar slot and return its registry id. */
    private static ResourceLocation hold(TestBodies.Victim actor, Item item) {
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(0, new ItemStack(item, 2));
        return idOf(item);
    }

    private static Trauma wound(GameTestHelper helper, TestBodies.Victim v, LimbType limb, String id,
                                float severity) {
        TraumaType type = TraumaRegistry.active().get(id);
        if (type == null) {
            helper.fail("the active registry has no " + id);
            return null;
        }
        MedicalProfile p = TestBodies.profileOf(helper, v);
        Trauma t = new Trauma(type, limb, severity, 0L);
        p.addTrauma(limb, t);
        p.limb(limb).rebuildCache();
        return t;
    }

    /** Advance the treatment to completion by ticking it at a game time past its duration. */
    private static void complete(GameTestHelper helper, TestBodies.Victim actor) {
        MedicalProfile p = TestBodies.profileOf(helper, actor);
        long done = p.getActiveStartGameTime() + p.getActiveTotalTicks();
        MedicalActionService.tick(actor, p, done);
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void usingABandageOnYourselfLocksInATreatmentAndThenAppliesIt(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        Trauma cut = wound(helper, v, LimbType.LEFT_ARM, "laceration_large", 0.8F);
        if (cut == null) {
            return;
        }
        ResourceLocation id = hold(v, ModItems.BANDAGE.get());

        if (!MedicalActionService.start(v, id, LimbType.LEFT_ARM, -1)) {
            helper.fail("the bandage was refused");
            return;
        }
        if (!profile.hasActiveTreatment()) {
            helper.fail("no treatment was locked in, so the progress bar would never appear");
        }
        if (profile.getActiveAction() != TreatmentAction.REDUCE_BLEEDING) {
            helper.fail("wrong action recorded: " + profile.getActiveAction());
        }
        if (profile.getActiveLimb() != LimbType.LEFT_ARM) {
            helper.fail("the limb choice was lost: " + profile.getActiveLimb());
        }

        complete(helper, v);

        if (profile.hasActiveTreatment()) {
            helper.fail("the treatment never finished, so the player is locked out of treating anything");
        }
        if (cut.getBleedFactor() != 0.0F) {
            helper.fail("the bandage completed without stopping the bleed");
        }
        if (v.getInventory().getItem(0).getCount() != 1) {
            helper.fail("the bandage was not consumed: " + v.getInventory().getItem(0));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aTreatmentDoesNotApplyBeforeItsDurationHasElapsed(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        Trauma cut = wound(helper, v, LimbType.TORSO, "laceration_large", 0.8F);
        if (cut == null) {
            return;
        }
        ResourceLocation id = hold(v, ModItems.BANDAGE.get());
        MedicalActionService.start(v, id, LimbType.TORSO, -1);

        MedicalActionService.tick(v, profile, profile.getActiveStartGameTime() + 1L);

        if (!profile.hasActiveTreatment()) {
            helper.fail("the treatment completed after a single tick; the cast time means nothing");
        }
        if (cut.getBleedFactor() != 1.0F) {
            helper.fail("the wound was treated before the cast finished");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void onlyOneTreatmentCanBeInFlightAtATime(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        wound(helper, v, LimbType.TORSO, "laceration_large", 0.8F);
        ResourceLocation id = hold(v, ModItems.BANDAGE.get());

        if (!MedicalActionService.start(v, id, LimbType.TORSO, -1)) {
            helper.fail("the first bandage was refused");
            return;
        }
        if (MedicalActionService.start(v, id, LimbType.TORSO, -1)) {
            helper.fail("a second treatment started on top of the first");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void cancellingReleasesTheLock(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        wound(helper, v, LimbType.TORSO, "laceration_large", 0.8F);
        ResourceLocation id = hold(v, ModItems.BANDAGE.get());

        MedicalActionService.start(v, id, LimbType.TORSO, -1);
        MedicalActionService.cancel(v, "test");
        if (profile.hasActiveTreatment()) {
            helper.fail("cancel left the treatment active");
        }
        if (!MedicalActionService.start(v, id, LimbType.TORSO, -1)) {
            helper.fail("after a cancel the player could not start again -- they are locked out");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void swappingTheItemMidCastAbortsTheTreatment(GameTestHelper helper) {
        // The slot is recorded at the start; if the stack changes underneath, applying anyway would let a
        // player treat with an item they no longer have.
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        Trauma cut = wound(helper, v, LimbType.TORSO, "laceration_large", 0.8F);
        if (cut == null) {
            return;
        }
        ResourceLocation id = hold(v, ModItems.BANDAGE.get());
        MedicalActionService.start(v, id, LimbType.TORSO, -1);

        v.getInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.STONE));
        complete(helper, v);

        if (profile.hasActiveTreatment()) {
            helper.fail("the treatment was neither applied nor cancelled");
        }
        if (cut.getBleedFactor() != 1.0F) {
            helper.fail("the bandage was applied although it had been swapped away");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anItemYouAreNotCarryingCannotBeUsed(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        wound(helper, v, LimbType.TORSO, "laceration_large", 0.8F);
        if (MedicalActionService.start(v, idOf(ModItems.BANDAGE.get()), LimbType.TORSO, -1)) {
            helper.fail("a bandage was used out of an empty inventory");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anOrdinaryItemIsNotATreatment(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        hold(v, net.minecraft.world.item.Items.STONE);
        if (MedicalActionService.start(v, idOf(net.minecraft.world.item.Items.STONE), null, -1)) {
            helper.fail("a stone block started a medical treatment");
        }
        if (MedicalActionService.start(v, null, null, -1)) {
            helper.fail("a null item id started a treatment");
        }
        if (MedicalActionService.start(null, idOf(ModItems.BANDAGE.get()), null, -1)) {
            helper.fail("a null actor started a treatment");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aTourniquetIsAppliedImmediatelyRatherThanCast(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        hold(v, ModItems.TOURNIQUET.get());

        if (!MedicalActionService.start(v, idOf(ModItems.TOURNIQUET.get()), LimbType.RIGHT_LEG, -1)) {
            helper.fail("the tourniquet was refused");
            return;
        }
        if (!profile.limb(LimbType.RIGHT_LEG).hasTourniquet()) {
            helper.fail("the tourniquet did not go on");
        }
        if (profile.hasActiveTreatment()) {
            helper.fail("a tourniquet should not leave a cast in flight");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aTourniquetCanBeCutOffAgain(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        profile.limb(LimbType.RIGHT_LEG).setTourniquet(true);

        if (!MedicalActionService.removeTourniquet(v, LimbType.RIGHT_LEG, -1)) {
            helper.fail("the tourniquet could not be removed");
        }
        if (profile.limb(LimbType.RIGHT_LEG).hasTourniquet()) {
            helper.fail("the tourniquet is still on");
        }
        if (MedicalActionService.removeTourniquet(v, LimbType.RIGHT_LEG, -1)) {
            helper.fail("removing a tourniquet that is not there reported success");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aTreatmentThatChangesNothingDoesNotEatTheItem(GameTestHelper helper) {
        // A bandage used on an uninjured limb: the service still completes, but consuming the item for
        // nothing is the kind of quiet loss players notice and cannot explain.
        TestBodies.Victim v = TestBodies.victim(helper);
        ResourceLocation id = hold(v, ModItems.BANDAGE.get());
        int before = v.getInventory().getItem(0).getCount();

        MedicalActionService.start(v, id, LimbType.TORSO, -1);
        complete(helper, v);

        if (v.getInventory().getItem(0).getCount() != before) {
            helper.fail("a no-op treatment consumed the item");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aDownedPlayerCannotTreatAnyone(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        wound(helper, v, LimbType.TORSO, "laceration_large", 0.8F);
        hold(v, ModItems.BANDAGE.get());

        profile.setOverdoseUnconscious(true);
        profile.recompute(MedicalConfig.toPhysiologyParams());

        if (MedicalActionService.start(v, idOf(ModItems.BANDAGE.get()), LimbType.TORSO, -1)) {
            helper.fail("an unconscious player bandaged themselves");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anInjectableRunsThroughTheSameCastAndDosesTheUser(GameTestHelper helper) {
        if (!MedicalConfig.enableInjectables()) {
            helper.succeed();
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        ResourceLocation id = hold(v, ModItems.MORPHINE_SYRINGE.get());

        if (!MedicalActionService.start(v, id, null, -1)) {
            helper.fail("the syringe was refused");
            return;
        }
        complete(helper, v);

        if (profile.getDrugLoad() <= 0.0F) {
            helper.fail("the syringe completed without dosing anyone");
        }
        if (v.getInventory().getItem(0).getCount() != 1) {
            helper.fail("the syringe was not consumed");
        }
        if (MedicalAttachments.get(v).getRevision() == 0) {
            helper.fail("nothing was marked for resync");
        }
        helper.succeed();
    }
}
