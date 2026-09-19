package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.compat.wfballistics.WfBallisticsArmorCompat;
import com.warfactory.medical.core.damage.ArmorEvaluation;
import com.warfactory.medical.core.limb.LimbType;
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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The hand-off to WF-Ballistics' armour resolver.
 *
 * <p>What is being pinned is that there is exactly one reduction step with one owner. Before this, both
 * mods reduced the same hit: WF-Ballistics summed DT and DR over every worn piece regardless of where
 * the hit landed, and this mod separately rolled dice off vanilla's armour attributes. Two identical
 * shots on identical armour could produce two different injuries.
 *
 * <p><b>Nothing here names a WF-Ballistics type.</b> Items come out of the registry by id and the
 * resolver is reached through {@code WfBallisticsArmorCompat}, so this class loads whether or not the
 * mod is installed. That is deliberate: a gametest holder that cannot be loaded takes the whole scan
 * with it.
 *
 * <p>Only the first test runs in the default configuration, where WF-Ballistics is absent and all it can
 * check is that the fallback really is the fallback. Run the suite with {@code -PwithBallistics} to put
 * the mod on the classpath and exercise the rest.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class WfBallisticsArmorGameTest {

    private static final String TEMPLATE = "empty";
    private static final String BALLISTICS = "wfballistics";

    /** A vest: DT 2 and DR 10% against a bullet, which is most of what soft armour is worth. */
    private static final ResourceLocation PLATE_CARRIER =
            ResourceLocation.fromNamespaceAndPath(BALLISTICS, "plate_carrier");
    private static final ResourceKey<DamageType> KINETIC =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(BALLISTICS, "physical"));

    /**
     * The guard says what is actually there, and says nothing when it is not. This is the one test that
     * means something in both configurations.
     */
    @GameTest(template = TEMPLATE)
    public static void theBridgeAgreesWithTheModList(GameTestHelper helper) {
        boolean present = ModList.get().isLoaded(BALLISTICS);
        if (present != WfBallisticsArmorCompat.isLoaded()) {
            helper.fail("mod list says " + present + " but the compat says "
                    + WfBallisticsArmorCompat.isLoaded());
            return;
        }
        TestBodies.Victim victim = TestBodies.victim(helper);
        if (!present) {
            if (WfBallisticsArmorCompat.resolve(victim, LimbType.TORSO,
                    helper.getLevel().damageSources().cactus(), 10.0F, false) != null) {
                helper.fail("the compat answered without the mod behind it");
                return;
            }
            helper.succeed();
            return;
        }
        if (!WfBallisticsArmorCompat.claimedPlayers()) {
            helper.fail("WF-Ballistics was never told this mod owns players, so both will reduce the hit");
            return;
        }
        helper.succeed();
    }

    /**
     * The three outcomes, read off the arithmetic rather than rolled for. Same armour, three hits, three
     * different and entirely predictable answers: under the threshold is stopped, over it but heavily cut
     * is reduced, and far over it is barely touched.
     */
    @GameTest(template = TEMPLATE)
    public static void theOutcomeComesOffTheArithmeticNotTheDice(GameTestHelper helper) {
        if (!WfBallisticsArmorCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        TestBodies.Victim victim = TestBodies.victim(helper);
        victim.setItemSlot(EquipmentSlot.CHEST, carrier(helper));
        DamageSource rifle = kinetic(helper);

        if (!outcomeIs(helper, victim, rifle, 1.5F, ArmorEvaluation.Outcome.BLOCKED)) {
            return;
        }
        if (!outcomeIs(helper, victim, rifle, 30.0F, ArmorEvaluation.Outcome.PARTIAL)) {
            return;
        }
        if (!outcomeIs(helper, victim, rifle, 100.0F, ArmorEvaluation.Outcome.FULL)) {
            return;
        }

        // Ten identical hits, ten identical answers. The old evaluation could not say that.
        for (int i = 0; i < 10; i++) {
            if (!outcomeIs(helper, victim, rifle, 30.0F, ArmorEvaluation.Outcome.PARTIAL)) {
                return;
            }
        }

        // DT 2, then 10% off the remaining 28.
        float through = WfBallisticsArmorCompat.resolve(victim, LimbType.TORSO, rifle, 30.0F, false).through();
        if (Math.abs(through - 25.2F) > 1.0E-3F) {
            helper.fail("the wound energy should be what got past the vest, not what was aimed at it: "
                    + through);
            return;
        }
        helper.succeed();
    }

    /** Arms are covered by the chestplate and legs are not, so the answer has to differ between them. */
    @GameTest(template = TEMPLATE)
    public static void itAsksAboutThePieceOnTheLimbThatWasHit(GameTestHelper helper) {
        if (!WfBallisticsArmorCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        TestBodies.Victim victim = TestBodies.victim(helper);
        victim.setItemSlot(EquipmentSlot.CHEST, carrier(helper));
        DamageSource rifle = kinetic(helper);

        WfBallisticsArmorCompat.Resolved torso =
                WfBallisticsArmorCompat.resolve(victim, LimbType.TORSO, rifle, 1.5F, false);
        WfBallisticsArmorCompat.Resolved leg =
                WfBallisticsArmorCompat.resolve(victim, LimbType.LEFT_LEG, rifle, 1.5F, false);
        if (torso.outcome() != ArmorEvaluation.Outcome.BLOCKED) {
            helper.fail("the vest should have stopped a 1.5 hit to the chest: " + torso.outcome());
            return;
        }
        if (leg.outcome() != ArmorEvaluation.Outcome.FULL || leg.through() != 1.5F) {
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
    @GameTest(template = TEMPLATE)
    public static void absorbingTheHitIsWhatCostsTheVest(GameTestHelper helper) {
        if (!WfBallisticsArmorCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        TestBodies.Victim victim = TestBodies.victim(helper);
        ItemStack vest = carrier(helper);
        victim.setItemSlot(EquipmentSlot.CHEST, vest);
        DamageSource rifle = kinetic(helper);

        WfBallisticsArmorCompat.resolve(victim, LimbType.TORSO, rifle, 30.0F, false);
        ItemStack worn = victim.getItemBySlot(EquipmentSlot.CHEST);
        if (worn.getDamageValue() != 0) {
            helper.fail("a dry run charged the vest " + worn.getDamageValue() + " anyway");
            return;
        }
        WfBallisticsArmorCompat.resolve(victim, LimbType.TORSO, rifle, 30.0F, true);
        if (worn.getDamageValue() != 3) {
            helper.fail("expected 3 durability spent on 4.8 absorbed, got " + worn.getDamageValue());
            return;
        }
        helper.succeed();
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private static boolean outcomeIs(GameTestHelper helper, TestBodies.Victim victim, DamageSource source,
                                     float amount, ArmorEvaluation.Outcome expected) {
        WfBallisticsArmorCompat.Resolved got =
                WfBallisticsArmorCompat.resolve(victim, LimbType.TORSO, source, amount, false);
        if (got == null) {
            helper.fail("the compat declined a hit while the mod is loaded");
            return false;
        }
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
            helper.fail("WF-Ballistics is loaded but " + PLATE_CARRIER + " is not registered");
        }
        return stack;
    }

    private static DamageSource kinetic(GameTestHelper helper) {
        Holder<DamageType> type = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(KINETIC);
        return new DamageSource(type);
    }
}
