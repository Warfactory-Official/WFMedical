package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.attachment.IMedicalData;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.substance.Substance;
import com.warfactory.medical.core.substance.SubstanceRegistry;
import com.warfactory.medical.server.SubstanceService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Injectables: analgesia, stimulants, overdose and the antidote that reverses them.
 *
 * <p>{@code SubstanceService.inject} recomputes physiology, applies mob effects and sends a full sync, so
 * it needs a real server player rather than a bare profile. The drug bookkeeping it performs is what the
 * downed/overdose state machine reads, and getting the antidote wrong -- leaving a player sealed
 * unconscious with no way back -- is unrecoverable in play.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubstanceServiceGameTest {

    private static final String TEMPLATE = "empty";

    private static Substance morphine() {
        Substance live = SubstanceRegistry.active().get(SubstanceRegistry.MORPHINE_ITEM_ID);
        return live != null ? live : SubstanceRegistry.defaultMorphine();
    }

    private static Substance naloxone() {
        Substance live = SubstanceRegistry.active().get(SubstanceRegistry.NALOXONE_ITEM_ID);
        return live != null ? live : SubstanceRegistry.defaultNaloxone();
    }

    private static Substance stimulant() {
        Substance live = SubstanceRegistry.active().get(SubstanceRegistry.COMBAT_STIMULANT_ITEM_ID);
        return live != null ? live : SubstanceRegistry.defaultCombatStimulant();
    }

    private static boolean injectablesOff(GameTestHelper helper) {
        if (!MedicalConfig.enableInjectables()) {
            helper.succeed();
            return true;
        }
        return false;
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void morphineSuppressesPainAndAddsDrugLoad(GameTestHelper helper) {
        if (injectablesOff(helper)) {
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);

        if (!SubstanceService.inject(v, morphine())) {
            helper.fail("the injection was refused");
            return;
        }
        if (profile.getPainSuppression() <= 0.0F) {
            helper.fail("morphine suppressed no pain");
        }
        if (profile.getDrugLoad() <= 0.0F) {
            helper.fail("morphine added no drug load, so an overdose could never accumulate");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void repeatedDosesAccumulateTowardsAnOverdose(GameTestHelper helper) {
        if (injectablesOff(helper)) {
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        Substance m = morphine();

        SubstanceService.inject(v, m);
        float afterOne = profile.getDrugLoad();
        SubstanceService.inject(v, m);
        if (profile.getDrugLoad() <= afterOne) {
            helper.fail("a second dose did not stack: " + afterOne + " -> " + profile.getDrugLoad());
        }

        // Enough doses to clear the threshold must leave a mark: either the blackout grace timer has
        // started or the player is already out.
        for (int i = 0; i < 8; i++) {
            SubstanceService.inject(v, m);
        }
        if (profile.getDrugLoad() < m.overdoseThreshold()) {
            helper.fail("ten doses did not reach the overdose threshold: " + profile.getDrugLoad());
        }
        boolean reacted = profile.isOverdoseUnconscious() || profile.getBlackoutGraceUntil() > 0L
                || profile.isAsphyxiating();
        if (!reacted) {
            helper.fail("the overdose threshold was crossed with no consequence at all");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aStimulantSetsATimedBuffRatherThanAPermanentOne(GameTestHelper helper) {
        if (injectablesOff(helper)) {
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        long now = helper.getLevel().getGameTime();

        if (!SubstanceService.inject(v, stimulant())) {
            helper.fail("the stimulant was refused");
            return;
        }
        if (profile.getStimulant() <= 0.0F) {
            helper.fail("no stimulant strength was applied");
        }
        if (profile.getStimulantEndTick() <= now) {
            helper.fail("the stimulant has no expiry, so its speed bonus would never wear off");
        }
        if (profile.getClottingBoost() > 0.0F && profile.getClottingBoostEndTick() <= now) {
            helper.fail("the clotting side effect has no expiry");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anAntidoteClearsTheDrugAndTheAnalgesia(GameTestHelper helper) {
        if (injectablesOff(helper)) {
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);

        SubstanceService.inject(v, morphine());
        SubstanceService.inject(v, morphine());
        float loaded = profile.getDrugLoad();
        if (loaded <= 0.0F) {
            helper.fail("nothing to reverse; the doses did not land");
            return;
        }

        if (!SubstanceService.inject(v, naloxone())) {
            helper.fail("the antidote was refused");
            return;
        }
        if (profile.getDrugLoad() >= loaded) {
            helper.fail("the antidote did not lower the drug load: " + loaded
                    + " -> " + profile.getDrugLoad());
        }
        if (profile.getPainSuppression() != 0.0F) {
            helper.fail("the antidote must strip the analgesia along with the drug");
        }
        if (profile.getDrugLoad() < 0.0F) {
            helper.fail("the drug load went negative");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anAntidoteEndsAStimulantEarly(GameTestHelper helper) {
        if (injectablesOff(helper)) {
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        SubstanceService.inject(v, stimulant());
        if (profile.getStimulant() <= 0.0F) {
            helper.fail("the stimulant did not land");
            return;
        }
        SubstanceService.inject(v, naloxone());
        if (profile.getStimulant() != 0.0F || profile.getStimulantEndTick() != 0L) {
            helper.fail("the stimulant survived the antidote: strength=" + profile.getStimulant()
                    + " end=" + profile.getStimulantEndTick());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anAntidoteOnAnOverdosedPlayerLatchesThemUnconsciousRatherThanSnappingThemAwake(
            GameTestHelper helper) {
        // Reversing the chemistry clears the cause, but waking instantly on the jab would make an
        // overdose weightless. The latch hands the player to the normal wake-up path instead.
        if (injectablesOff(helper)) {
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        profile.setOverdoseUnconscious(true);

        SubstanceService.inject(v, naloxone());

        if (profile.isOverdoseUnconscious()) {
            helper.fail("the overdose flag survived the antidote");
        }
        if (!profile.isUnconsciousLatched()) {
            helper.fail("an antidote must not snap an overdosed player straight back onto their feet");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anAntidoteOnASoberPlayerLeavesThemStanding(GameTestHelper helper) {
        // Guard: the latch above must depend on having been sealed, not fire on every antidote.
        if (injectablesOff(helper)) {
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        SubstanceService.inject(v, naloxone());
        if (profile.isUnconsciousLatched()) {
            helper.fail("an antidote knocked out a player who was fine");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void injectingBumpsTheRevisionSoTheClientSeesIt(GameTestHelper helper) {
        if (injectablesOff(helper)) {
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        IMedicalData data = MedicalAttachments.get(v);
        int before = data.getRevision();
        SubstanceService.inject(v, morphine());
        if (data.getRevision() <= before) {
            helper.fail("the injection did not bump the revision");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void missingArgumentsAreRefusedRatherThanThrowing(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        if (SubstanceService.inject(null, morphine())) {
            helper.fail("a null player was injected");
        }
        if (SubstanceService.inject(v, null)) {
            helper.fail("a null substance was injected");
        }
        if (SubstanceService.inject(v, v, null, morphine())) {
            helper.fail("a null data handle was injected");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aMedicCanInjectSomeoneElseAndTheEffectsLandOnThePatient(GameTestHelper helper) {
        if (injectablesOff(helper)) {
            return;
        }
        TestBodies.Victim medic = TestBodies.victim(helper);
        TestBodies.Victim patient = TestBodies.victim(helper);
        MedicalProfile medicProfile = TestBodies.profileOf(helper, medic);
        MedicalProfile patientProfile = TestBodies.profileOf(helper, patient);

        if (!SubstanceService.inject(medic, patient, MedicalAttachments.get(patient), morphine())) {
            helper.fail("the medic's injection was refused");
            return;
        }
        if (patientProfile.getDrugLoad() <= 0.0F) {
            helper.fail("the patient received nothing");
        }
        if (medicProfile.getDrugLoad() != 0.0F || medicProfile.getPainSuppression() != 0.0F) {
            helper.fail("the medic dosed themselves instead of the patient");
        }
        helper.succeed();
    }
}
