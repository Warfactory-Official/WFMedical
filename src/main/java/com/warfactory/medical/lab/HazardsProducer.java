package com.warfactory.medical.lab;

import com.warfactory.medical.body.DerivedStats;
import com.warfactory.medical.body.HealthState;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.MedicalEngine;
import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.PhysiologyParams;
import com.warfactory.medical.damage.DamageEvents;
import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.armor.api.ArmorStacks;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabArmors;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabHazard;
import com.wf.wflib.labs.api.LabHazards;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabRows;
import com.wf.wflib.labs.api.LabShelter;
import com.wf.wflib.labs.api.LabShelters;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Damaging hazards on a player body over an exposure: one real {@code DamageEvents.resolveHit} per hit interval (limb
 * picked by the real hit sampler, armour per limb, wear applied), the body stepping in between, then untreated to
 * incapacitation / death. Params: {@code durations=} s (default 10,30,120), {@code wound_samples=} (default 4),
 * {@code armor=} glob, {@code inserts=1} adds plate-filled sets. Radiation, anomalies, psy, EMP and item hazards
 * are not damage-hit hazards in this stack and are not mapped here.
 */
public final class HazardsProducer implements LabProducer {
    public static final String ID = "wfmedical:hazards";

    private static final LabTable TABLE = LabTable.of("wfmedical:hazards")
            .dimStr("hazard").dimStr("kind").dimStr("content_origin").dimNum("intensity", "x")
            .dimStr("armor").dimStr("shelter").dimNum("duration_s", "s")
            .measure("amount_after_shelter", "hp").measure("hits", "")
            .measure("p_major_trauma", "").measure("armor_wear_frac", "").measure("wounds", "")
            .measure("bleed_ml_tick", "ml/tick").measure("pain", "")
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

    private record Cell(LabHazard hazard, double intensity) {
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        boolean inserts = "1".equals(ctx.param("inserts", "0"));
        List<ArmorSetups.Setup> sets = LabArmors.sets(ctx).stream()
                .filter(s -> inserts || s.inserts().isEmpty()).toList();
        double[] durations = Arrays.stream(ctx.param("durations", "10,30,120").split(","))
                .mapToDouble(Double::parseDouble).toArray();
        int samples = (int) ctx.paramNum("wound_samples", 4);
        List<LabShelter> shelters = LabShelters.all();
        List<Cell> cells = new ArrayList<>();
        for (LabHazard h : LabHazards.REGISTRY.values()) {
            if (h.damageType() != null && h.damage() > 0.0) {
                for (double i : h.intensities()) {
                    cells.add(new Cell(h, i));
                }
            }
        }
        PhysiologyParams params = MedicalConfig.toPhysiologyParams();
        ServerLevel level = ctx.level();
        long seed = ctx.seed();
        double[][] sheltered = LabShelters.shield(level, shelters, cells, c -> c.hazard().kind(),
                c -> c.hazard().damage() * c.intensity());
        List<Integer> ix = java.util.stream.IntStream.range(0, cells.size()).boxed().toList();
        LabRows.eachWithVictim(ctx.executor(), level, ix, (i, victim) ->
                cell(level, cells.get(i), sets, durations, shelters, sheltered[i], samples, seed, params, sink, victim));
    }

