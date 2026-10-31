package com.warfactory.medical.damage.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.damage.ArmorResolution;
import com.warfactory.medical.body.gametest.TestBodies;
import com.warfactory.medical.body.limb.LimbType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.wf.wflib.armor.api.ArmorOutcome;
import com.wf.wflib.armor.api.ArmorResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** One armour reduction step with one owner: wflib per-limb resolution for players this mod claims. */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmorHandoffGameTest {

    private static final String TEMPLATE = "empty";
    private static final String BALLISTICS = "wflib";

    /** A vest: DT 2 and DR 10% against a bullet, which is most of what soft armour is worth. */
    private static final ResourceLocation PLATE_CARRIER =
            ResourceLocation.fromNamespaceAndPath(BALLISTICS, "plate_carrier");
    private static final ResourceKey<DamageType> KINETIC =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(BALLISTICS, "physical"));

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void thisModOwnsPlayerArmourResolution(GameTestHelper helper) {
        helper.assertTrue(com.wf.wflib.armor.api.ArmorResolution.PLAYER_RESOLUTION.get(),
                "wflib was never told this mod owns players, so both reduce the hit");
        helper.succeed();
    }

    /**
     * The three outcomes, read off the arithmetic rather than rolled for. Same armour, three hits, three
     * different and entirely predictable answers: under the threshold is stopped, over it but heavily cut
     * is reduced, and far over it is barely touched.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void theOutcomeComesOffTheArithmeticNotTheDice(GameTestHelper helper) {
        TestBodies.Victim victim = TestBodies.victim(helper);
        victim.setItemSlot(EquipmentSlot.CHEST, carrier(helper));
        DamageSource rifle = kinetic(helper);

        if (!outcomeIs(helper, victim, rifle, 1.5F, ArmorOutcome.STOPPED)) {
            return;
        }
        if (!outcomeIs(helper, victim, rifle, 30.0F, ArmorOutcome.REDUCED)) {
            return;
        }
        if (!outcomeIs(helper, victim, rifle, 100.0F, ArmorOutcome.THROUGH)) {
            return;
        }

        // Ten identical hits, ten identical answers. The old evaluation could not say that.
        for (int i = 0; i < 10; i++) {
            if (!outcomeIs(helper, victim, rifle, 30.0F, ArmorOutcome.REDUCED)) {
                return;
            }
        }

        // DT 2, then 10% off the remaining 28.
        float through = (float) ArmorResolution.resolve(victim, LimbType.TORSO, rifle, 30.0F, false).through();
        if (Math.abs(through - 25.2F) > 1.0E-3F) {
            helper.fail("the wound energy should be what got past the vest, not what was aimed at it: "
                    + through);
            return;
        }
        helper.succeed();
    }

    /** Arms are covered by the chestplate and legs are not, so the answer has to differ between them. */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void itAsksAboutThePieceOnTheLimbThatWasHit(GameTestHelper helper) {
        TestBodies.Victim victim = TestBodies.victim(helper);
        victim.setItemSlot(EquipmentSlot.CHEST, carrier(helper));
        DamageSource rifle = kinetic(helper);

        ArmorResult torso =
                ArmorResolution.resolve(victim, LimbType.TORSO, rifle, 1.5F, false);
        ArmorResult leg =
                ArmorResolution.resolve(victim, LimbType.LEFT_LEG, rifle, 1.5F, false);
        if (torso.outcome() != ArmorOutcome.STOPPED) {
            helper.fail("the vest should have stopped a 1.5 hit to the chest: " + torso.outcome());
            return;
        }
        if (leg.outcome() != ArmorOutcome.THROUGH || leg.through() != 1.5F) {
            helper.fail("a chest plate protected a bare leg: " + leg.outcome() + " / " + leg.through());
            return;
        }
        helper.succeed();
    }

    /**
     * Durability is spent on what the armour absorbed, and it is this mod's per-limb call that spends it.
     * A vest with hardness 1 that ate 4.8 of a 30-point hit owes 3.85 damage of condition, which is 385
     * points, which is three whole durability points and 85 over.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void absorbingTheHitIsWhatCostsTheVest(GameTestHelper helper) {
        TestBodies.Victim victim = TestBodies.victim(helper);
        ItemStack vest = carrier(helper);
        victim.setItemSlot(EquipmentSlot.CHEST, vest);
        DamageSource rifle = kinetic(helper);

        ArmorResolution.resolve(victim, LimbType.TORSO, rifle, 30.0F, false);
        ItemStack worn = victim.getItemBySlot(EquipmentSlot.CHEST);
        if (worn.getDamageValue() != 0) {
            helper.fail("a dry run charged the vest " + worn.getDamageValue() + " anyway");
            return;
        }
        ArmorResolution.resolve(victim, LimbType.TORSO, rifle, 30.0F, true);
        if (worn.getDamageValue() != 3) {
            helper.fail("expected 3 durability spent on 4.8 absorbed, got " + worn.getDamageValue());
            return;
        }
        helper.succeed();
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private static boolean outcomeIs(GameTestHelper helper, TestBodies.Victim victim, DamageSource source,
                                     float amount, ArmorOutcome expected) {
        ArmorResult got =
                ArmorResolution.resolve(victim, LimbType.TORSO, source, amount, false);
        if (got.outcome() != expected) {
            helper.fail(amount + " against a plate carrier read as " + got.outcome()
                    + ", expected " + expected);
            return false;
        }
        return true;
    }

    private static ItemStack carrier(GameTestHelper helper) {
        Item item = BuiltInRegistries.ITEM.get(PLATE_CARRIER);
        ItemStack stack = new ItemStack(item);
        // BuiltInRegistries.get hands back the registry default rather than null for an unknown id, so
        // an absent item arrives as an empty stack rather than as an exception.
        if (stack.isEmpty()) {
            helper.fail("WFLib is loaded but " + PLATE_CARRIER + " is not registered");
        }
        return stack;
    }

    private static DamageSource kinetic(GameTestHelper helper) {
        Holder<DamageType> type = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(KINETIC);
        return new DamageSource(type);
    }
}
