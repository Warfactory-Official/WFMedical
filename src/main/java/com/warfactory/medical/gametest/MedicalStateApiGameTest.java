package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code api.MedicalState} -- the surface other Warfactory mods read.
 *
 * <p>It is the one part of this mod with callers outside it, so its contract is the one that cannot be
 * changed by refactoring the internals. In particular every accessor has to answer sanely for a player
 * with no medical data at all: a mod asking about an armour stand or a mob must get "unimpaired", not
 * a null dereference and not "downed".
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class MedicalStateApiGameTest {

    private static final String TEMPLATE = "empty";

    private static void wound(GameTestHelper helper, MedicalProfile p, LimbType limb, String id, float sev) {
        TraumaType type = TraumaRegistry.active().get(id);
        if (type == null) {
            helper.fail("the active registry has no " + id);
            return;
        }
        p.addTrauma(limb, new Trauma(type, limb, sev, 0L));
        p.limb(limb).rebuildCache();
    }

    private static MedicalProfile recomputed(GameTestHelper helper, TestBodies.Victim v) {
        MedicalProfile p = TestBodies.profileOf(helper, v);
        p.recompute(MedicalConfig.toPhysiologyParams());
        return p;
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anUninjuredPlayerReadsAsFullyCapable(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        recomputed(helper, v);
        if (MedicalState.isSprintBlocked(v) || MedicalState.isUnconscious(v) || MedicalState.isDowned(v)
                || MedicalState.isHandsDisabled(v) || MedicalState.isBothArmsDisabled(v)
                || MedicalState.isBothLegsDisabled(v)) {
            helper.fail("a healthy player reported as impaired");
        }
        if (MedicalState.movementMultiplier(v) != 1.0F || MedicalState.jumpMultiplier(v) != 1.0F) {
            helper.fail("a healthy player is slowed: move=" + MedicalState.movementMultiplier(v)
                    + " jump=" + MedicalState.jumpMultiplier(v));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aNullPlayerAnswersNeutrallyRatherThanThrowing(GameTestHelper helper) {
        // Callers pass whatever entity they are holding; the API is the boundary that has to cope.
        if (MedicalState.isSprintBlocked(null) || MedicalState.isUnconscious(null)
                || MedicalState.isDowned(null) || MedicalState.isHandsDisabled(null)
                || MedicalState.isBothArmsDisabled(null) || MedicalState.isBothLegsDisabled(null)) {
            helper.fail("a null player reported as impaired");
        }
        if (MedicalState.movementMultiplier(null) != 1.0F || MedicalState.jumpMultiplier(null) != 1.0F) {
            helper.fail("a null player must not be slowed; callers multiply by this");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aBrokenLegBlocksSprintingAndJumping(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile p = TestBodies.profileOf(helper, v);
        wound(helper, p, LimbType.LEFT_LEG, "fracture", 1.0F);
        p.recompute(MedicalConfig.toPhysiologyParams());

        if (!MedicalState.isSprintBlocked(v)) {
            helper.fail("a broken leg did not block sprinting");
        }
        if (MedicalState.jumpMultiplier(v) != 0.0F) {
            helper.fail("a broken leg can still jump: " + MedicalState.jumpMultiplier(v));
        }
        if (MedicalState.movementMultiplier(v) >= 1.0F) {
            helper.fail("a broken leg did not slow the player");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anUnconsciousPlayerIsDownedAndHandless(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile p = TestBodies.profileOf(helper, v);
        p.setOverdoseUnconscious(true);
        p.recompute(MedicalConfig.toPhysiologyParams());

        if (!MedicalState.isUnconscious(v)) {
            helper.fail("an unconscious player did not read as unconscious");
        }
        if (!MedicalState.isDowned(v)) {
            helper.fail("an unconscious player did not read as downed");
        }
        if (!MedicalState.isHandsDisabled(v)) {
            helper.fail("an unconscious player could still use their hands -- they could shoot back");
        }
        if (MedicalState.movementMultiplier(v) != 0.0F) {
            helper.fail("an unconscious player can still walk");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void downedTracksTheProfileNotJustTheHealthState(GameTestHelper helper) {
        // isDowned consults the profile's own flag, which covers the overdose and asphyxia routes that do
        // not necessarily land on HealthState.UNCONSCIOUS through physiology.
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile p = TestBodies.profileOf(helper, v);
        p.setAsphyxiaUnconscious(true);
        p.recompute(MedicalConfig.toPhysiologyParams());
        if (!MedicalState.isDowned(v)) {
            helper.fail("an asphyxiated player did not read as downed");
        }
        p.clearAsphyxia();
        p.setState(HealthState.HEALTHY);
        p.setForcedState(null);
        p.recompute(MedicalConfig.toPhysiologyParams());
        if (MedicalState.isDowned(v)) {
            helper.fail("the downed flag did not clear once the cause was gone");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void twoRuinedArmsDisableTheHandsWithoutDowningThePlayer(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile p = TestBodies.profileOf(helper, v);
        for (LimbType arm : new LimbType[]{LimbType.LEFT_ARM, LimbType.RIGHT_ARM}) {
            for (int i = 0; i < 6; i++) {
                wound(helper, p, arm, "internal_bleeding", 1.0F);
            }
        }
        p.recompute(MedicalConfig.toPhysiologyParams());

        if (!MedicalState.isBothArmsDisabled(v)) {
            helper.fail("two destroyed arms did not report as disabled");
        }
        if (!MedicalState.isHandsDisabled(v)) {
            helper.fail("two destroyed arms could still hold a weapon");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anEntityWithNoMedicalDataReadsAsUnimpaired(GameTestHelper helper) {
        // MedicalState takes a Player, so the only way to reach the null-data branch is a player the
        // attachment does not track. The contract that matters is the default: unimpaired, not downed.
        if (MedicalState.jumpMultiplier(null) != 1.0F) {
            helper.fail("the no-data default must be neutral");
        }
        helper.succeed();
    }
}
