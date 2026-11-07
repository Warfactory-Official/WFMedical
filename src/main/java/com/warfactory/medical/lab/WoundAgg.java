package com.warfactory.medical.lab;

import com.warfactory.medical.body.PhysiologyParams;
import com.warfactory.medical.body.limb.LimbType;
import com.wf.wflib.armor.api.ArmorOutcome;
import com.wf.wflib.round.api.KineticPreset;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Seeded samples of {@link WoundModel#hit} folded into the table's measures. */
final class WoundAgg {
    private static final long STRIDE = 0x9E3779B97F4A7C15L;

    int n;
    int major;
    final EnumMap<ArmorOutcome, Integer> armor = new EnumMap<>(ArmorOutcome.class);
    int fractures;
    int internals;
    double wear;
    int incapN;
    double incapSum;
    int deathN;
    double deathSum;
    int woundN;
    double bleed;
    double pain;
    double maxHp;
    final Map<String, Integer> traumas = new HashMap<>();

    static WoundAgg run(ServerLevel level, FakePlayer victim, @Nullable ItemStack armor, EquipmentSlot slot,
                        LimbType limb, KineticPreset p, Vec3 vel, double damage, double scale, int samples, long seed,
                        PhysiologyParams params, Map<String, WoundModel.Sim> cache) {
        WoundAgg a = new WoundAgg();
        for (int i = 0; i < samples; i++) {
            a.add(WoundModel.hit(level, victim, armor, slot, limb, p, vel, damage, scale, seed + i * STRIDE, params,
                    cache));
        }
        return a;
    }

    private void add(WoundModel.Outcome o) {
        n++;
        armor.merge(o.armor(), 1, Integer::sum);
        wear += o.armorWear();
        if (o.major()) {
            major++;
        } else {
            woundN++;
            fractures += o.fracture() ? 1 : 0;
            internals += o.internal() ? 1 : 0;
            traumas.merge(o.traumas(), 1, Integer::sum);
            bleed += o.sim().bleedTick();
            pain += o.sim().pain();
            maxHp += o.sim().effectiveMaxHealth();
        }
        if (!Double.isNaN(o.sim().incap())) {
            incapN++;
            incapSum += o.sim().incap();
        }
        if (!Double.isNaN(o.sim().death())) {
            deathN++;
            deathSum += o.sim().death();
        }
    }

    double p(int count) {
        return n == 0 ? Double.NaN : (double) count / n;
    }

    double mean(double sum, int count) {
        return count == 0 ? Double.NaN : sum / count;
    }

    String armorMode() {
        return armor.entrySet().stream().max(Map.Entry.comparingByValue()).map(e -> e.getKey().name()
                .toLowerCase(java.util.Locale.ROOT)).orElse(null);
    }

    String woundMode() {
        return traumas.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }
}
