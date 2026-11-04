package com.warfactory.medical.lab;

import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.stance.AhfStance;
import com.norwood.ahf.stance.Posture;
import com.norwood.ahf.stance.StanceRequest;
import com.warfactory.medical.WFMedical;
import com.wf.wflib.damage.api.event.ProjectileStrikeEvent;
import com.wf.wflib.labs.api.LabRows;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.Rounds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Consumer;

/**
 * {@code wfmedical:hit_zones} cells == {@code Rounds.fire} rounds struck at the same stance in the world (first struck
 * part per {@code ProjectileStrikeEvent}), 4 sigma of the two binomial errors + 0.01.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class HitZonesGameTest {
    private static final String TEMPLATE = "empty";
    private static final int LIVE_SHOTS = 3000;
    private static final int LAB_SAMPLES = 20000;
    /** Above the arena lid: a free lane. */
    private static final double HEIGHT = 10.0;

    private record Case(String stance, int aim, double range, double inaccuracy, int tick) {
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE, batch = "wfmedical_labs", timeoutTicks = 600)
    public static void hitZoneCellsEqualLiveShots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KineticPreset p = KineticPresets.get(ResourceLocation.parse("wflib:5.56x45/ball"));
        Case[] cases = {
                new Case("stand", 0, 30.0, 1.5, 0), new Case("stand", 0, 30.0, 1.5, 222), new Case("stand", 1, 30.0, 0.8, 37),
                new Case("kneel", 0, 30.0, 1.5, 74), new Case("prone", 0, 30.0, 1.5, 111),
                new Case("sneak", 0, 30.0, 1.5, 148)};
        StringBuilder bad = new StringBuilder();
        var seq = helper.startSequence();
        for (Case c : cases) {
            FakePlayer[] v = new FakePlayer[1];
            Set<Long> keys = ConcurrentHashMap.newKeySet();
            AtomicIntegerArray counts = new AtomicIntegerArray(HitZones.PARTS.length + 1);
            Consumer<ProjectileStrikeEvent> listener = e -> {
                if (e.key() instanceof Long k && keys.contains(k) && e.target() == v[0]) {
                    List<HitboxPart> parts = e.parts();
                    counts.incrementAndGet(parts == null || parts.isEmpty() ? HitZones.PARTS.length
                            : parts.get(0).ordinal());
                }
            };
            seq.thenExecute(() -> {
                v[0] = LabRows.victim(level);
                Vec3 at = helper.absoluteVec(new Vec3(1.5, HEIGHT, 1.5));
                v[0].moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
                v[0].setYHeadRot(0.0F);
                v[0].yBodyRot = 0.0F;
                v[0].yBodyRotO = 0.0F;
                level.addFreshEntity(v[0]);
                switch (c.stance()) {
                    case "kneel" -> helper.assertTrue(AhfStance.request(v[0],
                            StanceRequest.STAND.withPosture(Posture.KNEEL)), "kneel refused");
                    case "prone" -> helper.assertTrue(AhfStance.request(v[0],
                            StanceRequest.STAND.withPosture(Posture.PRONE)), "prone refused");
                    case "sneak" -> {
                        v[0].setShiftKeyDown(true);
                        v[0].setPose(Pose.CROUCHING);
                    }
                    default -> {
                    }
                }
            }).thenIdle(30).thenExecute(() -> {
                NeoForge.EVENT_BUS.addListener(listener);
                v[0].tickCount = c.tick();
                HitZones.Body b = HitZones.body(c.stance(), v[0]);
                double yaw = Math.toRadians(b.yaw());
                Vec3 toShooter = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
                Vec3 muzzle = b.aim(c.aim()).add(toShooter.scale(c.range()));
                for (int k = 0; k < LIVE_SHOTS; k++) {
                    keys.add(Rounds.fire(level, p, muzzle, toShooter.scale(-1.0), (float) c.inaccuracy(), Vec3.ZERO,
                            null, null));
                }
            }).thenIdle(4).thenExecute(() -> {
                NeoForge.EVENT_BUS.unregister(listener);
                try {
                    compare(level, p, c, counts, bad);
                } finally {
                    v[0].discard();
                }
            });
        }
        seq.thenExecute(() -> helper.assertTrue(bad.isEmpty(), "lab != live:" + bad)).thenSucceed();
    }

    private static void compare(ServerLevel level, KineticPreset p, Case c, AtomicIntegerArray counts,
                                StringBuilder bad) {
        int st = List.of(HitZones.STANCES).indexOf(c.stance());
        double moa = (c.inaccuracy() + p.dispersion()) * 60.0;
        HitZones.Body[][] lab = HitZones.bodies(level, List.of(st), new int[]{c.tick()});
        HitZones.Field f = HitZones.solve(java.util.concurrent.ForkJoinPool.commonPool(), lab,
                List.of(new HitZones.Cell(moa, c.range())), List.of(st), List.of(c.aim()), List.of(0.0), LAB_SAMPLES,
                20261104L);
        HitZones.Dist d = f.get(moa, c.range(), st, c.aim(), 0);
        int live = 0;
        for (int k = 0; k < counts.length(); k++) {
            live += counts.get(k);
        }
        if (live <= LIVE_SHOTS / 4) {
            bad.append("\n").append(c).append(": only ").append(live).append(" of ").append(LIVE_SHOTS)
                    .append(" live shots struck");
            return;
        }
        for (HitboxPart part : HitZones.PARTS) {
            double pl = d.part(part) / (1.0 - d.miss() - d.other());
            double pv = (double) counts.get(part.ordinal()) / live;
            double tol = 4.0 * Math.sqrt(pl * (1.0 - pl) * (1.0 / LAB_SAMPLES + 1.0 / live)) + 0.01;
            if (Math.abs(pl - pv) > tol) {
                bad.append("\n").append(c).append(' ').append(part).append(": lab ").append(pl).append(", live ")
                        .append(pv).append(", tol ").append(tol);
            }
        }
        double hitLab = 1.0 - d.miss();
        double hitLive = (double) live / LIVE_SHOTS;
        double tol = 4.0 * Math.sqrt(hitLab * (1.0 - hitLab) * (1.0 / LAB_SAMPLES + 1.0 / LIVE_SHOTS)) + 0.01;
        if (Math.abs(hitLab - hitLive) > tol) {
            bad.append("\n").append(c).append(" hit share: lab ").append(hitLab).append(", live ").append(hitLive)
                    .append(", tol ").append(tol);
        }
    }
}
