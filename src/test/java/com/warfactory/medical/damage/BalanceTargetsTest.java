package com.warfactory.medical.damage;

import com.warfactory.medical.body.HealthState;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.MedicalEngine;
import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.PhysiologyParams;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.body.trauma.TraumaTypes;
import com.warfactory.medical.support.Fixtures;
import com.warfactory.medical.support.TestConfig;
import com.wf.wflib.armor.api.ArmorOutcome;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Combat balance, as numbers. A "round" here is what the live telemetry showed a soldier's rifle delivering
 * (~10.7 energy unarmoured, ~0.9 through a plate carrier, ~5 for an AP round through plate). The targets are the
 * design intent: head shots kill, a handful of torso hits down, limbs are wrecked by one or two, plate buys
 * shots rather than immunity, and a downed casualty is helpless, not immune.
 */
class BalanceTargetsTest {

    private static final float RIFLE = 10.66F;
    private static final float RIFLE_ABSORBED = 0.10F;
    private static final float PLATE_THROUGH = 0.89F;
    private static final float PLATE_ABSORBED = 8.04F;
    private static final float AP_THROUGH = 5.5F;
    private static final float AP_ABSORBED = 5.2F;
    /** Rough aim distribution of a soldier shooting centre mass: head, torso, arms, legs. */
    private static final double[] ZONE_W = {0.10, 0.50, 0.22, 0.18};
    private static final LimbType[][] ZONES = {
            {LimbType.HEAD}, {LimbType.TORSO},
            {LimbType.LEFT_ARM, LimbType.RIGHT_ARM}, {LimbType.LEFT_LEG, LimbType.RIGHT_LEG}};

    private TraumaTypes.Table registry;
    private PhysiologyParams params;

    @BeforeAll
    static void load() {
        TestConfig.load();
    }

    private record Fight(int shotsToDown, int shotsToDead, boolean deadByHit) {
    }

    private MedicalProfile fresh() {
        registry = Fixtures.registry();
        params = MedicalConfig.toPhysiologyParams();
        return new MedicalProfile(params.maxBloodMl());
    }

    private HealthState hit(MedicalProfile p, LimbType limb, ArmorOutcome o, float through, float absorbed,
                            RandomSource rand, boolean[] dead) {
        boolean wasDowned = p.isDowned();
        if (!wasDowned) {
            p.setDownedHitEnergy(0.0F);
        }
        DamageEvents.applyLimbTrauma(DamageCategory.BALLISTIC, o, limb, through, absorbed, p, registry, 0L, rand);
        if (wasDowned) {
            float e = (through + (o == ArmorOutcome.REDUCED ? absorbed * (float) MedicalConfig.armorSpallFraction() : 0.0F)) * (limb == LimbType.HEAD ? (float) MedicalConfig.downedHeadMultiplier() : 1.0F);
            p.setDownedHitEnergy(p.getDownedHitEnergy() + e);
            if (MedicalConfig.downedFinishEnergy() > 0 && p.getDownedHitEnergy() >= MedicalConfig.downedFinishEnergy()) {
                dead[0] = true;
            }
        }
        // The instant-kill gate in resolveHit.
        if (o != ArmorOutcome.STOPPED && through >= MedicalConfig.maxHealthPoints()
                * DamageConfig.majorTraumaFraction(DamageCategory.BALLISTIC)) {
            dead[0] = true;
        }
        p.markDirty();
        return p.recompute(params).state();
    }

    @Test
    void headDiag() {
        MedicalProfile p = fresh();
        boolean[] dead = {false};
        HealthState s = hit(p, LimbType.HEAD, ArmorOutcome.THROUGH, RIFLE, RIFLE_ABSORBED, Fixtures.rand(1), dead);
        System.out.println("HEADDIAG state=" + s + " effMax=" + p.cached().effectiveMaxHealth() + " pain="
                + p.cached().systemicPain() + " instakill=" + params.headDepletionInstakill());
    }

