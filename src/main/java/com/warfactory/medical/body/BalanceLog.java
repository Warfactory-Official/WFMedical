package com.warfactory.medical.body;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.limb.Limb;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.body.trauma.Trauma;
import com.wf.wflib.armor.api.ArmorOutcome;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Balance telemetry: one {@code [balance]} line per hit and per state change, for measuring time-to-kill.
 * Off by default; {@code /wfmedical balance on}. Server thread only, so the hit scratch fields need no locking.
 */
public final class BalanceLog {

    private static volatile boolean on;
    private static String limbs = "";
    private static ArmorOutcome outcome;
    private static float through;
    private static float lethal;
    private static boolean limbDowned;

    private BalanceLog() {
    }

    public static boolean on() {
        return on;
    }

    public static void set(boolean value) {
        on = value;
    }

    /** From {@code resolveHit}: what armour and the lethal gate made of this hit. */
    public static void resolved(String limbNames, ArmorOutcome armor, float throughEnergy, float lethalEnergy, boolean limbDown) {
        limbs = limbNames;
        outcome = armor;
        through = throughEnergy;
        lethal = lethalEnergy;
        limbDowned = limbDown;
    }

    public static void hit(LivingEntity victim, DamageSource src, Object cat, float amount, boolean wasDowned,
                    boolean finishedOff, boolean instakill, MedicalProfile profile) {
        DerivedStats s = profile.cached();
        StringBuilder wounds = new StringBuilder();
        for (LimbType lt : LimbType.values()) {
            Limb limb = profile.limb(lt);
            if (limb.getTraumas().isEmpty()) {
                continue;
            }
            wounds.append(lt.name().toLowerCase(Locale.ROOT)).append('[');
            for (Trauma t : limb.getTraumas()) {
                wounds.append(t.getType().getId()).append(':').append(String.format("%.2f", t.getSeverity())).append(' ');
            }
            wounds.append("] ");
        }
        WFMedical.LOGGER.info("[balance] tick={} victim={} by={} src={} cat={} in={} limbs={} armor={} through={} lethal={} "
                        + "limbDown={} wasDowned={} finish={} instakill={} state={} blood={} bleed={} effMax={} effCur={} pain={} wounds={}",
                victim.level().getGameTime(), victim.getName().getString(),
                src.getEntity() == null ? "-" : src.getEntity().getName().getString(), src.getMsgId(), cat,
                f(amount), limbs, outcome, f(through), f(lethal), limbDowned, wasDowned, finishedOff, instakill,
                s.state(), (int) profile.getBloodMl(), f((float) s.totalBleeding()), f(s.effectiveMaxHealth()),
                f(s.effectiveCurrentHealth()), f(s.systemicPain()), wounds.toString().trim());
    }

    public static void transition(LivingEntity who, HealthState from, HealthState to, MedicalProfile profile) {
        WFMedical.LOGGER.info("[balance] tick={} victim={} transition {} -> {} blood={} bleed={}",
                who.level().getGameTime(), who.getName().getString(), from, to, (int) profile.getBloodMl(),
                f((float) profile.cached().totalBleeding()));
    }

    private static String f(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
