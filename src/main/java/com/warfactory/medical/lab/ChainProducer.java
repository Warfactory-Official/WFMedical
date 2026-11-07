package com.warfactory.medical.lab;

import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.PhysiologyParams;
import com.warfactory.medical.body.limb.LimbType;
import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabArmors;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabCover;
import com.wf.wflib.labs.api.LabFlight;
import com.wf.wflib.labs.api.LabLauncher;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabRows;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.Rounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The whole stack on the JVM: launcher dispersion ({@code Rounds.spread}) onto a posed body ({@link HitZones}: AHF rig
 * OBBs), flight ({@code RoundArc}), cover ({@code Ricochet} / {@code BlockPen}), armour and wounds
 * ({@code DamageEvents.resolveHit}), untreated body. Wound columns are conditional on the round perforating the
 * cover. Limb rows: {@code p_hit} = share of shots whose first struck part is the limb, {@code *_total} =
 * {@code p_hit x p_cover_perforate x p}. Row {@code limb=all}: {@code p_hit} = share hitting any part, probabilities
 * weighted by the part shares (conditional on a hit), {@code *_total} summed over the six limbs (a limb the garment
 * does not cover answers bare); other wound columns null.
 * Params: {@code covers=block@thickness@angle,...} (default below, plus {@code none}), {@code limbs=} (default
 * {@code head,torso}), {@code stances=} (default {@code stand}), {@code aims=} (default {@code centre}),
 * {@code bearings=} (first value), {@code moas=}, {@code hit_samples=}, {@code armor=}, {@code wound_samples=} (default 16), ranges
 * default {@code 0,100,300,800}.
 */
public final class ChainProducer implements LabProducer {
    public static final String ID = "wfmedical:chain";
    static final String DEFAULT_COVERS = "minecraft:dirt@1@0,minecraft:oak_planks@0.5@0,minecraft:stone@0.5@0,"
            + "minecraft:stone@1@0,minecraft:iron_block@0.5@0,minecraft:stone@1@70";

    private static final LabTable TABLE = LabTable.of("wfmedical:chain")
            .dimStr("launcher").dimStr("round").dimStr("content_origin").dimNum("range_m", "m")
            .dimNum("dispersion_moa", "moa").dimStr("cover").dimNum("thickness_m", "m").dimNum("angle_deg", "deg").dimStr("armor").dimStr("limb").dimStr("stance").dimStr("aim")
            .measure("speed_ms", "m/s").measure("p_hit", "")
            .measure("p_cover_perforate", "").measure("p_cover_ricochet", "").measure("p_cover_stop", "")
            .measure("exit_speed_ms", "m/s").measure("damage_in", "hp")
            .measureStr("armor_outcome").measure("armor_wear_frac", "")
            .measure("p_major_trauma", "").measure("p_fracture", "").measure("p_internal_bleeding", "")
            .measureStr("wound_mode").measure("bleed_ml_tick", "ml/tick").measure("pain", "")
            .measure("p_incapacitated", "").measure("t_incapacitated_s", "s")
            .measure("p_dead", "").measure("t_dead_s", "s")
            .measure("p_incapacitated_total", "").measure("p_dead_total", "")
            .measure("samples", "")
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public LabTable table() {
        return TABLE;
    }

