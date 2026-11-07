package com.warfactory.medical.damage.gametest;

import com.norwood.ahf.part.HitboxPart;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.gametest.TestBodies;
import com.warfactory.medical.damage.HitReactions;
import com.warfactory.medical.damage.Stagger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** xray-monolith stagger: a rifle round to the head staggers (slowed, then recovers); a graze to a limb does not. */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class HitReactionGameTest {

    private static final String TEMPLATE = "empty";
    private static final float RIFLE = 10.0F;

    private static DamageSource shot(GameTestHelper helper, TestBodies.Victim v, HitboxPart part) {
        Vec3 feet = v.position();
        return WfLibRounds.strike(helper.getLevel(), "SMALL_ARMS", feet.add(0.0, 1.0, 2.0),
                feet.add(0.0, 1.0, -2.0), List.of(part));
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aHeadHitStaggersThenTheCasualtyRecovers(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        v.hurt(shot(helper, v, HitboxPart.HEAD), RIFLE);
        helper.assertTrue(HitReactions.isStaggered(v), "head hit did not stagger");
        helper.assertTrue(v.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(
                ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "stagger")), "no slow while staggered");
        helper.runAfterDelay(com.warfactory.medical.damage.HitClips.staggerTicks(Stagger.Zone.HEAD, 0) + 6, () -> {
            helper.assertTrue(!HitReactions.isStaggered(v), "still staggered");
            helper.assertTrue(!v.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(
                    ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "stagger")), "slow not removed");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public static void aSingleLimbHitDoesNotStagger(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        v.hurt(shot(helper, v, HitboxPart.RIGHT_ARM), RIFLE);
        helper.assertTrue(!HitReactions.isStaggered(v), "one arm hit staggered");
        helper.succeed();
    }
}
