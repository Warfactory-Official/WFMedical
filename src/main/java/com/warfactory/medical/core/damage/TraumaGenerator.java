package com.warfactory.medical.core.damage;

import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public final class TraumaGenerator {

    private static final String BRUISE = "bruise";
    private static final String LACERATION_SMALL = "laceration_small";
    private static final String LACERATION_LARGE = "laceration_large";
    private static final String FRACTURE = "fracture";
    private static final String BURN = "burn";
    private static final String INTERNAL_BLEEDING = "internal_bleeding";
    private static final String PUNCTURE = "puncture";
    private static final String CRUSH_INJURY = "crush_injury";
    private static final String RADIATION_BURN = "radiation_burn";
    private static final String CHEMICAL_BURN = "chemical_burn";
    private static final String BLUNT_FORCE_TRAUMA = "blunt_force_trauma";

    // Fall damage reaching us is roughly (fallDistance - 3) health points, so ~2 energy ~= a 5-block fall and
    // ~7 energy ~= a 10-block fall. The fracture floor is configurable (fallFractureMinBlocks, default 5 blocks):
    // below it a fall is pure blunt-force trauma (soft, self-healing, no bleed, no crush); at/above it a bone
    // may break, with the chance ramping up over this energy range.
    private static final float FALL_FRACTURE_RANGE = 16.0F;
    // Vanilla fall damage is roughly (fallDistance - 3), so convert the block floor to a fall-damage energy.
    private static final float FALL_DAMAGE_FREE_BLOCKS = 3.0F;

    private static final float MAJOR_ENERGY = 4.0F;

    private TraumaGenerator() {
    }

    public static List<Trauma> generate(DamageCategory cat, ArmorEvaluation.Outcome outcome,
                                        LimbType limb, float energy, TraumaRegistry registry,
                                        long nowTick, RandomSource rand) {
        List<Trauma> out = new ArrayList<>(3);
        if (registry == null || limb == null) {
            return out;
        }
        DamageCategory category = cat == null ? DamageCategory.GENERIC : cat;
        ArmorEvaluation.Outcome result = outcome == null ? ArmorEvaluation.Outcome.FULL : outcome;
        float e = Math.max(energy, 0.0F);
        float energyFactor = clampF(e * 0.1F, 0.1F, 1.5F);

        switch (category) {
            case FIRE -> {
                add(out, registry, BURN, TraumaCategory.BURN, limb, 0.9F * energyFactor, nowTick);
                return out;
            }
            case RADIATION -> {
                add(out, registry, RADIATION_BURN, TraumaCategory.RADIATION_BURN, limb, 0.8F * energyFactor, nowTick);
                return out;
            }
            case CHEMICAL -> {
                add(out, registry, CHEMICAL_BURN, TraumaCategory.CHEMICAL_BURN, limb, 0.8F * energyFactor, nowTick);
                return out;
            }
            case EXPLOSION -> {
                add(out, registry, CRUSH_INJURY, TraumaCategory.CRUSH_INJURY, limb, energyFactor, nowTick);
                add(out, registry, BURN, TraumaCategory.BURN, limb, 0.6F * energyFactor, nowTick);
                maybeFracture(out, registry, limb, nowTick, rand, fractureChance(category, limb, energyFactor));
                return out;
            }
            case FALL -> {
                // Falls are blunt force: soft-tissue trauma that regenerates on its own and never bleeds,
                // but it costs current health (~12 HP per severity). Energy ~= fall damage ~= fallDistance-3,
                // so e*0.085 makes a 10-block fall (e~7) ~0.6 severity ~= a vanilla ~7 HP hit.
                // A landing at/above fallFractureMinBlocks (default 5 blocks) additionally risks breaking a bone.
                float blunt = clampF(e * 0.085F, 0.08F, 1.0F);
                add(out, registry, BLUNT_FORCE_TRAUMA, TraumaCategory.BRUISE, limb, blunt, nowTick);
                // A fall is never a crush injury; the only escalation past bruising is a possible fracture.
                maybeFracture(out, registry, limb, nowTick, rand, fallFractureChance(limb, e));
                return out;
            }
            case UNARMED -> {
                // Fists are blunt force too -- a light current-health hit.
                add(out, registry, BLUNT_FORCE_TRAUMA, TraumaCategory.BRUISE, limb, 0.12F, nowTick);
                return out;
            }
            default -> {
            }
        }

        switch (result) {
            case BLOCKED -> {
                add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.5F, nowTick);
                return out;
            }
            case PARTIAL -> {
                add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.5F, nowTick);
                add(out, registry, LACERATION_SMALL, TraumaCategory.LACERATION, limb, 0.5F, nowTick);
                return out;
            }
            default -> {
                if (category == DamageCategory.BALLISTIC) {
                    // One round is one wound channel: the puncture is the entry and the large laceration is the
                    // tear it opens on the way through. The extra small laceration that used to ride along was
                    // the same wound counted a third time, so a single hit now reads as two wounds plus at most
                    // one complication (see the cap in add()).
                    add(out, registry, PUNCTURE, TraumaCategory.PUNCTURE, limb, 0.9F * energyFactor, nowTick);
                    add(out, registry, LACERATION_LARGE, TraumaCategory.LACERATION, limb, 0.7F * energyFactor, nowTick);
                    // Bone before organs: a broken limb is the characteristic complication of a hit to one,
                    // and internal bleeding there is already a long shot. On the trunk there is no bone in the
                    // rig to break, so the order only decides which one survives the cap on a limb.
                    maybeFracture(out, registry, limb, nowTick, rand, fractureChance(category, limb, energyFactor));
                    maybeInternalBleeding(out, registry, limb, nowTick, rand, e, 0.6F * energyFactor,
                            internalBleedingChanceFor(category, limb, e));
                    return out;
                }
                boolean impact = category == DamageCategory.BLUNT;
                if (e >= MAJOR_ENERGY) {
                    if (impact) {
                        add(out, registry, CRUSH_INJURY, TraumaCategory.CRUSH_INJURY, limb, 0.8F * energyFactor, nowTick);
                        add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.5F, nowTick);
                    } else {
                        add(out, registry, LACERATION_LARGE, TraumaCategory.LACERATION, limb, 0.8F * energyFactor, nowTick);
                        maybeFracture(out, registry, limb, nowTick, rand, fractureChance(category, limb, energyFactor));
                        maybeInternalBleeding(out, registry, limb, nowTick, rand, e, 0.5F * energyFactor,
                                internalBleedingChanceFor(category, limb, e));
                        return out;
                    }
                    maybeFracture(out, registry, limb, nowTick, rand, fractureChance(category, limb, energyFactor));
                } else if (impact) {
                    // Light blunt blows (fists, weak bonks) are blunt force, not open wounds.
                    add(out, registry, BLUNT_FORCE_TRAUMA, TraumaCategory.BRUISE, limb, 0.15F, nowTick);
                } else {
                    add(out, registry, LACERATION_SMALL, TraumaCategory.LACERATION, limb, 0.45F + 0.08F * e, nowTick);
                }
                return out;
            }
        }
    }

    private static float fractureChance(DamageCategory cat, LimbType limb, float energyFactor) {
        float base = switch (cat) {
            case BALLISTIC -> 0.35F;
            case EXPLOSION -> 0.5F;
            case BLUNT -> 0.4F;
            default -> 0.2F;
        };
        return clampF(base * energyFactor, 0.0F, 0.85F);
    }

    private static float fallFractureChance(LimbType limb, float energy) {
        float thresholdEnergy = Math.max(0.0F, (float) MedicalConfig.fallFractureMinBlocks() - FALL_DAMAGE_FREE_BLOCKS);
        if (energy < thresholdEnergy) {
            return 0.0F;
        }
        float t = clampF((energy - thresholdEnergy) / FALL_FRACTURE_RANGE, 0.0F, 1.0F);
        float chance = 0.15F + 0.7F * t;
        if (!limb.isLeg()) {
            chance *= 0.4F;
        }
        return clampF(chance, 0.0F, 0.9F);
    }

    /**
     * Internal bleeding is a deep organ/vessel injury, not the default outcome of being shot: it is permanent,
     * bleeds harder than anything else and no field dressing reaches it. It needs a wound deep enough to have
     * penetrated (energy above the threshold) and is far likelier in the trunk than in a limb.
     */
    static float internalBleedingChanceFor(DamageCategory cat, LimbType limb, float energy) {
        if (energy < (float) MedicalConfig.internalBleedingMinEnergy()) {
            return 0.0F;
        }
        float base = (float) MedicalConfig.internalBleedingChance();
        if (cat == DamageCategory.EXPLOSION) {
            base *= (float) MedicalConfig.internalBleedingExplosionMultiplier();
        }
        if (!(limb == LimbType.TORSO || limb == LimbType.HEAD)) {
            base *= (float) MedicalConfig.internalBleedingLimbMultiplier();
        }
        return clampF(base, 0.0F, 1.0F);
    }

    private static void maybeInternalBleeding(List<Trauma> out, TraumaRegistry registry, LimbType limb,
                                              long nowTick, RandomSource rand, float energy, float severity,
                                              float chance) {
        if (chance <= 0.0F) {
            return;
        }
        // No RNG available (deterministic call sites, e.g. tests): fall back to the energy gate alone so the
        // wound is still reachable rather than silently impossible.
        if (rand == null) {
            if (energy < (float) MedicalConfig.internalBleedingMinEnergy()) {
                return;
            }
        } else if (rand.nextFloat() >= chance) {
            return;
        }
        add(out, registry, INTERNAL_BLEEDING, TraumaCategory.INTERNAL_BLEEDING, limb, severity, nowTick);
    }

    private static void maybeFracture(List<Trauma> out, TraumaRegistry registry, LimbType limb,
                                      long nowTick, RandomSource rand, float chance) {
        if (!(limb.isArm() || limb.isLeg())) {
            return;
        }
        if (rand != null && chance > 0.0F && rand.nextFloat() < chance) {
            add(out, registry, FRACTURE, TraumaCategory.FRACTURE, limb, 1.0F, nowTick);
        }
    }

    /**
     * Every wound a hit produces funnels through here, and every damage path lists its primary wounds first
     * and its rolled complications (internal bleeding, fracture) last. That makes the cap an ordering rule
     * rather than a lottery: a hit never loses its wound channel, only the third thing piled on top of it.
     */
    private static void add(List<Trauma> out, TraumaRegistry registry, String id, TraumaCategory category,
                            LimbType limb, float severity, long nowTick) {
        int cap = MedicalConfig.maxTraumasPerHit();
        if (cap > 0 && out.size() >= cap) {
            return;
        }
        TraumaType type = resolve(registry, id, category);
        if (type == null) {
            return;
        }
        float sev = severity * type.getSeverityContribution();
        if (sev <= 0.0F) {
            sev = 0.01F;
        }
        out.add(new Trauma(type, limb, sev, nowTick));
    }

    private static TraumaType resolve(TraumaRegistry registry, String id, TraumaCategory category) {
        TraumaType type = registry.get(id);
        if (type == null) {
            type = registry.firstOfCategory(category);
        }
        return type;
    }

    private static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (Math.min(v, hi));
    }
}
