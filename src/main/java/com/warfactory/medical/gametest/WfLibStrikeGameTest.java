package com.warfactory.medical.gametest;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.part.HitboxPart;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.compat.wflib.WfLibArmorCompat;
import com.warfactory.medical.compat.wflib.WfLibStrikeCompat;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.damage.DamageCategory;
import com.warfactory.medical.core.damage.DamageClassifier;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * WFLib strikes -> medical: threat category, WFLib's parts as limbs, no gap reject. WFLib types only in
 * {@link WfLibRounds}. Registered by {@link WfLibGameTests}.
 */
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = WFMedical.MOD_ID)
public class WfLibStrikeGameTest {

    private static final String TEMPLATE = "empty";
    /** Above the arena lid: the round's lane is air. */
    private static final BlockPos STAND = new BlockPos(1, 10, 1);
    private static final double LANE = 2.4;
    private static final double HEAD_Y = 1.75;
    private static final double TORSO_Y = 1.0;
    /** Arm band: a side shot crosses arm, torso, arm. */
    private static final double ARM_Y = 1.15;

    private static final Map<String, DamageCategory> EXPECTED = Map.of(
            "SMALL_ARMS", DamageCategory.BALLISTIC,
            "KINETIC", DamageCategory.BALLISTIC,
            "HE", DamageCategory.EXPLOSION,
            "HEAT", DamageCategory.EXPLOSION,
            "TANDEM_HEAT", DamageCategory.EXPLOSION);

    private static final List<Hurt> HURTS = new ArrayList<>();
    private static boolean recording;

    private record Hurt(Entity victim, DamageSource source, float amount) {
    }

    /** Incoming amount before any handler (medical zeroes it). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (recording) {
            HURTS.add(new Hurt(event.getEntity(), event.getSource(), event.getAmount()));
        }
    }

    @WfLibMode(false)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void withoutWfLibNothingIsAStrike(GameTestHelper helper) {
        DamageSource cactus = helper.getLevel().damageSources().cactus();
        helper.assertTrue(WfLibStrikeCompat.category(cactus) == null && WfLibStrikeCompat.parts(cactus) == null,
                "the strike compat answered without WFLib");
        helper.succeed();
    }

    @WfLibMode(true)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void threatKindsMapToCategoriesAndConfigWins(GameTestHelper helper) {
        helper.assertTrue(new HashSet<>(WfLibRounds.threatKinds()).equals(EXPECTED.keySet()),
                "ThreatKind set changed: " + WfLibRounds.threatKinds() + " vs " + EXPECTED.keySet());
        Vec3 o = Vec3.ZERO;
        for (Map.Entry<String, DamageCategory> e : EXPECTED.entrySet()) {
            DamageSource s = WfLibRounds.strike(helper.getLevel(), e.getKey(), o, o, null);
            helper.assertTrue(DamageClassifier.classify(s) == e.getValue(),
                    e.getKey() + " -> " + DamageClassifier.classify(s) + ", want " + e.getValue());
        }
        DamageSource he = WfLibRounds.strike(helper.getLevel(), "HE", o, o, null);
        @SuppressWarnings("unchecked")
        ModConfigSpec.ConfigValue<List<? extends String>> overrides = (ModConfigSpec.ConfigValue<List<? extends String>>)
                MedicalConfig.SPEC.getValues().get("compat.damageSourceCategories");
        List<? extends String> prev = overrides.get();
        overrides.set(List.of(WfLibRounds.MSG_ID + "=FIRE"));
        DamageCategory configured;
        try {
            configured = DamageClassifier.classify(he);
        } finally {
            overrides.set(prev);
        }
        helper.assertTrue(configured == DamageCategory.FIRE, "config override lost to the threat: " + configured);
        helper.succeed();
    }

    /** Torso: BALLISTIC wounds on TORSO only; WFLib's part on the damage. */
    @WfLibMode(true)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aRoundWoundsTheLimbAhfClassifiedAsBallistic(GameTestHelper helper) {
        TestBodies.Victim v = spawned(helper);
        try {
            Hurt hurt = fire(helper, v, new Vec3(0.0, TORSO_Y, LANE / 2), new Vec3(0.0, 0.0, -LANE));
            helper.assertTrue(List.of(HitboxPart.TORSO).equals(WfLibStrikeCompat.parts(hurt.source())),
                    "WFLib parts " + WfLibStrikeCompat.parts(hurt.source()));
            helper.assertTrue(DamageClassifier.classify(hurt.source()) == DamageCategory.BALLISTIC,
                    "category " + DamageClassifier.classify(hurt.source()));
            MedicalProfile profile = TestBodies.profileOf(helper, v);
            helper.assertTrue(hasPuncture(profile, LimbType.TORSO), "no torso gunshot: " + TestBodies.describe(profile));
            for (LimbType limb : LimbType.VALUES) {
                helper.assertTrue(limb == LimbType.TORSO || profile.limb(limb).getTraumas().isEmpty(),
                        "wound off the struck limb: " + TestBodies.describe(profile));
            }
        } finally {
            v.discard();
        }
        helper.succeed();
    }

