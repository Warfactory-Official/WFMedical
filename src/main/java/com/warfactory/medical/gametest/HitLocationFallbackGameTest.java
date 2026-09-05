package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.damage.DamageCategory;
import com.warfactory.medical.core.damage.HitLocation;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The weighted limb fallback, used whenever a hit has no reconstructable direction -- a command kill, a
 * damage-over-time tick, an environmental source, or a modded projectile the geometry cannot trace.
 *
 * <p>{@code LimbRigGameTest} and the original {@code HitLocationGameTest} cover the geometric path. This
 * covers what happens when that path returns nothing, which in practice is a large share of all damage.
 * The distribution is the behaviour: a fall that breaks an arm, or a headshot-weighted bias that never
 * actually favours the head, are both invisible except across many samples.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class HitLocationFallbackGameTest {

    private static final String TEMPLATE = "empty";
    private static final int SAMPLES = 6000;

    /**
     * A damage source with neither an attacker nor a projectile, so {@code HitGeometry} cannot
     * reconstruct a hit point and {@code pick} is forced onto the weighted sampler.
     */
    private static DamageSource directionless(GameTestHelper helper) {
        Holder<DamageType> type = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GENERIC);
        return new DamageSource(type);
    }

    private static Map<LimbType, Integer> distribution(GameTestHelper helper, DamageCategory cat) {
        DamageSource src = directionless(helper);
        TestBodies.Victim v = TestBodies.victim(helper);
        RandomSource rand = RandomSource.create(0x5EEDL);
        Map<LimbType, Integer> counts = new EnumMap<>(LimbType.class);
        for (LimbType lt : LimbType.VALUES) {
            counts.put(lt, 0);
        }
        for (int i = 0; i < SAMPLES; i++) {
            LimbType picked = HitLocation.pick(v, src, cat, rand);
            if (picked == null) {
                helper.fail("pick returned null for " + cat);
                return counts;
            }
            counts.put(picked, counts.get(picked) + 1);
        }
        return counts;
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aDirectionlessHitReallyDoesFallBackToWeightedSampling(GameTestHelper helper) {
        // Guard: if the geometric path were still answering, every distribution below would be measuring
        // the rig instead of the weights and the category-bias tests would be meaningless.
        Map<LimbType, Integer> counts = distribution(helper, DamageCategory.GENERIC);
        int distinct = 0;
        for (int n : counts.values()) {
            if (n > 0) {
                distinct++;
            }
        }
        if (distinct < 5) {
            helper.fail("only " + distinct + " limbs were ever picked -- this is not a weighted sample: "
                    + counts);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aFallLandsOnTheLegsAndNeverOnTheHeadOrArms(GameTestHelper helper) {
        Map<LimbType, Integer> counts = distribution(helper, DamageCategory.FALL);
        if (counts.get(LimbType.HEAD) != 0 || counts.get(LimbType.LEFT_ARM) != 0
                || counts.get(LimbType.RIGHT_ARM) != 0) {
            helper.fail("a landing broke an arm or split a head open: " + counts);
        }
        int legs = counts.get(LimbType.LEFT_LEG) + counts.get(LimbType.RIGHT_LEG);
        if (legs < SAMPLES * 0.8) {
            helper.fail("a fall should overwhelmingly hit the legs, got " + legs + "/" + SAMPLES
                    + ": " + counts);
        }
        if (counts.get(LimbType.TORSO) == 0) {
            helper.fail("the torso should still be reachable on a bad landing: " + counts);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void gunfireFavoursCentreMassAndTheHeadOverTheLimbs(GameTestHelper helper) {
        Map<LimbType, Integer> ballistic = distribution(helper, DamageCategory.BALLISTIC);
        Map<LimbType, Integer> generic = distribution(helper, DamageCategory.GENERIC);

        double ballisticTorso = ballistic.get(LimbType.TORSO) / (double) SAMPLES;
        double genericTorso = generic.get(LimbType.TORSO) / (double) SAMPLES;
        if (ballisticTorso <= genericTorso) {
            helper.fail("ballistic did not bias towards the torso: " + ballisticTorso
                    + " vs generic " + genericTorso);
        }

        double ballisticHead = ballistic.get(LimbType.HEAD) / (double) SAMPLES;
        double genericHead = generic.get(LimbType.HEAD) / (double) SAMPLES;
        if (ballisticHead <= genericHead) {
            helper.fail("ballistic did not bias towards the head: " + ballisticHead
                    + " vs generic " + genericHead);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anExplosionFavoursTheLimbsOverTheVitals(GameTestHelper helper) {
        // Blast fragments catch extremities; that is the inverse of the ballistic bias.
        Map<LimbType, Integer> blast = distribution(helper, DamageCategory.EXPLOSION);
        Map<LimbType, Integer> generic = distribution(helper, DamageCategory.GENERIC);
        double blastVital = (blast.get(LimbType.HEAD) + blast.get(LimbType.TORSO)) / (double) SAMPLES;
        double genericVital = (generic.get(LimbType.HEAD) + generic.get(LimbType.TORSO)) / (double) SAMPLES;
        if (blastVital >= genericVital) {
            helper.fail("an explosion should hit vitals less often than a generic hit: "
                    + blastVital + " vs " + genericVital);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theSamplerIsDeterministicForAGivenSeed(GameTestHelper helper) {
        // A reproducible pick is what makes a hit-location bug reportable at all.
        DamageSource src = directionless(helper);
        TestBodies.Victim v = TestBodies.victim(helper);
        for (int seed = 0; seed < 20; seed++) {
            LimbType a = HitLocation.pick(v, src, DamageCategory.GENERIC, RandomSource.create(seed));
            LimbType b = HitLocation.pick(v, src, DamageCategory.GENERIC, RandomSource.create(seed));
            if (a != b) {
                helper.fail("seed " + seed + " gave " + a + " then " + b);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void withNoRandomSourceThePickIsTheTorso(GameTestHelper helper) {
        LimbType picked = HitLocation.pick(TestBodies.victim(helper), directionless(helper),
                DamageCategory.GENERIC, null);
        if (picked != LimbType.TORSO) {
            helper.fail("the no-randomness fallback should be centre mass, got " + picked);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aNullVictimStillYieldsALimb(GameTestHelper helper) {
        // Damage can arrive for an entity the geometry cannot pose at all; the sampler still has to answer.
        LimbType picked = HitLocation.pick(null, directionless(helper), DamageCategory.BALLISTIC,
                RandomSource.create(7L));
        if (picked == null) {
            helper.fail("pick returned null with no victim");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void pickPiercedAlwaysReturnsAtLeastOneLimb(GameTestHelper helper) {
        // Callers iterate the result without an emptiness check; an empty list is a silently dropped hit.
        TestBodies.Victim v = TestBodies.victim(helper);
        DamageSource src = directionless(helper);
        for (DamageCategory cat : DamageCategory.values()) {
            List<LimbType> pierced = HitLocation.pickPierced(v, src, cat, RandomSource.create(3L));
            if (pierced.isEmpty()) {
                helper.fail("pickPierced returned nothing for " + cat);
                return;
            }
            for (LimbType lt : pierced) {
                if (lt == null) {
                    helper.fail("pickPierced returned a null limb for " + cat);
                    return;
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void everyCategoryCanBeSampledWithoutThrowing(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        DamageSource src = directionless(helper);
        RandomSource rand = RandomSource.create(11L);
        for (DamageCategory cat : DamageCategory.values()) {
            for (int i = 0; i < 200; i++) {
                if (HitLocation.pick(v, src, cat, rand) == null) {
                    helper.fail("pick returned null for " + cat);
                    return;
                }
            }
        }
        helper.succeed();
    }
}