    private static void cell(ServerLevel level, Cell c, List<ArmorSetups.Setup> sets, double[] durations,
                             List<LabShelter> shelters, double[] sheltered, int samples, long seed,
                             PhysiologyParams params, LabSink sink, FakePlayer victim) {
        LabHazard h = c.hazard();
        DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse(h.damageType()))));
        for (int j = -1; j < shelters.size(); j++) {
            LabShelter shelter = j < 0 ? null : shelters.get(j);
            double after = j < 0 ? h.damage() * c.intensity() : sheltered[j];
            for (double d : durations) {
                one(sink, h, c.intensity(), "none", null, shelter, after, d, src, samples, seed, params, victim);
                for (ArmorSetups.Setup set : sets) {
                    one(sink, h, c.intensity(), set.label(), set, shelter, after, d, src, samples, seed, params,
                            victim);
                }
            }
        }
    }

    private static void one(LabSink sink, LabHazard h, double intensity, String armor, ArmorSetups.Setup set,
                            LabShelter shelter, double after, double duration, DamageSource src, int samples,
                            long seed, PhysiologyParams params, FakePlayer victim) {
        int step = Math.max(1, MedicalConfig.updateIntervalTicks());
        int hitEvery = Math.max(step, h.hitIntervalTicks());
        long hits = (long) (duration * 20.0 / hitEvery);
        long horizon = (long) (WoundModel.HORIZON_S * 20.0);
        int major = 0;
        int incapN = 0;
        int deadN = 0;
        double incapSum = 0;
        double deadSum = 0;
        double wear = 0;
        double wounds = 0;
        double bleed = 0;
        double pain = 0;
        for (int s = 0; s < samples; s++) {
            for (EquipmentSlot z : ArmorSetups.worn()) {
                victim.setItemSlot(z, ItemStack.EMPTY);
            }
            ItemStack worn = set == null ? null : set.stack().copy();
            if (worn != null) {
                victim.setItemSlot(set.slot(), worn);
            }
            double before = worn == null ? 1.0 : ArmorStacks.conditionFraction(worn);
            victim.getRandom().setSeed(seed + s * 0x9E3779B97F4A7C15L);
            MedicalProfile profile = new MedicalProfile(MedicalConfig.maxBloodMl());
            long tick = 0;
            double incap = Double.NaN;
            double death = Double.NaN;
            boolean dead = false;
            DerivedStats stats = MedicalEngine.settle(profile, params, 0);
            for (long k = 0; k < hits && after > 0.0 && !dead; k++) {
                DamageEvents.HurtResolution res = DamageEvents.resolveHit(victim, src, (float) after, profile);
                if (res.majorTrauma()) {
                    major++;
                    incap = 0;
                    death = tick / 20.0;
                    dead = true;
                    break;
                }
                for (int t = 0; t < hitEvery; t += step) {
                    MedicalEngine.bleedAndRegen(profile, params, step);
                    MedicalEngine.advanceTrauma(profile, step);
                    tick += step;
                    stats = MedicalEngine.settle(profile, params, tick);
                    if (Double.isNaN(incap) && stats.state().ordinal() >= HealthState.UNCONSCIOUS.ordinal()) {
                        incap = tick / 20.0;
                    }
                    if (stats.state() == HealthState.DEAD) {
                        death = tick / 20.0;
                        dead = true;
                        break;
                    }
                }
            }
            wounds += profile.allTraumas().size();
            bleed += stats.totalBleeding();
            pain += stats.totalPain();
            while (!dead && tick <= horizon) {
                MedicalEngine.bleedAndRegen(profile, params, step);
                MedicalEngine.advanceTrauma(profile, step);
                tick += step;
                stats = MedicalEngine.settle(profile, params, tick);
                if (Double.isNaN(incap) && stats.state().ordinal() >= HealthState.UNCONSCIOUS.ordinal()) {
                    incap = tick / 20.0;
                }
                if (stats.state() == HealthState.DEAD) {
                    death = tick / 20.0;
                    dead = true;
                }
            }
            if (!Double.isNaN(incap)) {
                incapN++;
                incapSum += incap;
            }
            if (!Double.isNaN(death)) {
                deadN++;
                deadSum += death;
            }
            wear += worn == null ? 0.0 : before - ArmorStacks.conditionFraction(worn);
        }
        String origin = set != null && set.dev() ? LabContents.DEV_ORIGIN : LabContents.SHIPPED;
        sink.row(h.id(), h.kind(), origin, intensity, armor, shelter == null ? "none" : shelter.id(), duration, after,
                hits, (double) major / samples, wear / samples, wounds / samples, bleed / samples, pain / samples,
                (double) incapN / samples, incapN == 0 ? null : incapSum / incapN, (double) deadN / samples,
                deadN == 0 ? null : deadSum / deadN, samples, WoundModel.HORIZON_S);
    }
}
