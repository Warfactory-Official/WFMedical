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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class HitLocationGameTest {

    private static final String TEMPLATE = "empty";

    private static ArmorStand victim(GameTestHelper helper) {
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
        stand.setNoGravity(true);
        stand.setYRot(0.0F);
        stand.yBodyRot = 0.0F;
        stand.yBodyRotO = 0.0F;
        return stand;
    }

    private static double yAt(AABB box, double relY) {
        return box.minY + relY * box.getYsize();
    }

    private static void expect(GameTestHelper helper, ArmorStand v, Vec3 from, Vec3 to, LimbType want) {
        LimbType got = HitGeometry.classifyRay(v, from, to);
        helper.assertTrue(got == want, "expected " + want + " but got " + got);
    }


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


    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void geometrylessPickFallsBackToWeighted(GameTestHelper helper) {
        ArmorStand v = victim(helper);
        DamageSource generic = helper.getLevel().damageSources().generic();

        helper.assertTrue(HitGeometry.classifyHit(v, generic, DamageCategory.GENERIC) == null,
                "generic damage must have no reconstructable hit point");

        RandomSource rand = RandomSource.create(1234L);
        LimbType limb = HitLocation.pick(v, generic, DamageCategory.GENERIC, rand);
        helper.assertTrue(limb != null, "geometry-less pick must return a non-null limb via the sampler");
        helper.succeed();
    }
}