    /** Shoot one body zone repeatedly until dead, counting shots to down and to death. */
    private Fight shootZone(LimbType[] zone, ArmorOutcome o, float through, float absorbed, long seed) {
        MedicalProfile p = fresh();
        RandomSource rand = Fixtures.rand(seed);
        boolean[] dead = {false};
        int down = -1;
        for (int shot = 1; shot <= 40; shot++) {
            LimbType limb = zone[rand.nextInt(zone.length)];
            HealthState s = hit(p, limb, o, through, absorbed, rand, dead);
            if (down < 0 && s.ordinal() >= HealthState.UNCONSCIOUS.ordinal()) {
                down = shot;
            }
            if (dead[0] || s == HealthState.DEAD) {
                return new Fight(down < 0 ? shot : down, shot, true);
            }
        }
        return new Fight(down, 40, false);
    }

    private double[] randomFights(ArmorOutcome o, float through, float absorbed, int trials) {
        double downSum = 0;
        double deadSum = 0;
        int downIn5 = 0;
        for (int t = 0; t < trials; t++) {
            RandomSource rand = Fixtures.rand(1000 + t);
            MedicalProfile p = fresh();
            boolean[] dead = {false};
            int down = -1;
            int death = -1;
            for (int shot = 1; shot <= 60; shot++) {
                double r = rand.nextDouble();
                int z = 0;
                for (double acc = 0; z < ZONE_W.length - 1 && (acc += ZONE_W[z]) < r; z++) {
                    // pick zone by cumulative weight
                }
                LimbType[] zone = ZONES[z];
                LimbType limb = zone[rand.nextInt(zone.length)];
                HealthState s = hit(p, limb, o, through, absorbed, rand, dead);
                if (down < 0 && s.ordinal() >= HealthState.UNCONSCIOUS.ordinal()) {
                    down = shot;
                }
                if (dead[0] || s == HealthState.DEAD) {
                    death = shot;
                    break;
                }
            }
            if (down < 0) {
                down = 60;
            }
            if (death < 0) {
                death = 60;
            }
            downSum += down;
            deadSum += death;
            if (down <= 5) {
                downIn5++;
            }
        }
        return new double[]{downSum / trials, deadSum / trials, downIn5 / (double) trials};
    }

    /** Seconds from going down to bleeding out, no help and no more hits. */
    private double bleedoutSeconds(LimbType[] zone, int shots) {
        MedicalProfile p = fresh();
        RandomSource rand = Fixtures.rand(7);
        boolean[] dead = {false};
        int n = 0;
        while (!p.isDowned() && n < 20) {
            hit(p, zone[rand.nextInt(zone.length)], ArmorOutcome.THROUGH, RIFLE, RIFLE_ABSORBED, rand, dead);
            n++;
        }
        if (!p.isDowned()) {
            return -1;
        }
        long tick = 0;
        while (p.recompute(params).state() != HealthState.DEAD && tick < 20 * 60 * 30) {
            MedicalEngine.bleedAndRegen(p, params, 20);
            p.markDirty();
            MedicalEngine.settle(p, params, tick);
            tick += 20;
            p.recompute(params);
        }
        return tick / 20.0;
    }

