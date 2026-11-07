package com.warfactory.medical.lab;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.gametest.TestBodies;
import com.warfactory.medical.body.limb.LimbType;
import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.RoundDamageSource;
import com.wf.wflib.round.api.Rounds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** WFLabs {@code wfmedical:wounds} rows (one seeded sample) == the wounds a real player gets from the same strike. */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class LabGameTest {
    private static final String TEMPLATE = "empty";

    private record Case(String round, String armor, LimbType limb) {
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE, batch = "wfmedical_labs")
    public static void woundRowsEqualLiveHits(GameTestHelper helper) {
        Case[] cases = {
                new Case("wflib:5.56x45/ball", "none", LimbType.TORSO),
                new Case("wflib:5.56x45/ball", "none", LimbType.HEAD),
                new Case("wflib:5.56x45/ball", "wflib:combat_helmet", LimbType.HEAD),
                new Case("wflib:5.56x45/ball", "wflib:plate_carrier", LimbType.TORSO),
                new Case("wflib:5.56x45/ball", "wflib:plate_carrier+wflib:steel_platex2", LimbType.TORSO),
                new Case("wflib:7.62x51/ball", "none", LimbType.LEFT_LEG),
                new Case("wflib:7.62x51/ball", "wflib:plate_carrier", LimbType.RIGHT_ARM)};
        for (Case c : cases) {
            KineticPreset p = KineticPresets.get(ResourceLocation.parse(c.round()));
            List<Object[]> rows = new ArrayList<>();
            LabContext ctx = LabContext.of(helper.getLevel(), new double[]{0.0}, id -> id.equals(c.round()),
                    Map.of("wound_samples", "1", "armor", c.armor().equals("none") ? "none" : c.armor().split("\\+")[0]));
            new WoundsProducer().produce(ctx, rows::add);
            String limb = c.limb().name().toLowerCase(Locale.ROOT);
            Object[] row = rows.stream().filter(r -> r[1].equals(c.round()) && r[4].equals(c.armor())
                    && r[5].equals(limb)).findFirst().orElse(null);
            helper.assertTrue(row != null, c + ": no row among " + rows.size());
            Vec3 vel = new Vec3(((Number) row[6]).doubleValue() / 20.0, 0.0, 0.0);
            Threat threat = Rounds.threat(p, vel);
            RoundDamageSource src = Rounds.source(helper.getLevel(), p, null, null, threat, Vec3.ZERO, vel, 0L, vel,
                    RoundDamageSource.LIVE, List.of(c.limb().part()));
            TestBodies.Victim victim = TestBodies.victim(helper);
            ArmorSetups.Setup set = c.armor().equals("none") ? null : ArmorSetups.all().stream()
                    .filter(s -> s.label().equals(c.armor())).findFirst().orElseThrow();
            if (set != null) {
                victim.setItemSlot(set.slot(), set.stack().copy());
            }
            victim.getRandom().setSeed(20261103L);
            victim.hurt(src, ((Number) row[7]).floatValue());
            MedicalProfile profile = TestBodies.profileOf(helper, victim);
            String live = WoundModel.text(profile.allTraumas());
            helper.assertTrue(live.equals(row[13]), c + ": live " + live + ", lab " + row[13]);
        }
        helper.succeed();
    }
}
