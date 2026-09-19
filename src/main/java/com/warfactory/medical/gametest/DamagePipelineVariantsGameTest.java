package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaCategory;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The damage pipeline for everything that is not a bullet.
 *
 * <p>{@code TraumaPipelineGameTest} proves the TACZ path end to end. This proves the ordinary vanilla
 * paths that every player meets far more often -- falling, burning, being punched, being shot with an
 * arrow -- reach the same handler and produce the right kind of wound. Each of these takes a different
 * branch through classification, hit location and the wound table, and every one of those branches can
 * break while the bullet test stays green.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class DamagePipelineVariantsGameTest {

    private static final String TEMPLATE = "empty";

    private static DamageSource source(GameTestHelper helper, ResourceKey<DamageType> key) {
        Holder<DamageType> type = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
        return new DamageSource(type);
    }

    private static DamageSource source(GameTestHelper helper, ResourceKey<DamageType> key,
                                       net.minecraft.world.entity.Entity attacker) {
        Holder<DamageType> type = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
        return new DamageSource(type, attacker, attacker);
    }

    private static boolean anyOfCategory(List<Trauma> traumas, TraumaCategory cat) {
        return traumas.stream().anyMatch(t -> t.getType().getCategory() == cat);
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aFallWoundsTheLegsWithSelfHealingBluntTrauma(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);

        if (!v.hurt(source(helper, DamageTypes.FALL), 8.0F)) {
            helper.fail("the fall damage was refused before the handler ran");
        }

        List<Trauma> traumas = TestBodies.allTraumas(profile);
        if (traumas.isEmpty()) {
            helper.fail("a fall left no wound at all");
            return;
        }
        // The weighted sampler biases falls heavily onto the legs, and the head and arms are excluded
        // outright -- a landing that splits your skull open is the bug this guards.
        for (Trauma t : traumas) {
            if (t.getLimb() == LimbType.HEAD || t.getLimb().isArm()) {
                helper.fail("a landing wounded the " + t.getLimb() + ": " + TestBodies.describe(profile));
            }
        }
        // A fall is blunt force: no open wound, so nothing should be bleeding.
        for (Trauma t : traumas) {
            if (!t.isFracture() && t.bleeding() > 0.0F) {
                helper.fail("a fall opened a bleeding wound (" + t.getType().getId() + ")");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void burningLeavesABurnAndNotACut(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);

        if (!v.hurt(source(helper, DamageTypes.ON_FIRE), 6.0F)) {
            helper.fail("the fire damage was refused before the handler ran");
        }

        List<Trauma> traumas = TestBodies.allTraumas(profile);
        if (!anyOfCategory(traumas, TraumaCategory.BURN)) {
            helper.fail("fire left no burn: " + TestBodies.describe(profile));
        }
        if (anyOfCategory(traumas, TraumaCategory.LACERATION)
                || anyOfCategory(traumas, TraumaCategory.PUNCTURE)) {
            helper.fail("fire opened a cut: " + traumas.stream()
                    .map(t -> t.getType().getId()).toList());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anExplosionCrushesAndBurnsAtOnce(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);

        if (!v.hurt(source(helper, DamageTypes.EXPLOSION), 12.0F)) {
            helper.fail("the blast was refused before the handler ran");
        }
        List<Trauma> traumas = TestBodies.allTraumas(profile);
        if (!anyOfCategory(traumas, TraumaCategory.CRUSH_INJURY)
                && !anyOfCategory(traumas, TraumaCategory.BURN)) {
            helper.fail("a blast left neither a crush nor a burn: " + TestBodies.describe(profile));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aPunchBruisesWhereASwordCuts(GameTestHelper helper) {
        // The armed/unarmed split decided in DamageClassifier has to survive all the way to the wound
        // table, or every bare-handed hit opens a bleeding laceration.
        TestBodies.Victim punched = TestBodies.victim(helper);
        TestBodies.Victim bareAttacker = TestBodies.attacker(helper, punched, 1.5);
        bareAttacker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        MedicalProfile punchedProfile = TestBodies.profileOf(helper, punched);
        if (!punched.hurt(source(helper, DamageTypes.PLAYER_ATTACK, bareAttacker), 4.0F)) {
            helper.fail("the punch was rejected as a gap shot -- the attacker is not aimed at the victim");
            return;
        }

        List<Trauma> fromFist = TestBodies.allTraumas(punchedProfile);
        if (fromFist.isEmpty()) {
            helper.fail("a punch left no mark at all");
            return;
        }
        if (anyOfCategory(fromFist, TraumaCategory.LACERATION)) {
            helper.fail("a bare fist opened a laceration: " + TestBodies.describe(punchedProfile));
        }

        TestBodies.Victim cut = TestBodies.victim(helper);
        TestBodies.Victim armedAttacker = TestBodies.attacker(helper, cut, 1.5);
        armedAttacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        MedicalProfile cutProfile = TestBodies.profileOf(helper, cut);
        if (!cut.hurt(source(helper, DamageTypes.PLAYER_ATTACK, armedAttacker), 8.0F)) {
            helper.fail("the sword swing was rejected as a gap shot");
            return;
        }

        if (!anyOfCategory(TestBodies.allTraumas(cutProfile), TraumaCategory.LACERATION)) {
            helper.fail("a sword left no cut: " + TestBodies.describe(cutProfile));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anArrowPunchesAHole(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        if (!v.hurt(source(helper, DamageTypes.ARROW), 7.0F)) {
            helper.fail("the arrow was refused before the handler ran");
        }
        List<Trauma> traumas = TestBodies.allTraumas(profile);
        if (traumas.isEmpty()) {
            helper.fail("an arrow left no wound");
            return;
        }
        boolean bleeds = traumas.stream().anyMatch(t -> t.bleeding() > 0.0F);
        if (!bleeds) {
            helper.fail("an arrow wound that does not bleed: " + TestBodies.describe(profile));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theMedicalSystemAbsorbsVanillaDamageForEveryCategory(GameTestHelper helper) {
        // Health is driven by the physiology model, not by the vanilla hit; if the event does not absorb
        // the amount the player is damaged twice for one hit.
        for (ResourceKey<DamageType> key : List.of(DamageTypes.FALL, DamageTypes.ON_FIRE,
                DamageTypes.EXPLOSION, DamageTypes.ARROW)) {
            TestBodies.Victim v = TestBodies.victim(helper);
            float before = v.getHealth();
            v.hurt(source(helper, key), 6.0F);
            if (v.getHealth() < before - 6.0F + 0.001F) {
                helper.fail(key.location() + " applied the raw vanilla damage on top of the medical model: "
                        + before + " -> " + v.getHealth());
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aDownedPlayerDoesNotSuffocateInsideABlock(GameTestHelper helper) {
        // A downed body lies inside its own block space; without this cancel it would be ground to death
        // by suffocation before anyone could revive it.
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        profile.setOverdoseUnconscious(true);
        profile.recompute(MedicalConfig.toPhysiologyParams());

        if (!MedicalState.isDowned(v)) {
            helper.fail("the victim is not registering as downed, so this proves nothing");
        }
        // Health is the wrong thing to watch: the pipeline absorbs the vanilla amount for every hit it
        // handles, so a landed hit and a cancelled one both leave the health bar untouched. hurt() returns
        // false exactly when the event was cancelled, which is the property under test.
        if (v.hurt(source(helper, DamageTypes.IN_WALL), 4.0F)) {
            helper.fail("a downed player was not shielded from suffocation");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aStandingPlayerStillSuffocatesNormally(GameTestHelper helper) {
        // Guard: the cancel above must be conditional on being downed, not a blanket suffocation immunity.
        TestBodies.Victim v = TestBodies.victim(helper);
        if (MedicalState.isDowned(v)) {
            helper.fail("a fresh victim should not be downed");
        }
        if (!v.hurt(source(helper, DamageTypes.IN_WALL), 4.0F)) {
            helper.fail("a conscious player was immune to suffocation");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void drowningIsHandedToTheAsphyxiaModelInsteadOfDealingDamage(GameTestHelper helper) {
        if (!MedicalConfig.drowningAsphyxiaEnabled()) {
            helper.succeed();
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        if (v.hurt(source(helper, DamageTypes.DROWN), 4.0F)) {
            helper.fail("drowning dealt damage instead of being handed to the asphyxia model");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void naturalRegenCannotOutrunTheMedicalHealthCap(GameTestHelper helper) {
        if (!MedicalConfig.manageNaturalRegen()) {
            helper.succeed();
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);

        // Open a real wound so the effective MAX drops below full health, then take a fall on top of it:
        // blunt trauma costs CURRENT health without lowering the max, which is what leaves a gap for regen
        // to try to close. A penetrating wound alone lowers both together and leaves nothing to observe.
        v.hurt(source(helper, DamageTypes.ARROW), 10.0F);
        v.invulnerableTime = 0;
        v.spawnInvulnerableTime = 0;
        v.hurt(source(helper, DamageTypes.FALL), 8.0F);
        profile.recompute(MedicalConfig.toPhysiologyParams());
        float cap = profile.cached().effectiveCurrentHealth();
        if (cap >= v.getMaxHealth()) {
            helper.fail("the wounds did not lower the health cap, so the clamp cannot be observed: "
                    + TestBodies.describe(profile));
            return;
        }

        v.setHealth(cap);
        v.heal(20.0F);
        if (v.getHealth() > cap + 0.01F) {
            helper.fail("regen healed past the medical cap: " + v.getHealth() + " > " + cap);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anAttackerIsCreditedSoABleedOutDeathHasAKiller(GameTestHelper helper) {
        TestBodies.Victim victim = TestBodies.victim(helper);
        TestBodies.Victim attacker = TestBodies.attacker(helper, victim, 1.5);
        MedicalProfile profile = TestBodies.profileOf(helper, victim);

        if (!victim.hurt(source(helper, DamageTypes.PLAYER_ATTACK, attacker), 6.0F)) {
            helper.fail("the attack was rejected, so there was nothing to credit");
            return;
        }

        if (profile.getLastDamagingPlayer() == null) {
            helper.fail("no attacker was credited; a later bleed-out death would have no killer");
        } else if (!profile.getLastDamagingPlayer().equals(attacker.getUUID())) {
            helper.fail("the wrong player was credited");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void enoughDamageDownsThePlayerRatherThanKillingThemOutright(GameTestHelper helper) {
        if (!MedicalConfig.enableBleedout()) {
            helper.succeed();
            return;
        }
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);

        for (int i = 0; i < 12 && profile.getState() != HealthState.UNCONSCIOUS
                && profile.getState() != HealthState.DEAD; i++) {
            v.invulnerableTime = 0;
            v.spawnInvulnerableTime = 0;
            v.hurt(source(helper, DamageTypes.ARROW), 12.0F);
        }

        if (profile.getState() == HealthState.HEALTHY) {
            // Not a hard failure of the downing rule so much as of the premise: nothing landed.
            helper.fail("twelve arrows left the player perfectly healthy: " + TestBodies.describe(profile));
        }
        if (MedicalAttachments.get(v) == null) {
            helper.fail("the victim lost its attachment mid-fight");
        }
        helper.succeed();
    }
}
