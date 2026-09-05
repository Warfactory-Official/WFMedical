package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.damage.ArmorEvaluation;
import com.warfactory.medical.core.damage.DamageCategory;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
import java.util.Map;

/**
 * Armour mitigation: whether a hit is stopped outright, partially stopped, or lands in full.
 *
 * <p>This needs a real {@link LivingEntity} for its attribute map and equipment slots, so it cannot be a
 * unit test. The armour <em>value</em> is set on the attribute directly rather than by equipping a
 * chestplate and waiting for vanilla's equipment plumbing to run -- a {@link TestBodies.Victim} never
 * ticks, so an equipped plate would contribute nothing and every armour assertion would compare 0 to 0.
 * Equipment is still used, but only for what {@code ArmorEvaluation} reads off the stack directly:
 * the per-limb durability factor.
 *
 * <p>The outcome is a dice roll, so every test below either pins the roll or measures a rate over a
 * fixed-seed sample. None of them assert on a single random outcome.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmorEvaluationGameTest {

    private static final String TEMPLATE = "empty";
    private static final int SAMPLES = 4000;

    private static TestBodies.Victim armoured(GameTestHelper helper, double armor, double toughness) {
        TestBodies.Victim v = TestBodies.victim(helper);
        set(v, Attributes.ARMOR, armor);
        set(v, Attributes.ARMOR_TOUGHNESS, toughness);
        return v;
    }

    private static void set(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                            double value) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    /** Outcome counts over a fixed-seed sample, so a rate is reproducible rather than flaky. */
    private static Map<ArmorEvaluation.Outcome, Integer> sample(LivingEntity victim, LimbType limb,
                                                                DamageCategory cat, float amount) {
        RandomSource rand = RandomSource.create(0xC0FFEEL);
        Map<ArmorEvaluation.Outcome, Integer> counts = new EnumMap<>(ArmorEvaluation.Outcome.class);
        for (ArmorEvaluation.Outcome o : ArmorEvaluation.Outcome.values()) {
            counts.put(o, 0);
        }
        for (int i = 0; i < SAMPLES; i++) {
            ArmorEvaluation.Outcome o = ArmorEvaluation.evaluate(victim, limb, cat, amount, rand);
            counts.put(o, counts.get(o) + 1);
        }
        return counts;
    }

    /** Fraction of hits the armour did anything about. */
    private static double stopRate(LivingEntity victim, LimbType limb, DamageCategory cat, float amount) {
        Map<ArmorEvaluation.Outcome, Integer> c = sample(victim, limb, cat, amount);
        return (c.get(ArmorEvaluation.Outcome.BLOCKED) + c.get(ArmorEvaluation.Outcome.PARTIAL))
                / (double) SAMPLES;
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anUnarmouredHitAlwaysLandsInFull(GameTestHelper helper) {
        TestBodies.Victim v = armoured(helper, 0.0D, 0.0D);
        Map<ArmorEvaluation.Outcome, Integer> c = sample(v, LimbType.TORSO, DamageCategory.BALLISTIC, 8.0F);
        if (c.get(ArmorEvaluation.Outcome.FULL) != SAMPLES) {
            helper.fail("bare skin stopped a bullet: " + c);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void armourActuallyChangesTheOutcome(GameTestHelper helper) {
        // Guard: without this, every rate comparison below could be comparing zero to zero.
        TestBodies.Victim bare = armoured(helper, 0.0D, 0.0D);
        TestBodies.Victim plated = armoured(helper, 20.0D, 12.0D);
        double bareRate = stopRate(bare, LimbType.TORSO, DamageCategory.BALLISTIC, 8.0F);
        double platedRate = stopRate(plated, LimbType.TORSO, DamageCategory.BALLISTIC, 8.0F);
        if (bareRate != 0.0D) {
            helper.fail("unarmoured stop rate should be exactly zero, was " + bareRate);
        }
        if (platedRate <= 0.05D) {
            helper.fail("full armour barely mitigated anything: " + platedRate);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void moreArmourStopsMore(GameTestHelper helper) {
        double light = stopRate(armoured(helper, 6.0D, 0.0D), LimbType.TORSO, DamageCategory.SLASHING, 6.0F);
        double heavy = stopRate(armoured(helper, 20.0D, 12.0D), LimbType.TORSO, DamageCategory.SLASHING, 6.0F);
        if (heavy <= light) {
            helper.fail("more armour did not stop more: light=" + light + " heavy=" + heavy);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void armourIsWeakestAgainstBulletsAndStrongestAgainstBluntForce(GameTestHelper helper) {
        // categoryEffectiveness: BALLISTIC 0.45 < PIERCING 0.6 < EXPLOSION 0.7 < SLASHING 1.0 < BLUNT 1.2.
        // This ordering is the whole reason a plate carrier is not a bullet-proof vest in this mod.
        TestBodies.Victim v = armoured(helper, 20.0D, 12.0D);
        double ballistic = stopRate(v, LimbType.TORSO, DamageCategory.BALLISTIC, 8.0F);
        double piercing = stopRate(v, LimbType.TORSO, DamageCategory.PIERCING, 8.0F);
        double explosion = stopRate(v, LimbType.TORSO, DamageCategory.EXPLOSION, 8.0F);
        double slashing = stopRate(v, LimbType.TORSO, DamageCategory.SLASHING, 8.0F);
        double blunt = stopRate(v, LimbType.TORSO, DamageCategory.BLUNT, 8.0F);

        if (!(ballistic < piercing && piercing < explosion && explosion < slashing && slashing < blunt)) {
            helper.fail("category effectiveness is out of order: ballistic=" + ballistic
                    + " piercing=" + piercing + " explosion=" + explosion
                    + " slashing=" + slashing + " blunt=" + blunt);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void armourNeverStopsFireChemicalRadiationOrAFall(GameTestHelper helper) {
        TestBodies.Victim v = armoured(helper, 20.0D, 12.0D);
        v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        for (DamageCategory cat : new DamageCategory[]{DamageCategory.FIRE, DamageCategory.CHEMICAL,
                DamageCategory.RADIATION, DamageCategory.FALL}) {
            Map<ArmorEvaluation.Outcome, Integer> c = sample(v, LimbType.TORSO, cat, 8.0F);
            if (c.get(ArmorEvaluation.Outcome.FULL) != SAMPLES) {
                helper.fail("a plate carrier mitigated " + cat + ": " + c);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aHeavierHitPunchesThroughMoreOften(GameTestHelper helper) {
        // The load penalty: mitigation is scaled down by amount/(amount+12), so armour that reliably stops
        // a light blow is much less use against a heavy one.
        TestBodies.Victim v = armoured(helper, 20.0D, 12.0D);
        double light = stopRate(v, LimbType.TORSO, DamageCategory.SLASHING, 2.0F);
        double heavy = stopRate(v, LimbType.TORSO, DamageCategory.SLASHING, 60.0F);
        if (heavy >= light) {
            helper.fail("armour held up as well against a heavy hit: light=" + light + " heavy=" + heavy);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aBrokenPieceProtectsLessThanAFreshOne(GameTestHelper helper) {
        // Same attribute armour on both, so the only difference is the stack's remaining durability --
        // which is exactly the term this test is isolating.
        TestBodies.Victim fresh = armoured(helper, 12.0D, 4.0D);
        fresh.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));

        TestBodies.Victim worn = armoured(helper, 12.0D, 4.0D);
        ItemStack battered = new ItemStack(Items.IRON_CHESTPLATE);
        battered.setDamageValue(battered.getMaxDamage() - 1);
        worn.setItemSlot(EquipmentSlot.CHEST, battered);

        double freshRate = stopRate(fresh, LimbType.TORSO, DamageCategory.SLASHING, 6.0F);
        double wornRate = stopRate(worn, LimbType.TORSO, DamageCategory.SLASHING, 6.0F);
        if (freshRate <= wornRate) {
            helper.fail("a nearly-broken plate protected as well as a new one: fresh=" + freshRate
                    + " worn=" + wornRate);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void eachLimbReadsItsOwnArmourSlot(GameTestHelper helper) {
        // A helmet and nothing else: the head is covered, the legs are not. Both limbs see the same
        // attribute armour, so any difference has to come from the slot mapping.
        TestBodies.Victim v = armoured(helper, 12.0D, 4.0D);
        v.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));

        double head = stopRate(v, LimbType.HEAD, DamageCategory.SLASHING, 6.0F);
        double leg = stopRate(v, LimbType.LEFT_LEG, DamageCategory.SLASHING, 6.0F);
        if (head <= leg) {
            helper.fail("the helmet did not favour the head: head=" + head + " leg=" + leg);
        }

        // Arms are covered by the chestplate, so a chest piece must help an arm hit.
        TestBodies.Victim chested = armoured(helper, 12.0D, 4.0D);
        chested.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        double arm = stopRate(chested, LimbType.RIGHT_ARM, DamageCategory.SLASHING, 6.0F);
        double bareArm = stopRate(armoured(helper, 12.0D, 4.0D), LimbType.RIGHT_ARM,
                DamageCategory.SLASHING, 6.0F);
        if (arm <= bareArm) {
            helper.fail("a chestplate did not cover the arms: with=" + arm + " without=" + bareArm);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anUndamageableArmourPieceCountsAsFullyIntact(GameTestHelper helper) {
        TestBodies.Victim v = armoured(helper, 12.0D, 4.0D);
        // Not an armour item and not damageable: pieceDurabilityFactor short-circuits to 1.0 rather than
        // dividing by a zero max damage.
        v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.STONE));
        double withStone = stopRate(v, LimbType.TORSO, DamageCategory.SLASHING, 6.0F);
        double empty = stopRate(armoured(helper, 12.0D, 4.0D), LimbType.TORSO,
                DamageCategory.SLASHING, 6.0F);
        if (withStone <= empty) {
            helper.fail("an indestructible chest slot should count as intact: " + withStone + " vs " + empty);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void mitigationIsCappedSoArmourIsNeverTotal(GameTestHelper helper) {
        // The clamp is 0.95, and BLOCKED needs roll < mitigation^2. Even absurd armour must leave a gap.
        TestBodies.Victim v = armoured(helper, 1000.0D, 1000.0D);
        v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        Map<ArmorEvaluation.Outcome, Integer> c = sample(v, LimbType.TORSO, DamageCategory.BLUNT, 1.0F);
        if (c.get(ArmorEvaluation.Outcome.FULL) + c.get(ArmorEvaluation.Outcome.PARTIAL) == 0) {
            helper.fail("armour became absolute: " + c);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void missingArgumentsFallBackToAnUnmitigatedHit(GameTestHelper helper) {
        if (ArmorEvaluation.evaluate(null, LimbType.TORSO, DamageCategory.BALLISTIC, 5.0F,
                RandomSource.create(1L)) != ArmorEvaluation.Outcome.FULL) {
            helper.fail("a null victim should not be armoured");
        }
        if (ArmorEvaluation.evaluate(TestBodies.victim(helper), LimbType.TORSO, DamageCategory.BALLISTIC,
                5.0F, null) != ArmorEvaluation.Outcome.FULL) {
            helper.fail("a null RandomSource should not be armoured");
        }
        helper.succeed();
    }
}