    private record Cover(String block, double thickness, double angle, LabCover.Material material) {
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        double[] ranges = ctx.param("ranges", null) != null ? ctx.ranges() : WoundsProducer.DEFAULT_RANGES;
        int samples = (int) ctx.paramNum("wound_samples", 16);
        int coverSamples = (int) ctx.paramNum("samples", 128);
        List<ArmorSetups.Setup> sets = LabArmors.sets(ctx);
        List<Cover> covers = new ArrayList<>();
        covers.add(new Cover("none", 0.0, 0.0, null));
        for (String c : ctx.param("covers", DEFAULT_COVERS).split(",")) {
            if (c.isBlank()) {
                continue;
            }
            String[] t = c.split("@");
            covers.add(new Cover(t[0], Double.parseDouble(t[1]), Double.parseDouble(t[2]), LabCover.material(t[0])));
        }
        List<LimbType> limbs = Arrays.stream(ctx.param("limbs", "head,torso").split(","))
                .map(n -> LimbType.valueOf(n.trim().toUpperCase(java.util.Locale.ROOT))).toList();
        HitZones.Plan plan = HitZones.plan(ctx, ranges, "stand", "centre", true);
        PhysiologyParams params = MedicalConfig.toPhysiologyParams();
        Map<String, WoundModel.Sim> cache = new ConcurrentHashMap<>();
        ServerLevel level = ctx.level();
        long seed = ctx.seed();
        List<Integer> index = new ArrayList<>();
        for (int i = 0; i < plan.launchers().size(); i++) {
            index.add(i);
        }
        LabRows.eachWithVictim(ctx.executor(), level, index, (i, victim) -> launcher(level, plan, i, covers, limbs,
                sets, samples, coverSamples, seed, params, cache, sink, victim));
    }