    @Test
    void report() {
        StringBuilder sb = new StringBuilder("\n=== BALANCE: shots to down / shots to dead (single zone, until dead) ===\n");
        String[] names = {"head", "torso", "arm", "leg"};
        Object[][] loads = {
                {"unarmoured rifle", ArmorOutcome.THROUGH, RIFLE, RIFLE_ABSORBED},
                {"plate vs rifle", ArmorOutcome.REDUCED, PLATE_THROUGH, PLATE_ABSORBED},
                {"AP vs plate", ArmorOutcome.REDUCED, AP_THROUGH, AP_ABSORBED}};
        for (Object[] ld : loads) {
            sb.append(String.format("%-18s", ld[0]));
            for (int z = 0; z < 4; z++) {
                double down = 0;
                double dead = 0;
                int n = 60;
                for (int t = 0; t < n; t++) {
                    Fight f = shootZone(ZONES[z], (ArmorOutcome) ld[1], (Float) ld[2], (Float) ld[3], 50 + t);
                    down += f.shotsToDown;
                    dead += f.shotsToDead;
                }
                sb.append(String.format("  %s %.1f/%.1f", names[z], down / n, dead / n));
            }
            sb.append('\n');
            double[] r = randomFights((ArmorOutcome) ld[1], (Float) ld[2], (Float) ld[3], 400);
            sb.append(String.format("%-18s  centre-mass fight: down in %.1f shots, dead in %.1f, P(down<=5)=%.2f%n", "", r[0], r[1], r[2]));
        }
        for (LimbType lt : new LimbType[]{LimbType.LEFT_ARM, LimbType.LEFT_LEG}) {
            MedicalProfile p = fresh();
            boolean[] dead = {false};
            int n = 0;
            float cap = MedicalConfig.healthShare(lt) * MedicalConfig.maxHealthPoints();
            while (n < 20) {
                n++;
                hit(p, lt, ArmorOutcome.THROUGH, RIFLE, RIFLE_ABSORBED, Fixtures.rand(3), dead);
                if (p.limb(lt).getCachedHealthReduction() >= cap) {
                    break;
                }
            }
            sb.append(String.format("unarmoured rifle: %s disabled after %d hit(s)%n", lt, n));
        }
        sb.append(String.format("bleed-out after torso downing: %.0fs   after leg-heavy: %.0fs%n",
                bleedoutSeconds(ZONES[1], 0), bleedoutSeconds(ZONES[3], 0)));
        System.out.println(sb);
    }

    private double mean(LimbType[] zone, ArmorOutcome o, float through, float absorbed, boolean toDeath) {
        double sum = 0;
        for (int t = 0; t < 60; t++) {
            Fight f = shootZone(zone, o, through, absorbed, 50 + t);
            sum += toDeath ? f.shotsToDead : f.shotsToDown;
        }
        return sum / 60;
    }

    @Test
    void headShotKillsAndTorsoDownsInAFewHits() {
        assertTrue(mean(ZONES[0], ArmorOutcome.THROUGH, RIFLE, RIFLE_ABSORBED, true) <= 1.01, "one unarmoured head hit kills");
        assertTrue(mean(ZONES[1], ArmorOutcome.THROUGH, RIFLE, RIFLE_ABSORBED, false) <= 3.0, "torso downs in 3 or fewer");
        assertTrue(mean(ZONES[1], ArmorOutcome.THROUGH, RIFLE, RIFLE_ABSORBED, true) <= 5.0, "a few more hits finish a downed torso");
    }

    @Test
    void aDownedCasualtyIsNotImmune() {
        assertTrue(mean(ZONES[1], ArmorOutcome.THROUGH, RIFLE, RIFLE_ABSORBED, true)
                < mean(ZONES[1], ArmorOutcome.THROUGH, RIFLE, RIFLE_ABSORBED, false) + 3.0,
                "two more rounds into a downed torso end it");
    }

    @Test
    void plateBuysShotsNotImmunity() {
        double plate = mean(ZONES[1], ArmorOutcome.REDUCED, PLATE_THROUGH, PLATE_ABSORBED, false);
        double ap = mean(ZONES[1], ArmorOutcome.REDUCED, AP_THROUGH, AP_ABSORBED, false);
        double bare = mean(ZONES[1], ArmorOutcome.THROUGH, RIFLE, RIFLE_ABSORBED, false);
        assertTrue(plate >= 4 && plate <= 10, "plate vs rifle torso: " + plate);
        assertTrue(ap >= 2.5 && ap <= 4.5, "AP vs plate torso: " + ap);
        assertTrue(plate > ap && ap > bare, "bare < AP-through-plate < rifle-on-plate");
    }

    @Test
    void bleedOutIsSlowEnoughToBeRevivedFrom() {
        double s = bleedoutSeconds(ZONES[1], 0);
        assertTrue(s >= 150 && s <= 420, "torso bleed-out after downing took " + s + "s");
    }
}
