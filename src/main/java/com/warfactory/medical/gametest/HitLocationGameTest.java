package com.warfactory.medical.gametest;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.damage.DamageCategory;
import com.warfactory.medical.core.damage.HitGeometry;
import com.warfactory.medical.core.damage.HitLocation;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Deterministic GameTests for the Tier 1 geometric hit-location classifier ({@link HitGeometry}).
 *
 * <p>Each test spawns a stationary {@link ArmorStand} at a fixed {@code yBodyRot} of 0 (facing +Z /
 * south, so the victim's <em>front</em> is +Z and the victim's <em>right</em> is -X), then fires a
 * synthetic ray through {@link HitGeometry#classifyRay} and asserts the resulting {@link LimbType}.
 * Ray endpoints are derived from the victim's live bounding box so the asserts are independent of the
 * absolute spawn coordinates, and no {@code RandomSource} is ever consulted on the geometric path.</p>
 *
 * <p>Discovery: {@code @GameTestHolder(wfmedical)} + {@code forge.enabledGameTestNamespaces=wfmedical}
 * (already set on the {@code gameTestServer} run in build.gradle) auto-registers every method below.
 * Each test loads the bundled empty stone platform at
 * {@code src/main/resources/data/wfmedical/structures/empty.nbt}. Run with
 * {@code ./gradlew runGameTestServer} or, in a running server, {@code /test run wfmedical:<name>}.</p>
 */
@GameTestHolder(WFMedical.MOD_ID)
public class HitLocationGameTest {

    /** Structure template shared by every test (a 3x3 stone floor); resolves {@code wfmedical:empty}. */
    private static final String TEMPLATE = "empty";

    /**
     * Spawn a gravity-free victim facing +Z (south) with {@code yBodyRot = 0}. Determinism relies on the
     * body yaw being fixed, so it is pinned on every rotation field the classifier could read.
     */
    private static ArmorStand victim(GameTestHelper helper) {
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
        stand.setNoGravity(true);
        stand.setYRot(0.0F);
        // yBodyRot is the frame the classifier reads (HitGeometry reads this exact public field).
        stand.yBodyRot = 0.0F;
        stand.yBodyRotO = 0.0F;
        return stand;
    }

    /** World-space Y for a given fraction of body height (0 = feet, 1 = crown). */
    private static double yAt(AABB box, double relY) {
        return box.minY + relY * box.getYsize();
    }

    private static void expect(GameTestHelper helper, ArmorStand v, Vec3 from, Vec3 to, LimbType want) {
        LimbType got = HitGeometry.classifyRay(v, from, to);
        helper.assertTrue(got == want, "expected " + want + " but got " + got);
    }

    // --- frontal shots (ray travels -Z into the front face; nx = 0) -----------------------------

    /** A centred frontal hit high on the box reads as HEAD. */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void frontalHeadIsHead(GameTestHelper helper) {
        ArmorStand v = victim(helper);
        AABB box = v.getBoundingBox();
        double cx = box.getCenter().x;
        double y = yAt(box, 0.90);
        expect(helper, v,
                new Vec3(cx, y, box.maxZ + 1.0),
                new Vec3(cx, y, box.minZ - 1.0),
                LimbType.HEAD);
        helper.succeed();
    }

    /** A centred frontal hit at mid height reads as TORSO. */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void frontalTorsoIsTorso(GameTestHelper helper) {
        ArmorStand v = victim(helper);
        AABB box = v.getBoundingBox();
        double cx = box.getCenter().x;
        double y = yAt(box, 0.50);
        expect(helper, v,
                new Vec3(cx, y, box.maxZ + 1.0),
                new Vec3(cx, y, box.minZ - 1.0),
                LimbType.TORSO);
        helper.succeed();
    }

    /** A centred frontal hit low on the box reads as a LEG (nx = 0 -> the right leg). */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void lowFrontalIsLeg(GameTestHelper helper) {
        ArmorStand v = victim(helper);
        AABB box = v.getBoundingBox();
        double cx = box.getCenter().x;
        double y = yAt(box, 0.20);
        expect(helper, v,
                new Vec3(cx, y, box.maxZ + 1.0),
                new Vec3(cx, y, box.minZ - 1.0),
                LimbType.RIGHT_LEG);
        helper.succeed();
    }

    // --- side / directional shots ---------------------------------------------------------------

    /**
     * A torso-height ray entering the victim's RIGHT face (-X / west) reads as the RIGHT_ARM: it lands on
     * the outer sliver of the box, which is arm territory when the victim is caught side-on.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void sideOnIsArm(GameTestHelper helper) {
        ArmorStand v = victim(helper);
        AABB box = v.getBoundingBox();
        double cz = box.getCenter().z;
        double y = yAt(box, 0.50);
        expect(helper, v,
                new Vec3(box.minX - 1.0, y, cz),
                new Vec3(box.maxX + 1.0, y, cz),
                LimbType.RIGHT_ARM);
        helper.succeed();
    }

    /**
     * A blast arriving from the victim's LEFT (+X / east): the ray enters the +X face, so the entry maps to
     * the victim's left side and reads as LEFT_ARM at torso height. Proves left/right discrimination is
     * driven purely by the entry geometry.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void blastFromLeftIsLeftSide(GameTestHelper helper) {
        ArmorStand v = victim(helper);
        AABB box = v.getBoundingBox();
        double cz = box.getCenter().z;
        double y = yAt(box, 0.50);
        expect(helper, v,
                new Vec3(box.maxX + 1.0, y, cz),
                new Vec3(box.minX - 1.0, y, cz),
                LimbType.LEFT_ARM);
        helper.succeed();
    }

    // --- fallback path --------------------------------------------------------------------------

    /**
     * A geometry-less source (generic damage: no attacker, no projectile, no source position) yields no
     * reconstructable hit point, so {@link HitGeometry#classifyHit} returns null and
     * {@link HitLocation#pick} must fall through to the weighted sampler and still return a (non-null)
     * limb without throwing.
     */
    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void geometrylessPickFallsBackToWeighted(GameTestHelper helper) {
        ArmorStand v = victim(helper);
        DamageSource generic = helper.getLevel().damageSources().generic();

        // The geometric path cannot reconstruct a position -> null (this is what triggers the fallback).
        helper.assertTrue(HitGeometry.classifyHit(v, generic, DamageCategory.GENERIC) == null,
                "generic damage must have no reconstructable hit point");

        RandomSource rand = RandomSource.create(1234L);
        LimbType limb = HitLocation.pick(v, generic, DamageCategory.GENERIC, rand);
        helper.assertTrue(limb != null, "geometry-less pick must return a non-null limb via the sampler");
        helper.succeed();
    }
}
