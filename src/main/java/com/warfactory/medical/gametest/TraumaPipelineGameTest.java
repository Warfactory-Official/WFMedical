package com.warfactory.medical.gametest;

import com.mojang.authlib.GameProfile;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.attachment.IMedicalData;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.compat.TaczCompat;
import com.warfactory.medical.compat.TaczHitCapture;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    /** A server player that can actually be damaged; see the class javadoc. */
    private static final class Victim extends FakePlayer {
        private Victim(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }
    }

    private static Victim victim(GameTestHelper helper) {
        Victim v = new Victim(helper.getLevel(), new GameProfile(UUID.randomUUID(), "wfmed_victim"));
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        v.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
        v.setYRot(0.0F);
        v.setXRot(0.0F);
        v.setYHeadRot(0.0F);
        v.yBodyRot = 0.0F;
        v.yBodyRotO = 0.0F;
        v.yHeadRot = 0.0F;
        v.yHeadRotO = 0.0F;
        v.setPose(Pose.STANDING);
        // Creative/spectator is skipped outright by onLivingHurt when effectImmuneInCreative is on.
        v.setGameMode(GameType.SURVIVAL);
        v.setHealth(v.getMaxHealth());
        v.invulnerableTime = 0;
        // A fresh ServerPlayer starts with 60 ticks of spawn invulnerability, and ServerPlayer.hurt
        // refuses everything that does not bypass invulnerability while it lasts. It only decays in
        // ServerPlayer.tick, which FakePlayer no-ops -- so without this every hurt() below silently
        // returns false and the tests pass by never testing anything. (Widened by our AT.)
        v.spawnInvulnerableTime = 0;
        return v;
    }

    private static boolean taczPresent() {
        return LoadingModList.get().getModFileById(TaczCompat.MOD_ID) != null;
    }

    private static MedicalProfile profileOf(GameTestHelper helper, Victim v) {
        IMedicalData data = MedicalAttachments.get(v);
        if (data == null) {
            helper.fail("the victim has no medical attachment -- MedicalAttachments.carriesMedical "
                    + "should be true for any Player");
        }
        return data.getProfile();
    }

    private static List<Trauma> allTraumas(MedicalProfile profile) {
        List<Trauma> out = new ArrayList<>();
        for (LimbType limb : LimbType.VALUES) {
            out.addAll(profile.limb(limb).getTraumas());
        }
        return out;
    }

    private static String describe(MedicalProfile profile) {
        StringBuilder sb = new StringBuilder();
        for (LimbType limb : LimbType.VALUES) {
            int n = profile.limb(limb).getTraumas().size();
            if (n > 0) {
                sb.append(limb).append('=').append(n).append(' ');
            }
        }
        return sb.length() == 0 ? "(no traumas)" : sb.toString().trim();
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
        Victim v = victim(helper);
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
        Victim v = victim(helper);
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
        Victim v = victim(helper);
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
        Victim v = victim(helper);
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
        Victim v = victim(helper);
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
        Victim v = victim(helper);
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
        Victim v = victim(helper);
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
