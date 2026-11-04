package com.warfactory.medical.lab;

import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.PhysiologyParams;
import com.warfactory.medical.body.limb.LimbType;
import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabArmors;
import com.wf.wflib.labs.api.LabContext;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Round x launcher x range x garment (+ inserts) x limb: real {@code DamageEvents.resolveHit} on a seeded victim, then
 * the untreated body forward. Params: {@code wound_samples=} (default 32), {@code armor=} glob, ranges default
 * {@code 0,100,300,800} unless {@code ranges=}. Damage = the round's direct hit; no headshot factor (WFMedical
 * claims the body, limb decides).
 */
public final class WoundsProducer implements LabProducer {
    public static final String ID = "wfmedical:wounds";
    static final double[] DEFAULT_RANGES = {0, 100, 300, 800};

    static final LabTable TABLE = LabTable.of("wfmedical:wounds")
            .dimStr("launcher").dimStr("round").dimStr("content_origin").dimNum("range_m", "m")
            .dimStr("armor").dimStr("limb")
            .measure("speed_ms", "m/s").measure("damage_in", "hp")
            .measureStr("armor_outcome").measure("armor_wear_frac", "")
            .measure("p_major_trauma", "").measure("p_fracture", "").measure("p_internal_bleeding", "")
            .measureStr("wound_mode")
            .measure("bleed_ml_tick", "ml/tick").measure("pain", "").measure("effective_max_hp", "hp")
            .measure("p_incapacitated", "").measure("t_incapacitated_s", "s")
            .measure("p_dead", "").measure("t_dead_s", "s")
            .measure("samples", "").measure("horizon_s", "s")
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public LabTable table() {
        return TABLE;
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        double[] ranges = ctx.param("ranges", null) != null ? ctx.ranges() : DEFAULT_RANGES;
        int samples = (int) ctx.paramNum("wound_samples", 32);
        List<ArmorSetups.Setup> sets = LabArmors.sets(ctx);
        PhysiologyParams params = MedicalConfig.toPhysiologyParams();
        Map<String, WoundModel.Sim> cache = new ConcurrentHashMap<>();
        ServerLevel level = ctx.level();
        long seed = ctx.seed();
        LabRows.eachWithVictim(ctx.executor(), level, ctx.launchers(), (l, victim) -> launcher(level, l, ranges, sets,
                samples, seed, params, cache, sink, victim));
    }

    private static void launcher(ServerLevel level, LabLauncher l, double[] ranges, List<ArmorSetups.Setup> sets,
                                 int samples, long seed, PhysiologyParams params,
                                 Map<String, WoundModel.Sim> cache, LabSink sink, FakePlayer victim) {
        KineticPreset p = LabRows.preset(l);
        String launcherOrigin = LabRows.origin(l);
        for (LabFlight.Sample s : LabFlight.fly(p, l.muzzleVelocity(), ranges)) {
            if (!s.reached()) {
                continue;
            }
            Vec3 vel = new Vec3(s.speed() / 20.0, 0.0, 0.0);
            double damage = Rounds.impactDamage(p, vel);
            for (LimbType limb : LimbType.VALUES) {
                row(sink, level, victim, l, p, launcherOrigin, s, "none", null, null, EquipmentSlot.CHEST, limb, vel,
                        damage, samples, seed, params, cache);
            }
            for (ArmorSetups.Setup set : sets) {
                for (EquipmentSlot slot : set.covers()) {
                    for (LimbType limb : WoundModel.limbsOf(slot)) {
                        row(sink, level, victim, l, p, launcherOrigin, s, set.label(), set.dev() ? "dev" : null,
                                set.stack(), set.slot(), limb, vel, damage, samples, seed, params, cache);
                    }
                }
            }
        }
    }

    static void row(LabSink sink, ServerLevel level, FakePlayer victim, LabLauncher l, KineticPreset p,
                    String launcherOrigin, LabFlight.Sample s, String armor, String armorOrigin,
                    net.minecraft.world.item.ItemStack stack, EquipmentSlot slot, LimbType limb, Vec3 vel,
                    double damage, int samples, long seed, PhysiologyParams params,
                    Map<String, WoundModel.Sim> cache) {
        WoundAgg a = WoundAgg.run(level, victim, stack, slot, limb, p, vel, damage, l.damageScale(), samples, seed,
                params, cache);
        String origin = armorOrigin != null ? LabContents.DEV_ORIGIN : launcherOrigin;
        sink.row(l.id(), p.id().toString(), origin, s.range(), armor, WoundModel.limbName(limb), s.speed(),
                damage * l.damageScale(), a.armorMode(), a.mean(a.wear, a.n), a.p(a.major), a.p(a.fractures),
                a.p(a.internals), a.woundMode(), a.mean(a.bleed, a.woundN), a.mean(a.pain, a.woundN),
                a.mean(a.maxHp, a.woundN), a.p(a.incapN), a.mean(a.incapSum, a.incapN), a.p(a.deathN),
                a.mean(a.deathSum, a.deathN), a.n, WoundModel.HORIZON_S);
    }
}
