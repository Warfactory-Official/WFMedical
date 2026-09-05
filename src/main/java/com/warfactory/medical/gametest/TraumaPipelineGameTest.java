package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.compat.TaczHitCapture;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * The damage -> trauma pipeline end to end: a real {@code hurt()} on a real server player, through
 * {@code MedicalEventHandler.onLivingHurt}, out the other side as a wound on a specific limb.
 *
 * <p>The geometry tests upstream of this prove a ray picks the right box. They cannot prove the mod
 * ever asks: classification, TACZ coalescing, armour evaluation and trauma generation are wired
 * together by an event handler, and every one of those seams can break while every geometry test
 * stays green.
 *
 * <p>Two things make this awkward enough to be worth writing down:
 * <ul>
 *   <li>{@link FakePlayer#isInvulnerableTo} returns {@code true} unconditionally, so a plain FakePlayer
 *       silently absorbs every {@code hurt()} before the event is ever fired -- a test written on one
 *       passes by doing nothing at all. {@link Victim} below overrides that back.</li>
 *   <li>TACZ is optional and only a {@code compileOnly} dependency, so nothing here may reference a
 *       TACZ class by name: the class would fail to load during gametest discovery on an install
 *       without it. The bullet is spawned through the entity registry by id instead.</li>
 * </ul>
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class TraumaPipelineGameTest {

    private static final String TEMPLATE = "empty";

    private static final ResourceLocation TACZ_BULLET_ENTITY =
            ResourceLocation.fromNamespaceAndPath("tacz", "bullet");
    private static final ResourceKey<DamageType> TACZ_BULLET_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("tacz", "bullet"));

    private static final double HEAD_Y = 1.62;
    private static final double TORSO_Y = 0.90;

    // The victim, the profile accessor and the TACZ presence check now live in TestBodies, so the
    // invulnerability workarounds are stated once. See that class for why each of them is needed.
    private static TestBodies.Victim victim(GameTestHelper helper) {
        return TestBodies.victim(helper);
    }

    private static boolean taczPresent() {
        return TestBodies.taczPresent();
    }

    private static MedicalProfile profileOf(GameTestHelper helper, TestBodies.Victim v) {
        return TestBodies.profileOf(helper, v);
    }

    private static List<Trauma> allTraumas(MedicalProfile profile) {
        return TestBodies.allTraumas(profile);
    }

    private static String describe(MedicalProfile profile) {
        return TestBodies.describe(profile);
    }

    /**
     * Spawns a real TACZ bullet entity, resolved through the registry rather than by class reference.
     * {@code getOptional} rather than {@code get}: BuiltInRegistries are DEFAULTED, so a missing id
     * silently yields minecraft:pig instead of null.
     */
    private static Entity spawnTaczBullet(GameTestHelper helper, Vec3 at) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(TACZ_BULLET_ENTITY);
        if (type.isEmpty()) {
            helper.fail("TACZ is loaded but its bullet entity '" + TACZ_BULLET_ENTITY + "' is not registered");
        }
        Entity bullet = type.get().create(helper.getLevel());
        if (bullet == null) {
            helper.fail("could not construct a " + TACZ_BULLET_ENTITY);
        }
        bullet.setPos(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(bullet);
        return bullet;
    }

    private static DamageSource taczDamage(GameTestHelper helper, Entity bullet, Entity shooter) {
        Holder<DamageType> type = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(TACZ_BULLET_DAMAGE);
        return new DamageSource(type, bullet, shooter);
    }

    /**
     * Reproduces what EntityKineticBulletMixin captures at the moment of impact: the resolved hit point
     * and the true per-tick ray segment, plus the bullet's total damage.
     */
    private static void captureShot(Entity bullet, Vec3 from, Vec3 to, float totalDamage) {
        TaczHitCapture.capture(bullet.getId(), to, from, to);
        TaczHitCapture.captureDamage(bullet.getId(), totalDamage);
    }

    // ---------------------------------------------------------------------------------------------

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aVictimStartsUninjured(GameTestHelper helper) {
        // Baseline. Every assertion below is "a wound appeared", which means nothing unless the
        // starting state is known to be clean.
        TestBodies.Victim v = victim(helper);
        MedicalProfile profile = profileOf(helper, v);
        helper.assertTrue(allTraumas(profile).isEmpty(),
                "a fresh player should have no traumas; had " + describe(profile));
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aTaczBulletWoundsTheLimbItsRayEntered(GameTestHelper helper) {
        if (!taczPresent()) {
            helper.succeed();
            return;
        }
        TestBodies.Victim v = victim(helper);
        Vec3 feet = v.position();
        Entity bullet = spawnTaczBullet(helper, feet.add(0.0, HEAD_Y, 3.0));
        // A frontal ray at head height, the same geometry the rig tests classify as HEAD.
        captureShot(bullet, feet.add(0.0, HEAD_Y, 2.0), feet.add(0.0, HEAD_Y, -2.0), 4.0F);

        v.hurt(taczDamage(helper, bullet, null), 4.0F);

        MedicalProfile profile = profileOf(helper, v);
        List<Trauma> head = profile.limb(LimbType.HEAD).getTraumas();
        helper.assertTrue(!head.isEmpty(),
                "a bullet whose ray enters the head should wound the HEAD; got " + describe(profile));
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aTaczBulletAimedAtTheChestWoundsTheTorso(GameTestHelper helper) {
        if (!taczPresent()) {
            helper.succeed();
            return;
        }
        TestBodies.Victim v = victim(helper);
        Vec3 feet = v.position();
        Entity bullet = spawnTaczBullet(helper, feet.add(0.0, TORSO_Y, 3.0));
        captureShot(bullet, feet.add(0.0, TORSO_Y, 2.0), feet.add(0.0, TORSO_Y, -2.0), 4.0F);

        v.hurt(taczDamage(helper, bullet, null), 4.0F);

        MedicalProfile profile = profileOf(helper, v);
        helper.assertTrue(!profile.limb(LimbType.TORSO).getTraumas().isEmpty(),
                "a chest-height bullet should wound the TORSO; got " + describe(profile));
        helper.assertTrue(profile.limb(LimbType.HEAD).getTraumas().isEmpty(),
                "a chest-height bullet should NOT wound the head; got " + describe(profile));
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void taczsSecondHurtEventAddsNoFurtherWounds(GameTestHelper helper) {
        if (!taczPresent()) {
            helper.succeed();
            return;
        }
        // The headline. TACZ delivers one bullet as two hurt events (its AP and non-AP portions, with
        // i-frames zeroed between them). Without the per-tick claim in onLivingHurt the shot wounds
        // twice -- invisible in game except that every gunshot is twice as bad as configured.
        //
        // Phrased as "the second event adds nothing" rather than "there is exactly one wound": a single
        // ballistic hit legitimately generates more than one trauma (penetration walks the limbs the ray
        // crossed, and TraumaGenerator can emit several per limb), so a fixed expected count would be
        // asserting an unrelated tuning value and would break every time it changed.
        TestBodies.Victim v = victim(helper);
        Vec3 feet = v.position();
        Entity bullet = spawnTaczBullet(helper, feet.add(0.0, TORSO_Y, 3.0));
        captureShot(bullet, feet.add(0.0, TORSO_Y, 2.0), feet.add(0.0, TORSO_Y, -2.0), 4.0F);
        DamageSource src = taczDamage(helper, bullet, null);
        MedicalProfile profile = profileOf(helper, v);

        v.hurt(src, 3.0F);
        int afterFirst = allTraumas(profile).size();

        v.invulnerableTime = 0;   // TACZ zeroes these between its two events; mirror that here
        v.hurt(src, 1.0F);
        int afterSecond = allTraumas(profile).size();

        helper.assertTrue(afterFirst > 0,
                "the first hurt event should have wounded the victim; got " + describe(profile));
        helper.assertTrue(afterSecond == afterFirst,
                "the second hurt event of the same bullet must add no wounds, but the count went "
                        + afterFirst + " -> " + afterSecond + " (" + describe(profile) + "). "
                        + "That is the TACZ double-hurt coalescing (TaczHitCapture.claim) not holding.");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aSeparateBulletInTheSameTickStillWounds(GameTestHelper helper) {
        if (!taczPresent()) {
            helper.succeed();
            return;
        }
        // The other half of the claim's contract: it is per bullet, not per tick. A burst puts several
        // rounds in flight on one tick, and if claiming one consumed the tick, only the first would wound.
        TestBodies.Victim v = victim(helper);
        Vec3 feet = v.position();

        Entity first = spawnTaczBullet(helper, feet.add(0.0, TORSO_Y, 3.0));
        captureShot(first, feet.add(0.0, TORSO_Y, 2.0), feet.add(0.0, TORSO_Y, -2.0), 3.0F);
        v.hurt(taczDamage(helper, first, null), 3.0F);
        v.invulnerableTime = 0;

        Entity second = spawnTaczBullet(helper, feet.add(0.0, TORSO_Y, 3.0));
        captureShot(second, feet.add(0.0, TORSO_Y, 2.0), feet.add(0.0, TORSO_Y, -2.0), 3.0F);
        v.hurt(taczDamage(helper, second, null), 3.0F);

        MedicalProfile profile = profileOf(helper, v);
        helper.assertTrue(!profile.limb(LimbType.TORSO).getTraumas().isEmpty(),
                "two distinct bullets in one tick should both be able to wound; got " + describe(profile));
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void aBallisticWoundBleedsAndHurts(GameTestHelper helper) {
        if (!taczPresent()) {
            helper.succeed();
            return;
        }
        // The wound is not just a marker: the derived per-limb state is what the HUD, the bleed-out
        // timer and the downed threshold all read.
        TestBodies.Victim v = victim(helper);
        Vec3 feet = v.position();
        Entity bullet = spawnTaczBullet(helper, feet.add(0.0, TORSO_Y, 3.0));
        captureShot(bullet, feet.add(0.0, TORSO_Y, 2.0), feet.add(0.0, TORSO_Y, -2.0), 5.0F);

        v.hurt(taczDamage(helper, bullet, null), 5.0F);

        MedicalProfile profile = profileOf(helper, v);
        var torso = profile.limb(LimbType.TORSO);
        torso.rebuildCache();
        helper.assertTrue(!torso.getTraumas().isEmpty(),
                "expected a torso wound to derive state from; got " + describe(profile));
        helper.assertTrue(torso.getCachedPain() > 0.0F,
                "a fresh ballistic wound should generate pain; got " + torso.getCachedPain());
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theMedicalSystemAbsorbsTheVanillaDamage(GameTestHelper helper) {
        if (!taczPresent()) {
            helper.succeed();
            return;
        }
        // onLivingHurt zeroes the event amount on an unblocked hit: health is driven by the medical
        // model, not by vanilla subtraction. If this regressed, players would take damage twice.
        TestBodies.Victim v = victim(helper);
        float before = v.getHealth();
        Vec3 feet = v.position();
        Entity bullet = spawnTaczBullet(helper, feet.add(0.0, TORSO_Y, 3.0));
        captureShot(bullet, feet.add(0.0, TORSO_Y, 2.0), feet.add(0.0, TORSO_Y, -2.0), 4.0F);

        v.hurt(taczDamage(helper, bullet, null), 4.0F);

        helper.assertTrue(v.getHealth() >= before - 0.001F,
                "vanilla damage should have been absorbed by the medical system; health went "
                        + before + " -> " + v.getHealth());
        helper.succeed();
    }
}
