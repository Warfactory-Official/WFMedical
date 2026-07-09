package com.warfactory.medical.core.damage;

import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a classified, armor-evaluated hit into concrete {@link Trauma} objects following the design
 * doc's Armor Integration table. The caller is responsible for merging the returned list into the
 * profile (via {@code Limb.tryMerge}); this class only builds instances and does not touch any profile.
 *
 * <p>Trauma types are resolved by id from the registry, falling back to
 * {@link TraumaRegistry#firstOfCategory} when a specific id is absent, so missing config entries never
 * crash the pipeline. Severity scales with the remaining {@code energy} of the hit.</p>
 */
public final class TraumaGenerator {

    // Canonical trauma ids; must line up with the config default definitions.
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

    // Fall tuning. The energy the generator sees is (roughly) the vanilla fall damage, i.e. fall-blocks - 3,
    // so energy 2 ~ 5 blocks, energy 5 ~ 8 blocks, energy 21 ~ 24 blocks. Below IMPACT a fall is a harmless
    // stumble (light bruise); at/above it a scaling crush injury forms (pain + health hit); at/above FRACTURE
    // a leg-break chance opens up and ramps across RANGE.
    private static final float FALL_IMPACT_ENERGY = 2.0F;    // ~5 blocks: falls start to actually hurt
    private static final float FALL_FRACTURE_ENERGY = 5.0F;  // ~8 blocks: leg fractures become possible
    private static final float FALL_FRACTURE_RANGE = 16.0F;  // fracture chance reaches its cap by ~24 blocks

    /**
     * Raw damage a fully-penetrating kinetic hit must deal to carve a MAJOR wound. Below it a hit is only a
     * scratch/bruise (minor), which still accumulates toward a major wound via {@link TraumaEscalation}. So
     * bushes, weak mobs and glancing blows chip minor trauma; solid hits wound outright (and add a scratch too).
     */
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
        // Growth factor from remaining energy; clamped so a single huge hit still stays bounded.
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
                // Blast -> crushing + burning; heavy blasts can fracture.
                add(out, registry, CRUSH_INJURY, TraumaCategory.CRUSH_INJURY, limb, energyFactor, nowTick);
                add(out, registry, BURN, TraumaCategory.BURN, limb, 0.6F * energyFactor, nowTick);
                maybeFracture(out, registry, limb, nowTick, rand, fractureChance(category, limb, energyFactor));
                return out;
            }
            case FALL -> {
                if (e < FALL_IMPACT_ENERGY) {
                    // A short drop (~<5 blocks): just a light bruise (a stumble), no real harm.
                    add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.6F * energyFactor, nowTick);
                    return out;
                }
                // A real fall: a crushing impact injury that scales with height (pain + health hit + slowed
                // legs), plus a leg-fracture chance from ~8 blocks up. A hard landing pushes the limb past its
                // health cap into an external bleed (bandage-able, else you bleed out), and a big enough drop
                // instant-kills via the lethality fraction upstream — so falls hurt, break legs, and can kill.
                float crush = clampF(0.45F + (e - FALL_IMPACT_ENERGY) * 0.06F, 0.45F, 1.25F);
                add(out, registry, CRUSH_INJURY, TraumaCategory.CRUSH_INJURY, limb, crush, nowTick);
                maybeFracture(out, registry, limb, nowTick, rand, fallFractureChance(limb, e));
                return out;
            }
            case UNARMED -> {
                // A punch bruises and stings a little; it never one-shots, but repeated blows stack (via
                // TraumaEscalation) into internal bleeding -- so beating someone with fists eventually matters
                // instead of doing almost nothing.
                add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.6F, nowTick);
                return out;
            }
            default -> {
            }
        }

        // Kinetic categories: BALLISTIC, SLASHING, PIERCING, BLUNT, GENERIC. Driven by armor outcome.
        switch (result) {
            case BLOCKED -> {
                // Armor ate it: just bruising -- still stacks toward internal bleeding under a sustained beating.
                add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.5F, nowTick);
                return out;
            }
            case PARTIAL -> {
                // Glanced off armor: a bruise + a scratch, both minor.
                add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.5F, nowTick);
                add(out, registry, LACERATION_SMALL, TraumaCategory.LACERATION, limb, 0.5F, nowTick);
                return out;
            }
            default -> {
                // FULL penetration. Bullets are always serious; other kinetic hits only carve a MAJOR wound
                // once they land hard enough (>= MAJOR_ENERGY). A light/glancing hit is just a scratch/bruise
                // that accumulates. A major wound always comes WITH a minor injury, so minor rides with major.
                if (category == DamageCategory.BALLISTIC) {
                    add(out, registry, PUNCTURE, TraumaCategory.PUNCTURE, limb, 0.9F * energyFactor, nowTick);
                    add(out, registry, LACERATION_LARGE, TraumaCategory.LACERATION, limb, 0.7F * energyFactor, nowTick);
                    add(out, registry, INTERNAL_BLEEDING, TraumaCategory.INTERNAL_BLEEDING, limb, 0.6F * energyFactor, nowTick);
                    add(out, registry, LACERATION_SMALL, TraumaCategory.LACERATION, limb, 0.5F, nowTick);
                    maybeFracture(out, registry, limb, nowTick, rand, fractureChance(category, limb, energyFactor));
                    return out;
                }
                boolean impact = category == DamageCategory.BLUNT;
                if (e >= MAJOR_ENERGY) {
                    if (impact) {
                        add(out, registry, CRUSH_INJURY, TraumaCategory.CRUSH_INJURY, limb, 0.8F * energyFactor, nowTick);
                        add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.5F, nowTick);
                    } else {
                        add(out, registry, LACERATION_LARGE, TraumaCategory.LACERATION, limb, 0.8F * energyFactor, nowTick);
                        add(out, registry, INTERNAL_BLEEDING, TraumaCategory.INTERNAL_BLEEDING, limb, 0.5F * energyFactor, nowTick);
                        add(out, registry, LACERATION_SMALL, TraumaCategory.LACERATION, limb, 0.5F, nowTick);
                    }
                    maybeFracture(out, registry, limb, nowTick, rand, fractureChance(category, limb, energyFactor));
                } else if (impact) {
                    // Light impact: a bruise (stacks toward internal bleeding).
                    add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.5F, nowTick);
                } else {
                    // Light/glancing cut (a bush, a weak swipe): just a scratch -- minor bleed + minor pain --
                    // that accumulates toward a real laceration.
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

    /**
     * Fall-specific fracture odds by raw fall energy (~fall-blocks - 3): nothing below ~8 blocks, then a ramp
     * from ~15% up to ~85% by a big drop. A non-leg landing (torso/back) breaks far less readily than a leg.
     */
    private static float fallFractureChance(LimbType limb, float energy) {
        if (energy < FALL_FRACTURE_ENERGY) {
            return 0.0F;
        }
        float t = clampF((energy - FALL_FRACTURE_ENERGY) / FALL_FRACTURE_RANGE, 0.0F, 1.0F);
        float chance = 0.15F + 0.7F * t;
        if (!limb.isLeg()) {
            chance *= 0.4F;
        }
        return clampF(chance, 0.0F, 0.9F);
    }

    private static void maybeFracture(List<Trauma> out, TraumaRegistry registry, LimbType limb,
                                      long nowTick, RandomSource rand, float chance) {
        if (rand != null && chance > 0.0F && rand.nextFloat() < chance) {
            add(out, registry, FRACTURE, TraumaCategory.FRACTURE, limb, 1.0F, nowTick);
        }
    }

    private static void add(List<Trauma> out, TraumaRegistry registry, String id, TraumaCategory category,
                            LimbType limb, float severity, long nowTick) {
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