    /** Medical claimed players => WFLib headshot factor 1; HEAD wounded. */
    @WfLibMode(true)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aHeadshotOnAClaimedPlayerIsNotMultiplied(GameTestHelper helper) {
        TestBodies.Victim v = spawned(helper);
        try {
            Hurt hurt = fire(helper, v, new Vec3(0.0, HEAD_Y, LANE / 2), new Vec3(0.0, 0.0, -LANE));
            helper.assertTrue(List.of(HitboxPart.HEAD).equals(WfLibStrikeCompat.parts(hurt.source())),
                    "WFLib parts " + WfLibStrikeCompat.parts(hurt.source()));
            helper.assertTrue(hurt.amount() == WfLibRounds.DAMAGE,
                    "claimed player took " + hurt.amount() + ", want " + WfLibRounds.DAMAGE);
            MedicalProfile profile = TestBodies.profileOf(helper, v);
            helper.assertTrue(hasPuncture(profile, LimbType.HEAD), "no head gunshot: " + TestBodies.describe(profile));
        } finally {
            v.discard();
        }
        helper.succeed();
    }

    /** Side shot, penetration on: every WFLib part wounded (AHF's own pierce sees only the first). */
    @WfLibMode(true)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aPiercingRoundWoundsEveryPartItCrossed(GameTestHelper helper) {
        TestBodies.Victim v = spawned(helper);
        try {
            Hurt hurt = withPenetration(true,
                    () -> fire(helper, v, new Vec3(LANE / 2, ARM_Y, 0.0), new Vec3(-LANE, 0.0, 0.0)));
            List<HitboxPart> parts = WfLibStrikeCompat.parts(hurt.source());
            helper.assertTrue(parts != null && parts.size() >= 2, "side shot crossed " + parts);
            MedicalProfile profile = TestBodies.profileOf(helper, v);
            for (HitboxPart part : parts) {
                helper.assertTrue(!profile.limb(LimbType.of(part)).getTraumas().isEmpty(),
                        parts + " but " + part + " unwounded: " + TestBodies.describe(profile));
            }
        } finally {
            v.discard();
        }
        helper.succeed();
    }

    /** Penetration off: first WFLib part only, even when WFLib names more. */
    @WfLibMode(true)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void withoutPenetrationOnlyTheFirstPartIsWounded(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        Vec3 feet = v.position();
        DamageSource s = WfLibRounds.strike(helper.getLevel(), "SMALL_ARMS", feet.add(0.0, TORSO_Y, 2.0),
                feet.add(0.0, TORSO_Y, -2.0), List.of(HitboxPart.LEFT_ARM, HitboxPart.TORSO));
        withPenetration(false, () -> v.hurt(s, WfLibRounds.DAMAGE));
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        helper.assertTrue(!profile.limb(LimbType.LEFT_ARM).getTraumas().isEmpty()
                        && profile.limb(LimbType.TORSO).getTraumas().isEmpty(),
                "want LEFT_ARM only: " + TestBodies.describe(profile));
        helper.succeed();
    }

    /** Segment 1 block beside the body: rejected unclassified, wounds when WFLib names the part. */
    @WfLibMode(true)
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aClassifiedStrikeIsNeverGapRejected(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        Vec3 feet = v.position();
        Vec3 from = feet.add(1.0, TORSO_Y, 2.0);
        Vec3 to = feet.add(1.0, TORSO_Y, -2.0);
        boolean unclassified = v.hurt(WfLibRounds.strike(helper.getLevel(), "SMALL_ARMS", from, to, null),
                WfLibRounds.DAMAGE);
        helper.assertTrue(!unclassified, "gap control: the unclassified whiff landed");
        v.invulnerableTime = 0;
        v.hurt(WfLibRounds.strike(helper.getLevel(), "SMALL_ARMS", from, to, List.of(HitboxPart.TORSO)),
                WfLibRounds.DAMAGE);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        helper.assertTrue(hasPuncture(profile, LimbType.TORSO),
                "classified strike gap-rejected: " + TestBodies.describe(profile));
        helper.succeed();
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private static TestBodies.Victim spawned(GameTestHelper helper) {
        return TestBodies.spawn(helper, TestBodies.victim(helper, STAND, 0.0F));
    }

    /** One round from {@code feet + offset}. @return the only damage {@code v} took */
    private static Hurt fire(GameTestHelper helper, TestBodies.Victim v, Vec3 offset, Vec3 velocity) {
        HURTS.clear();
        recording = true;
        try {
            WfLibRounds.fire(helper.getLevel(), v.position().add(offset), velocity);
        } finally {
            recording = false;
        }
        Hurt only = null;
        for (Hurt h : HURTS) {
            if (h.victim() == v) {
                helper.assertTrue(only == null, "one round hurt the victim twice");
                only = h;
            }
        }
        helper.assertTrue(only != null, "the round never hurt the victim");
        return only;
    }

    private static boolean hasPuncture(MedicalProfile profile, LimbType limb) {
        for (Trauma t : profile.limb(limb).getTraumas()) {
            if (t.getType().getCategory() == TraumaCategory.PUNCTURE) {
                return true;
            }
        }
        return false;
    }

    /** AHF exposes no setter; restore before returning (server thread: no other test interleaves). */
    private static <T> T withPenetration(boolean on, Supplier<T> body) {
        Field f = penetrationField();
        try {
            boolean prev = f.getBoolean(null);
            f.setBoolean(null, on);
            try {
                return body.get();
            } finally {
                f.setBoolean(null, prev);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field penetrationField() {
        try {
            Field f = AhfConfig.class.getDeclaredField("penetrationEnabled");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }
}
