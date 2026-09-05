package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.attachment.IMedicalData;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The data attachment: who carries medical state, and whether it survives a respawn.
 *
 * <p>Attachments are created lazily for <em>any</em> entity, so {@code MedicalAttachments.get} has to
 * re-impose the old capability's attach condition itself. Getting that wrong in the permissive direction
 * gives every zombie and item frame a blood volume and a per-tick physiology update; getting it wrong in
 * the restrictive direction makes players untrackable.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class MedicalAttachmentGameTest {

    private static final String TEMPLATE = "empty";

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aPlayerCarriesMedicalState(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        if (!MedicalAttachments.isEligible(v)) {
            helper.fail("a player must be eligible");
        }
        IMedicalData data = MedicalAttachments.get(v);
        if (data == null || data.getProfile() == null) {
            helper.fail("a player got no medical data");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void ordinaryMobsAndObjectsDoNot(GameTestHelper helper) {
        for (EntityType<?> type : new EntityType<?>[]{EntityType.ZOMBIE, EntityType.PIG,
                EntityType.ARMOR_STAND, EntityType.ITEM, EntityType.ARROW}) {
            Entity e = type.create(helper.getLevel());
            if (e == null) {
                continue;
            }
            if (MedicalAttachments.isEligible(e)) {
                helper.fail(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type)
                        + " is eligible for medical state -- every one of these would get a per-tick "
                        + "physiology update");
            }
            if (MedicalAttachments.get(e) != null) {
                helper.fail("get() returned data for an ineligible entity");
            }
        }
        if (MedicalAttachments.isEligible(null) || MedicalAttachments.get(null) != null) {
            helper.fail("a null entity must not be eligible");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theSameEntityAlwaysGetsTheSameData(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        IMedicalData first = MedicalAttachments.get(v);
        first.getProfile().setBloodMl(1234.0D);
        IMedicalData second = MedicalAttachments.get(v);
        if (second.getProfile().getBloodMl() != 1234.0D) {
            helper.fail("a second get() handed back a fresh profile, losing the first one's state");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void copyCarriesTheProfileAcrossARespawn(GameTestHelper helper) {
        // PlayerEvent.Clone hands a brand-new player object on respawn and on a dimension change; without
        // this copy every wound and the blood volume silently reset.
        TraumaRegistry registry = TraumaRegistry.active();
        TraumaType type = registry.get("laceration_large");
        if (type == null) {
            helper.fail("the active registry has no laceration_large -- definitions did not load");
            return;
        }

        TestBodies.Victim original = TestBodies.victim(helper);
        MedicalProfile before = MedicalAttachments.get(original).getProfile();
        before.setBloodMl(2500.0D);
        before.addTrauma(LimbType.LEFT_LEG, new Trauma(type, LimbType.LEFT_LEG, 0.7F, 0L));

        TestBodies.Victim clone = TestBodies.victim(helper);
        MedicalAttachments.copy(original, clone);

        MedicalProfile after = MedicalAttachments.get(clone).getProfile();
        if (Math.abs(after.getBloodMl() - 2500.0D) > 1.0e-6) {
            helper.fail("blood was not carried across: " + after.getBloodMl());
        }
        if (after.limb(LimbType.LEFT_LEG).getTraumas().size() != 1) {
            helper.fail("the leg wound was lost on respawn: " + TestBodies.describe(after));
        }
        if (!MedicalAttachments.get(clone).needsSync()) {
            helper.fail("a freshly cloned player must be resynced, or their HUD stays blank");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void copyIsADeepCopyNotASharedProfile(GameTestHelper helper) {
        TestBodies.Victim original = TestBodies.victim(helper);
        TestBodies.Victim clone = TestBodies.victim(helper);
        MedicalAttachments.get(original).getProfile().setBloodMl(2500.0D);
        MedicalAttachments.copy(original, clone);

        MedicalAttachments.get(clone).getProfile().setBloodMl(100.0D);
        if (MedicalAttachments.get(original).getProfile().getBloodMl() != 2500.0D) {
            helper.fail("the two players share a profile object; bleeding one would bleed the other");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void copyingToOrFromAnIneligibleEntityIsANoOp(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalAttachments.copy(null, v);
        MedicalAttachments.copy(v, null);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theShippedDefinitionsAreLoadedOnTheServer(GameTestHelper helper) {
        // Guard for the whole in-world suite: with an empty active registry every wound the pipeline
        // generates resolves to null and quietly disappears, and the trauma assertions elsewhere would
        // fail for a reason that has nothing to do with the code under test.
        TraumaRegistry registry = TraumaRegistry.active();
        if (registry.size() == 0) {
            helper.fail("the active trauma registry is empty -- MedicalDefinitions never ran");
        }
        for (String id : new String[]{"puncture", "laceration_large", "internal_bleeding", "fracture"}) {
            if (registry.get(id) == null) {
                helper.fail("the active registry is missing " + id);
            }
        }
        if (com.warfactory.medical.core.substance.SubstanceRegistry.active().size() == 0) {
            helper.fail("no substances registered -- every syringe would be inert");
        }
        helper.succeed();
    }
}
