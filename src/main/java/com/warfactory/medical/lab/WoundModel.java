package com.warfactory.medical.lab;

import com.warfactory.medical.body.HealthState;
import com.warfactory.medical.body.DerivedStats;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.MedicalEngine;
import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.PhysiologyParams;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.body.trauma.Trauma;
import com.warfactory.medical.damage.DamageEvents;
import com.wf.wflib.armor.api.ArmorOutcome;
import com.wf.wflib.armor.api.ArmorStacks;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.RoundDamageSource;
import com.wf.wflib.round.api.Rounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One round hitting one limb through the real {@link DamageEvents#resolveHit} (WFLib per-limb armour, trauma
 * generation, escalation), then the real body step forward untreated: {@code bleedAndRegen}, {@code advanceTrauma},
 * {@code settle} every {@code updateIntervalTicks}. Not simulated: stress, substances, wake-up, treatment.
 */
final class WoundModel {
    static final double HORIZON_S = 1800.0;

    private WoundModel() {
    }

    /** @param incap seconds to first UNCONSCIOUS/DEAD, NaN never within the horizon; death likewise */
    record Sim(double incap, double death, double bleedTick, double pain, double effectiveMaxHealth,
               HealthState state) {
    }

    record Outcome(boolean major, ArmorOutcome armor, String traumas, boolean fracture,
                   boolean internal, double armorWear, Sim sim) {
    }

    static Outcome hit(ServerLevel level, FakePlayer victim, @Nullable ItemStack armor, EquipmentSlot slot,
                       LimbType limb, KineticPreset p, Vec3 vel, double damage, double damageScale, long seed,
                       PhysiologyParams params, Map<String, Sim> cache) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            victim.setItemSlot(s, ItemStack.EMPTY);
        }
        ItemStack worn = armor == null ? null : armor.copy();
        if (worn != null) {
            victim.setItemSlot(slot, worn);
        }
        double before = worn == null ? 1.0 : ArmorStacks.conditionFraction(worn);
        victim.getRandom().setSeed(seed);
        Threat threat = Rounds.threat(p, vel);
        RoundDamageSource src = Rounds.source(level, p, null, null, threat, Vec3.ZERO, vel, 0L, vel,
                RoundDamageSource.LIVE, List.of(limb.part()));
        MedicalProfile profile = new MedicalProfile(MedicalConfig.maxBloodMl());
        DamageEvents.HurtResolution res = DamageEvents.resolveHit(victim, src, (float) (damage * damageScale), profile);
        double wear = worn == null ? 0.0 : before - ArmorStacks.conditionFraction(worn);
        if (res.majorTrauma()) {
            return new Outcome(true, res.armor(), "major", false, false, wear,
                    new Sim(0.0, 0.0, Double.NaN, Double.NaN, 0.0, HealthState.DEAD));
        }
        List<Trauma> traumas = profile.allTraumas();
        StringBuilder key = new StringBuilder(profile.isUnconsciousLatched() ? "down;" : "");
        boolean fracture = false;
        boolean internal = false;
        for (Trauma t : traumas) {
            String id = t.getType().getId();
            key.append(t.getLimb().name()).append(':').append(id).append(':')
                    .append(Float.floatToIntBits(t.getSeverity())).append(';');
            fracture |= t.isFracture();
            internal |= id.contains("internal");
        }
        String text = text(traumas);
        String sig = key.toString();
        Sim sim = cache.get(sig);
        if (sim == null) {
            sim = run(profile, params);
            cache.put(sig, sim);
        }
        return new Outcome(false, res.armor(), text, fracture, internal, wear, sim);
    }

    /** {@code type:severity;...}, profile order. */
    static String text(List<Trauma> traumas) {
        StringBuilder text = new StringBuilder();
        for (Trauma t : traumas) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(t.getType().getId().toLowerCase(Locale.ROOT)).append(':')
                    .append(Math.round(t.getSeverity() * 100.0f) / 100.0);
        }
        return text.toString();
    }

    static Sim run(MedicalProfile profile, PhysiologyParams params) {
        int interval = Math.max(1, MedicalConfig.updateIntervalTicks());
        long tick = 0;
        DerivedStats stats = MedicalEngine.settle(profile, params, tick);
        double incap = Double.NaN;
        double death = Double.NaN;
        double bleed0 = stats.totalBleeding();
        double pain0 = stats.totalPain();
        double maxHp0 = stats.effectiveMaxHealth();
        HealthState state0 = stats.state();
        long limit = (long) (HORIZON_S * 20.0);
        while (tick <= limit) {
            if (Double.isNaN(incap) && stats.state().ordinal() >= HealthState.UNCONSCIOUS.ordinal()) {
                incap = tick / 20.0;
            }
            if (stats.state() == HealthState.DEAD) {
                death = tick / 20.0;
                break;
            }
            MedicalEngine.bleedAndRegen(profile, params, interval);
            MedicalEngine.advanceTrauma(profile, interval);
            tick += interval;
            stats = MedicalEngine.settle(profile, params, tick);
        }
        return new Sim(incap, death, bleed0, pain0, maxHp0, state0);
    }

    static List<LimbType> limbsOf(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> List.of(LimbType.HEAD);
            case CHEST -> List.of(LimbType.TORSO, LimbType.LEFT_ARM, LimbType.RIGHT_ARM);
            case LEGS -> List.of(LimbType.LEFT_LEG, LimbType.RIGHT_LEG);
            default -> new ArrayList<>();
        };
    }

    static String limbName(LimbType l) {
        return l.name().toLowerCase(Locale.ROOT);
    }
}
