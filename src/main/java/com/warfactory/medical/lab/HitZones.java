package com.warfactory.medical.lab;

import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.rig.RigCache;
import com.norwood.ahf.stance.AhfStance;
import com.norwood.ahf.stance.Posture;
import com.norwood.ahf.stance.StanceLogic;
import com.norwood.ahf.stance.StanceRequest;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabFlight;
import com.wf.wflib.labs.api.LabLauncher;
import com.wf.wflib.labs.api.LabRows;
import com.wf.wflib.round.api.Rounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Where a dispersed shot lands on a posed body: {@code Rounds.spread} cone x range x stance x aim, each sample
 * through {@link Rounds#hitLive} (AHF rig OBBs, registration envelope, PRECISE gap rule) on a real {@code FakePlayer}.
 * Level shot, zero-drop sights, shooter at the aim point's height {@code range} m away on {@code bearing} from the
 * body front. Bodies are posed and ticked on the calling thread; workers only read them. Idle arm bob follows
 * {@code tickCount} ({@code HumanoidRig}): each stance is built at {@link #IDLE_PHASES} tick counts, sample i
 * shoots body {@code i % phases} = time average over a body that stands about.
 */
final class HitZones {
    static final String[] STANCES = {"stand", "sneak", "kneel", "prone"};
    static final String[] AIMS = {"centre", "head"};
    static final HitboxPart[] PARTS = HitboxPart.VALUES;
    /** {@code tickCount}s of the bodies a cell averages over: both bob periods (2 pi / 0.09, 2 pi / 0.067 ticks) covered. */
    static final int[] IDLE_PHASES = {0, 37, 74, 111, 148, 185, 222, 259, 296, 333, 370, 407, 444, 481, 518, 555};
    /** Ticks past the slowest posture blend ({@code StanceLogic.LIE_RATE} = 1/12). */
    private static final int SETTLE_TICKS = 16;
    /** Segment half-length about the range plane, m. */
    private static final double REACH = 2.0;
    private static final long MIX = 0x9E3779B97F4A7C15L;

    private HitZones() {
    }

    /** A body posed and settled on the calling thread. */
    record Body(String stance, FakePlayer victim, double yaw, Vec3 centre, Vec3 head) {
        Vec3 aim(int aim) {
            return aim == 0 ? centre : head;
        }
    }

    /** Hit counts of one cell: {@code parts[HitboxPart ordinal]}, box fallback, miss. */
    record Dist(int[] parts, int otherN, int missN, int n) {
        double part(HitboxPart p) {
            return (double) parts[p.ordinal()] / n;
        }

        double other() {
            return (double) otherN / n;
        }

        double miss() {
            return (double) missN / n;
        }

        double hit() {
            return 1.0 - miss();
        }
    }

    /** Dispersion x distance of a shot. */
    record Cell(double moa, double range) {
    }

    /** One cell of the distribution. */
    record Key(double moa, double range, int stance, int aim, int bearing) {
    }

    /** Solved cells; {@link #get} for the keys handed to {@link #solve}. */
    static final class Field {
        private final ConcurrentHashMap<Key, Dist> cells = new ConcurrentHashMap<>();
        private final List<Double> bearings;

        private Field(List<Double> bearings) {
            this.bearings = bearings;
        }

        Dist get(double moa, double range, int stance, int aim, int bearing) {
            Dist d = cells.get(new Key(moa, range, stance, aim, bearing));
            if (d == null) {
                throw new IllegalStateException("unsolved cell " + moa + " moa " + range + " m " + STANCES[stance]
                        + "/" + AIMS[aim] + " bearing " + bearings.get(bearing));
            }
            return d;
        }
    }

    static List<Integer> indices(String csv, String[] names, String what) {
        List<Integer> out = new ArrayList<>();
        for (String n : csv.split(",")) {
            String t = n.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) {
                continue;
            }
            int i = java.util.Arrays.asList(names).indexOf(t);
            if (i < 0) {
                throw new IllegalArgumentException(what + " " + t + ": one of " + String.join(",", names));
            }
            out.add(i);
        }
        return out;
    }

    static List<Double> bearings(String csv) {
        List<Double> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            if (!s.isBlank()) {
                out.add(Double.parseDouble(s.trim()));
            }
        }
        return out;
    }

    /**
     * Grid of one run: launchers, their flights over {@code ranges}, and the solved field. Params {@code stances=}
     * ({@link #STANCES}), {@code aims=} ({@link #AIMS}), {@code bearings=} (deg from the body front, +90 = body's
     * local +x side), {@code hit_samples=}, and with {@code overridable} {@code moas=} (dispersion, MOA, replacing each
     * launcher's own).
     */
    record Plan(List<LabLauncher> launchers, LabFlight.Sample[][] flights, List<Integer> stances, List<Integer> aims,
                List<Double> bearings, double[] moaOverride, Field field, int n) {
        double[] moas(LabLauncher l) {
            return moaOverride != null ? moaOverride : new double[]{l.dispersionMoa()};
        }
    }

    static Plan plan(LabContext ctx, double[] ranges, String defaultStances, String defaultAims, boolean overridable) {
        List<Integer> stances = indices(ctx.param("stances", defaultStances), STANCES, "stance");
        List<Integer> aims = indices(ctx.param("aims", defaultAims), AIMS, "aim");
        List<Double> bearings = bearings(ctx.param("bearings", "0"));
        int n = (int) ctx.paramNum("hit_samples", 2000);
        String moaCsv = overridable ? ctx.param("moas", null) : null;
        double[] override = moaCsv == null ? null : bearings(moaCsv).stream().mapToDouble(Double::doubleValue).toArray();
        List<LabLauncher> launchers = ctx.launchers();
        LabFlight.Sample[][] flights = new LabFlight.Sample[launchers.size()][];
        List<Integer> ix = new ArrayList<>();
        for (int i = 0; i < flights.length; i++) {
            ix.add(i);
        }
        LabRows.each(ctx.executor(), ix, i -> flights[i] = LabFlight.fly(LabRows.preset(launchers.get(i)),
                launchers.get(i).muzzleVelocity(), ranges));
        Collection<Cell> cells = new LinkedHashSet<>();
        for (int i = 0; i < flights.length; i++) {
            for (LabFlight.Sample s : flights[i]) {
                if (s.reached()) {
                    double[] moas = override != null ? override : new double[]{launchers.get(i).dispersionMoa()};
                    for (double moa : moas) {
                        cells.add(new Cell(moa, s.range()));
                    }
                }
            }
        }
        Body[][] bodies = bodies(ctx.level(), stances, IDLE_PHASES);
        Field field = solve(ctx.executor(), bodies, cells, stances, aims, bearings, n, ctx.seed());
        return new Plan(launchers, flights, stances, aims, bearings, override, field, n);
    }

    /** Calling (server) thread: stance requests, ticks and rig solve. */
    static Body[][] bodies(ServerLevel level, List<Integer> stances, int[] phases) {
        Body[][] out = new Body[STANCES.length][];
        BlockPos spawn = level.getSharedSpawnPos();
        double y = Math.max(spawn.getY(), level.getMinBuildHeight()) + 40.0;
        for (int s : stances) {
            out[s] = new Body[phases.length];
            for (int k = 0; k < phases.length; k++) {
                out[s][k] = posed(level, spawn, y, s, phases[k]);
            }
        }
        return out;
    }

    private static Body posed(ServerLevel level, BlockPos spawn, double y, int s, int tickCount) {
        FakePlayer v = LabRows.victim(level);
        v.moveTo(spawn.getX() + 0.5, y, spawn.getZ() + 0.5, 0.0F, 0.0F);
        v.setYHeadRot(0.0F);
        v.yBodyRot = 0.0F;
        v.yBodyRotO = 0.0F;
        v.setPose(Pose.STANDING);
        switch (STANCES[s]) {
            case "sneak" -> {
                v.setShiftKeyDown(true);
                v.setPose(Pose.CROUCHING);
            }
            case "kneel" -> request(v, Posture.KNEEL);
            case "prone" -> request(v, Posture.PRONE);
            default -> {
            }
        }
        if (s >= 2) {
            for (int i = 0; i < SETTLE_TICKS; i++) {
                StanceLogic.serverTick(v);
            }
        }
        v.tickCount = tickCount;
        return body(STANCES[s], v);
    }

    private static void request(FakePlayer v, Posture p) {
        if (!AhfStance.request(v, StanceRequest.STAND.withPosture(p))) {
            throw new IllegalStateException("stance " + p + " refused at " + v.position());
        }
    }

    static Body body(String stance, FakePlayer v) {
        HumanoidRig.LocalRig rig = RigCache.resolve(v);
        rig.all();
        double yaw = Math.toRadians(v.yBodyRot);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-front.z, 0.0, front.x);
        Vec3 c = rig.torso.center().add(rig.chest.center()).scale(0.5);
        return new Body(stance, v, v.yBodyRot, world(v.position(), right, front, c),
                world(v.position(), right, front, rig.head.center()));
    }

    private static Vec3 world(Vec3 pos, Vec3 right, Vec3 front, Vec3 local) {
        return pos.add(right.scale(local.x)).add(0.0, local.y, 0.0).add(front.scale(local.z));
    }

    /**
     * Fills a {@link Field} for {@code cells} x the listed stance / aim / bearing indices on
     * {@code pool}. Cell results depend on the cell and {@code seed} only.
     */
    static Field solve(ExecutorService pool, Body[][] bodies, Collection<Cell> cells, List<Integer> stances,
                       List<Integer> aims, List<Double> bearings, int n, long seed) {
        Field f = new Field(bearings);
        List<Key> keys = new ArrayList<>();
        for (Cell c : cells) {
            for (int s : stances) {
                for (int a : aims) {
                    for (int b = 0; b < bearings.size(); b++) {
                        keys.add(new Key(c.moa(), c.range(), s, a, b));
                    }
                }
            }
        }
        LabRows.each(pool, keys, k -> f.cells.put(k, sample(bodies[k.stance()], k, bearings.get(k.bearing()), n, seed)));
        return f;
    }

    private static Dist sample(Body[] phases, Key k, double bearing, int n, long seed) {
        long mix = seed;
        for (long part : new long[]{Double.doubleToLongBits(k.moa()), Double.doubleToLongBits(k.range()), k.stance(),
                k.aim(), Double.doubleToLongBits(bearing)}) {
            mix = (mix ^ part) * MIX;
            mix ^= mix >>> 29;
        }
        RandomSource random = RandomSource.create(mix);
        float deg = (float) (k.moa() / 60.0);
        int[] parts = new int[PARTS.length];
        int other = 0;
        int miss = 0;
        double lead = Math.max(k.range() - REACH, 0.0);
        for (int i = 0; i < n; i++) {
            Body b = phases[i % phases.length];
            double yaw = Math.toRadians(b.yaw() + bearing);
            Vec3 toShooter = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
            Vec3 muzzle = b.aim(k.aim()).add(toShooter.scale(k.range()));
            Vec3 d = Rounds.spread(toShooter.scale(-1.0), deg, random);
            Rounds.LiveHit h = Rounds.hitLive(b.victim(), muzzle.add(d.scale(lead)), muzzle.add(d.scale(k.range() + REACH)));
            if (h == null) {
                miss++;
            } else if (h.part() == null) {
                other++;
            } else {
                parts[h.part().ordinal()]++;
            }
        }
        return new Dist(parts, other, miss, n);
    }
}
