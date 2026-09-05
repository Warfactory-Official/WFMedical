package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.damage.DamageCategory;
import com.warfactory.medical.core.damage.DamageClassifier;
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

/**
 * The tag-driven half of damage classification, against the real damage-type registry.
 *
 * <p>{@code DamageClassifier} asks {@code source.is(DamageTypeTags.IS_FIRE)} and friends before it falls
 * back to name matching. A {@code Holder.direct} type -- all a unit test can build -- answers {@code false}
 * to every tag, so those branches are only reachable here. That matters because the tags are datapack
 * content: a vanilla retag, or a pack that drops a type out of {@code is_fire}, silently reclassifies
 * damage with no compile error and no crash.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class DamageClassifierGameTest {

    private static final String TEMPLATE = "empty";

    private static DamageSource of(GameTestHelper helper, ResourceKey<DamageType> key) {
        Holder<DamageType> type = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
        return new DamageSource(type);
    }

    private static void expect(GameTestHelper helper, ResourceKey<DamageType> key, DamageCategory want) {
        DamageSource src = of(helper, key);
        DamageCategory got = DamageClassifier.classify(src);
        if (got != want) {
            helper.fail(key.location() + " classified as " + got + ", expected " + want);
        }
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theseSourcesAreActuallyRegistryBacked(GameTestHelper helper) {
        // Guard: if these resolved to direct holders, every tag branch below would be skipped and the
        // suite would silently be re-testing the name heuristics the unit test already covers.
        DamageSource fall = of(helper, DamageTypes.FALL);
        if (fall.typeHolder().unwrapKey().isEmpty()) {
            helper.fail("the damage source is not registry-backed, so no tag can match");
        }
        if (!fall.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            helper.fail("minecraft:fall is not in the is_fall tag -- the datapack is not loaded");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void fallsAreClassifiedByTag(GameTestHelper helper) {
        expect(helper, DamageTypes.FALL, DamageCategory.FALL);
        expect(helper, DamageTypes.STALAGMITE, DamageCategory.FALL);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void burningSourcesAreClassifiedByTag(GameTestHelper helper) {
        expect(helper, DamageTypes.IN_FIRE, DamageCategory.FIRE);
        expect(helper, DamageTypes.ON_FIRE, DamageCategory.FIRE);
        expect(helper, DamageTypes.LAVA, DamageCategory.FIRE);
        expect(helper, DamageTypes.HOT_FLOOR, DamageCategory.FIRE);
        expect(helper, DamageTypes.FIREBALL, DamageCategory.FIRE);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void blastsAreClassifiedByTag(GameTestHelper helper) {
        expect(helper, DamageTypes.EXPLOSION, DamageCategory.EXPLOSION);
        expect(helper, DamageTypes.PLAYER_EXPLOSION, DamageCategory.EXPLOSION);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void projectilesArePiercing(GameTestHelper helper) {
        expect(helper, DamageTypes.ARROW, DamageCategory.PIERCING);
        expect(helper, DamageTypes.TRIDENT, DamageCategory.PIERCING);
        expect(helper, DamageTypes.MOB_PROJECTILE, DamageCategory.PIERCING);
        expect(helper, DamageTypes.THROWN, DamageCategory.PIERCING);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void impactsAreBlunt(GameTestHelper helper) {
        expect(helper, DamageTypes.FALLING_ANVIL, DamageCategory.BLUNT);
        expect(helper, DamageTypes.FALLING_BLOCK, DamageCategory.BLUNT);
        expect(helper, DamageTypes.FLY_INTO_WALL, DamageCategory.BLUNT);
        expect(helper, DamageTypes.CRAMMING, DamageCategory.BLUNT);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void sharpEnvironmentalSourcesAreSlashing(GameTestHelper helper) {
        expect(helper, DamageTypes.CACTUS, DamageCategory.SLASHING);
        expect(helper, DamageTypes.STING, DamageCategory.SLASHING);
        expect(helper, DamageTypes.SWEET_BERRY_BUSH, DamageCategory.SLASHING);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anArmedAttackerSlashesAndABareOnePunches(GameTestHelper helper) {
        // The only difference between SLASHING and UNARMED is what the attacker is holding, and getting
        // that backwards means every punch opens a bleeding cut.
        Holder<DamageType> type = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.PLAYER_ATTACK);

        TestBodies.Victim bare = TestBodies.victim(helper);
        bare.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        DamageCategory punch = DamageClassifier.classify(new DamageSource(type, bare, bare));
        if (punch != DamageCategory.UNARMED) {
            helper.fail("an empty-handed attack classified as " + punch);
        }

        TestBodies.Victim armed = TestBodies.victim(helper);
        armed.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        DamageCategory cut = DamageClassifier.classify(new DamageSource(type, armed, armed));
        if (cut != DamageCategory.SLASHING) {
            helper.fail("a sword attack classified as " + cut);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anAttackWithNoAttackerIsStillAnAttack(GameTestHelper helper) {
        // getEntity() is null for a command-dealt attack; isUnarmed must not NPE and the fallback is a cut.
        DamageCategory got = DamageClassifier.classify(of(helper, DamageTypes.PLAYER_ATTACK));
        if (got != DamageCategory.SLASHING) {
            helper.fail("an attacker-less player attack classified as " + got);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void mobAttacksAreClassifiedLikePlayerAttacks(GameTestHelper helper) {
        expect(helper, DamageTypes.MOB_ATTACK, DamageCategory.SLASHING);
        expect(helper, DamageTypes.MOB_ATTACK_NO_AGGRO, DamageCategory.SLASHING);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void unmodelledSourcesStayGeneric(GameTestHelper helper) {
        // Drowning, starvation and the void are handled elsewhere (or not at all); they must not be
        // dressed up as a wound category, or every hunger tick would open a laceration.
        expect(helper, DamageTypes.DROWN, DamageCategory.GENERIC);
        expect(helper, DamageTypes.STARVE, DamageCategory.GENERIC);
        expect(helper, DamageTypes.FELL_OUT_OF_WORLD, DamageCategory.GENERIC);
        expect(helper, DamageTypes.GENERIC_KILL, DamageCategory.GENERIC);
        expect(helper, DamageTypes.WITHER, DamageCategory.GENERIC);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void everyVanillaDamageTypeClassifiesWithoutThrowing(GameTestHelper helper) {
        // Sweep the whole registry: classify reads tags, keys and message ids and each lookup is wrapped
        // in its own try/catch, so a type that trips one of them would otherwise only surface in play.
        var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        int seen = 0;
        for (Holder<DamageType> holder : registry.listElements().toList()) {
            DamageCategory got = DamageClassifier.classify(new DamageSource(holder));
            if (got == null) {
                helper.fail("classify returned null for " + holder.getRegisteredName());
            }
            seen++;
        }
        if (seen < 40) {
            helper.fail("only " + seen + " damage types in the registry -- the sweep is not covering much");
        }
        helper.succeed();
    }
}
