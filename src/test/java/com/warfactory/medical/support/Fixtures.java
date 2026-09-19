package com.warfactory.medical.support;

import com.warfactory.medical.config.MedicalDefinitions;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.PhysiologyParams;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.substance.SubstanceRegistry;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import com.warfactory.medical.core.treatment.Treatment;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

/** Shared builders for the unit suite: the shipped trauma/treatment definitions, and posed profiles. */
public final class Fixtures {

    private Fixtures() {
    }

    /**
     * A registry holding the hardcoded defaults from {@link MedicalDefinitions#loadDefaults}, which are what
     * ship when no {@code wfmedical_definitions.toml} is present. Tests assert against these ids, so a rename
     * upstream surfaces here rather than silently degrading into {@code firstOfCategory} fallbacks.
     */
    public static TraumaRegistry registry() {
        TraumaRegistry r = new TraumaRegistry();
        MedicalDefinitions.loadDefaults(r, new HashMap<>(), new SubstanceRegistry());
        return r;
    }

    /** The shipped item -> treatment table (bandage, splint, suture kit, medkit, ...). */
    public static Map<String, Treatment> treatments() {
        Map<String, Treatment> t = new HashMap<>();
        MedicalDefinitions.loadDefaults(new TraumaRegistry(), t, new SubstanceRegistry());
        return t;
    }

    public static SubstanceRegistry substances() {
        SubstanceRegistry s = new SubstanceRegistry();
        MedicalDefinitions.loadDefaults(new TraumaRegistry(), new HashMap<>(), s);
        return s;
    }

    public static PhysiologyParams params() {
        return PhysiologyParams.defaults();
    }

    /** The same params with the circulation model toggled, for testing the deceleration in isolation. */
    public static PhysiologyParams paramsWith(PhysiologyParams base, boolean cardiacOutputEnabled) {
        return copy(base, base.bleedoutEnabled(), base.torsoDepletionInstakill(), cardiacOutputEnabled,
                base.heartRateEnabled());
    }

    /** The same params with bleeding out toggled: off means a lethal condition kills outright. */
    public static PhysiologyParams paramsWithBleedout(PhysiologyParams base, boolean bleedoutEnabled) {
        return copy(base, bleedoutEnabled, base.torsoDepletionInstakill(), base.cardiacOutputEnabled(),
                base.heartRateEnabled());
    }

    /** The same params with a destroyed torso set to kill outright rather than down the player. */
    public static PhysiologyParams paramsWithTorsoInstakill(PhysiologyParams base, boolean instakill) {
        return copy(base, base.bleedoutEnabled(), instakill, base.cardiacOutputEnabled(),
                base.heartRateEnabled());
    }

    /** The same params with the heart rate toggled; off pins it at resting, as before it was modelled. */
    public static PhysiologyParams paramsWithHeartRate(PhysiologyParams base, boolean heartRateEnabled) {
        return copy(base, base.bleedoutEnabled(), base.torsoDepletionInstakill(), base.cardiacOutputEnabled(),
                heartRateEnabled);
    }

    /**
     * The one place in the test tree that spells out every component of {@link PhysiologyParams}, so growing
     * the record is a one-site edit rather than a hunt through the suites.
     */
    private static PhysiologyParams copy(PhysiologyParams base, boolean bleedoutEnabled, boolean torsoInstakill,
                                         boolean cardiacOutputEnabled, boolean heartRateEnabled) {
        return new PhysiologyParams(
                base.maxHealthPoints(), base.maxBloodMl(), base.bloodLowFraction(),
                base.bloodCriticalFraction(), base.bloodDeathMl(), base.painShockThreshold(),
                base.painMaxHealthPenalty(), base.legFractureSpeedMultiplier(), base.painSpeedFloor(),
                bleedoutEnabled, base.bleedoutTicks(), base.bloodDeathLossFraction(),
                base.bloodUnconsciousLossFraction(), base.painUnconsciousThreshold(),
                base.painUnconsciousWeight(), base.bloodMovementPenaltyLossFraction(),
                base.painShareHead(), base.painShareTorso(), base.painShareArm(), base.painShareLeg(),
                base.painSaturationK(), base.adrenalineEnabled(), base.asphyxiaMoveMultiplier(),
                base.stimulantSpeedBonus(), base.healthShareHead(), base.healthShareTorso(),
                base.healthShareArm(), base.healthShareLeg(), base.tourniquetBleedMultiplier(),
                base.tourniquetLegSpeedMultiplier(), base.tourniquetArmSpeedMultiplier(),
                base.headDepletionInstakill(), torsoInstakill,
                base.bleedingRateMultiplier(), cardiacOutputEnabled,
                base.cardiacVenousReturnFloor(), base.cardiacOutputFloor(),
                heartRateEnabled, base.heartRateResting(), base.heartRateMax(),
                base.heartRateBleedInfluence(), base.heartRateCompensationRatio(),
                base.heartRateDecompensationRatio(), base.heartRatePainThreshold(),
                base.heartRatePainGain(), base.heartRateStimulantBonus(), base.heartRateOpioidDrop());
    }

    /** A fresh, uninjured profile at full blood. */
    public static MedicalProfile profile() {
        return new MedicalProfile(params().maxBloodMl());
    }

    /** Add a trauma of {@code id} at {@code severity} to {@code limb}, and return it. */
    public static Trauma wound(MedicalProfile p, TraumaRegistry r, LimbType limb, String id, float severity) {
        TraumaType type = r.getOrThrow(id);
        Trauma t = new Trauma(type, limb, severity, 0L);
        p.addTrauma(limb, t);
        return t;
    }

    /**
     * A fixed-seed RandomSource. Every test that feeds randomness into production code uses one of these so a
     * failure is reproducible; tests that need a specific branch pin the outcome instead of relying on odds.
     */
    public static RandomSource rand(long seed) {
        return RandomSource.create(seed);
    }

    /** Always-lowest RandomSource: makes every {@code nextFloat() < chance} branch taken (for chance > 0). */
    public static RandomSource alwaysRolls() {
        return new FixedRandom(0.0F);
    }

    /** Always-highest RandomSource: makes every chance branch missed. */
    public static RandomSource neverRolls() {
        return new FixedRandom(0.999999F);
    }

    /**
     * A RandomSource pinned to one value, so a probabilistic branch can be tested as a decision rather than
     * as a distribution. Only the float/double draws the production code actually uses are overridden; the
     * rest inherit a real generator so an unexpected new draw still behaves sanely.
     */
    private static final class FixedRandom implements RandomSource {
        private final float value;
        private final RandomSource delegate = RandomSource.create(1234L);

        private FixedRandom(float value) {
            this.value = value;
        }

        @Override
        public RandomSource fork() {
            return this;
        }

        @Override
        public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() {
            return delegate.forkPositional();
        }

        @Override
        public void setSeed(long seed) {
        }

        @Override
        public int nextInt() {
            return delegate.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            return delegate.nextInt(bound);
        }

        @Override
        public long nextLong() {
            return delegate.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            return value < 0.5F;
        }

        @Override
        public float nextFloat() {
            return value;
        }

        @Override
        public double nextDouble() {
            return value;
        }

        @Override
        public double nextGaussian() {
            return value;
        }
    }
}