    private static void launcher(ServerLevel level, HitZones.Plan plan, int index, List<Cover> covers,
                                 List<LimbType> limbs, List<ArmorSetups.Setup> sets, int samples, int coverSamples,
                                 long seed, PhysiologyParams params, Map<String, WoundModel.Sim> cache, LabSink sink,
                                 FakePlayer victim) {
        LabLauncher l = plan.launchers().get(index);
        KineticPreset p = LabRows.preset(l);
        String launcherOrigin = LabRows.origin(l);
        for (LabFlight.Sample s : plan.flights()[index]) {
            if (!s.reached()) {
                continue;
            }
            for (Cover c : covers) {
                double pPerf = 1.0;
                double pRic = 0.0;
                double pStop = 0.0;
                double exit = s.speed();
                if (c.material() != null) {
                    LabCover.Result r = LabCover.evaluate(p, s.speed(), c.material(), c.thickness(), c.angle(),
                            coverSamples, seed);
                    pPerf = r.pPerforate();
                    pRic = r.pRicochet();
                    pStop = r.pStop();
                    exit = r.exitSpeed();
                    if (r.verdict() == LabCover.Verdict.DETONATE) {
                        pPerf = 0.0;
                    }
                }
                Vec3 vel = new Vec3(exit / 20.0, 0.0, 0.0);
                double damage = Rounds.impactDamage(p, vel);
                Map<LimbType, WoundAgg> bare = new EnumMap<>(LimbType.class);
                if (pPerf > 0.0) {
                    for (LimbType limb : LimbType.VALUES) {
                        bare.put(limb, WoundAgg.run(level, victim, null, EquipmentSlot.CHEST, limb, p, vel, damage,
                                l.damageScale(), samples, seed, params, cache));
                    }
                }
                List<Target> targets = new ArrayList<>();
                targets.add(new Target("none", null, launcherOrigin, bare));
                for (ArmorSetups.Setup set : sets) {
                    Map<LimbType, WoundAgg> worn = new EnumMap<>(LimbType.class);
                    for (LimbType limb : LimbType.VALUES) {
                        if (set.covers().stream().anyMatch(sl -> WoundModel.limbsOf(sl).contains(limb))) {
                            worn.put(limb, pPerf > 0.0 ? WoundAgg.run(level, victim, set.stack(), set.slot(), limb, p,
                                    vel, damage, l.damageScale(), samples, seed, params, cache) : null);
                        }
                    }
                    if (!worn.isEmpty()) {
                        targets.add(new Target(set.label(), set, set.dev() ? LabContents.DEV_ORIGIN : launcherOrigin,
                                worn));
                    }
                }
                for (double moa : plan.moas(l)) {
                    for (int st : plan.stances()) {
                        for (int ai : plan.aims()) {
                            HitZones.Dist dist = plan.field().get(moa, s.range(), st, ai, 0);
                            for (Target t : targets) {
                                for (LimbType limb : limbs) {
                                    if (!t.worn().containsKey(limb) && t.set() != null) {
                                        continue;
                                    }
                                    double pHit = dist.part(limb.part());
                                    WoundAgg a = t.worn().get(limb);
                                    if (a == null && t.set() == null) {
                                        a = bare.get(limb);
                                    }
                                    double pi = a == null ? 0.0 : a.p(a.incapN);
                                    double pd = a == null ? 0.0 : a.p(a.deathN);
                                    sink.row(l.id(), p.id().toString(), t.origin(), s.range(), moa, c.block(), c.thickness(),
                                            c.angle(), t.label(), WoundModel.limbName(limb), HitZones.STANCES[st],
                                            HitZones.AIMS[ai], s.speed(), pHit, pPerf, pRic, pStop, exit,
                                            damage * l.damageScale(), a == null ? null : a.armorMode(),
                                            a == null ? null : a.mean(a.wear, a.n), a == null ? null : a.p(a.major),
                                            a == null ? null : a.p(a.fractures), a == null ? null : a.p(a.internals),
                                            a == null ? null : a.woundMode(), a == null ? null : a.mean(a.bleed, a.woundN),
                                            a == null ? null : a.mean(a.pain, a.woundN), a == null ? null : pi,
                                            a == null ? null : a.mean(a.incapSum, a.incapN), a == null ? null : pd,
                                            a == null ? null : a.mean(a.deathSum, a.deathN), pHit * pPerf * pi,
                                            pHit * pPerf * pd, a == null ? 0 : a.n);
                                }
                                all(sink, l, p, s, moa, c, t, bare, dist, st, ai, pPerf, pRic, pStop, exit, damage);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Wound answer of {@code t} per limb: its own where worn, else bare. */
    private record Target(String label, ArmorSetups.Setup set, String origin, Map<LimbType, WoundAgg> worn) {
    }

    private static void all(LabSink sink, LabLauncher l, KineticPreset p, LabFlight.Sample s, double moa, Cover c, Target t,
                            Map<LimbType, WoundAgg> bare, HitZones.Dist dist, int st, int ai, double pPerf,
                            double pRic, double pStop, double exit, double damage) {
        double hit = 0.0;
        double major = 0.0;
        double fracture = 0.0;
        double internal = 0.0;
        double pi = 0.0;
        double pd = 0.0;
        double ti = 0.0;
        double td = 0.0;
        int n = 0;
        for (LimbType limb : LimbType.VALUES) {
            double w = dist.part(limb.part());
            WoundAgg a = t.worn().containsKey(limb) ? t.worn().get(limb) : bare.get(limb);
            hit += w;
            if (a == null) {
                continue;
            }
            major += w * a.p(a.major);
            fracture += w * a.p(a.fractures);
            internal += w * a.p(a.internals);
            double wi = w * a.p(a.incapN);
            double wd = w * a.p(a.deathN);
            pi += wi;
            pd += wd;
            ti += a.incapN == 0 ? 0.0 : wi * a.mean(a.incapSum, a.incapN);
            td += a.deathN == 0 ? 0.0 : wd * a.mean(a.deathSum, a.deathN);
            n += a.n;
        }
        boolean wounds = pPerf > 0.0 && hit > 0.0;
        sink.row(l.id(), p.id().toString(), t.origin(), s.range(), moa, c.block(), c.thickness(), c.angle(), t.label(),
                "all", HitZones.STANCES[st], HitZones.AIMS[ai], s.speed(), hit, pPerf, pRic, pStop, exit,
                damage * l.damageScale(), null, null, wounds ? major / hit : null, wounds ? fracture / hit : null,
                wounds ? internal / hit : null, null, null, null, wounds ? pi / hit : null,
                wounds && pi > 0.0 ? ti / pi : null, wounds ? pd / hit : null, wounds && pd > 0.0 ? td / pd : null,
                pPerf * pi, pPerf * pd, n);
    }
}
