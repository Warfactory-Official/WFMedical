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
        float e = energy < 0.0F ? 0.0F : energy;
        // Growth factor from remaining energy; clamped so a single huge hit still stays bounded.
        float energyFactor = clampF(e * 0.1F, 0.1F, 1.5F);

        switch (category) {
            case FIRE:
                add(out, registry, BURN, TraumaCategory.BURN, limb, 0.9F * energyFactor, nowTick);
                return out;
            case RADIATION:
                add(out, registry, RADIATION_BURN, TraumaCategory.RADIATION_BURN, limb, 0.8F * energyFactor, nowTick);
                return out;
            case CHEMICAL:
                add(out, registry, CHEMICAL_BURN, TraumaCategory.CHEMICAL_BURN, limb, 0.8F * energyFactor, nowTick);
                return out;
            case EXPLOSION:
                // Blast -> crushing + burning; heavy blasts can fracture.
                add(out, registry, CRUSH_INJURY, TraumaCategory.CRUSH_INJURY, limb, 1.0F * energyFactor, nowTick);
                add(out, registry, BURN, TraumaCategory.BURN, limb, 0.6F * energyFactor, nowTick);
                maybeFracture(out, registry, limb, nowTick, rand, fractureChance(category, limb, energyFactor));
                return out;
            case FALL:
                // Impact -> bruising, with real fracture risk on legs.
                add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.7F * energyFactor, nowTick);
                maybeFracture(out, registry, limb, nowTick, rand, fractureChance(category, limb, energyFactor));
                return out;
            default:
                break;
        }

        // Kinetic categories: BALLISTIC, SLASHING, PIERCING, BLUNT, GENERIC. Driven by armor outcome.
        switch (result) {
            case BLOCKED:
                add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.4F * energyFactor, nowTick);
                return out;
            case PARTIAL:
                add(out, registry, BRUISE, TraumaCategory.BRUISE, limb, 0.5F * energyFactor, nowTick);
                add(out, registry, LACERATION_SMALL, TraumaCategory.LACERATION, limb, 0.6F * energyFactor, nowTick);
                return out;
            case FULL:
            default:
                if (category == DamageCategory.BALLISTIC) {
                    // Bullet defeats armor -> puncture + large tearing + bleeding, plus fracture chance.
                    add(out, registry, PUNCTURE, TraumaCategory.PUNCTURE, limb, 0.9F * energyFactor, nowTick);
                    add(out, registry, LACERATION_LARGE, TraumaCategory.LACERATION, limb, 0.7F * energyFactor, nowTick);
                    add(out, registry, INTERNAL_BLEEDING, TraumaCategory.INTERNAL_BLEEDING, limb, 0.6F * energyFactor, nowTick);
                } else {
                    add(out, registry, LACERATION_LARGE, TraumaCategory.LACERATION, limb, 0.8F * energyFactor, nowTick);
                    add(out, registry, INTERNAL_BLEEDING, TraumaCategory.INTERNAL_BLEEDING, limb, 0.5F * energyFactor, nowTick);
                }
                maybeFracture(out, registry, limb, nowTick, rand, fractureChance(category, limb, energyFactor));
                return out;
        }
    }

    private static float fractureChance(DamageCategory cat, LimbType limb, float energyFactor) {
        float base;
        switch (cat) {
            case BALLISTIC:
                base = 0.35F;
                break;
            case EXPLOSION:
                base = 0.5F;
                break;
            case FALL:
                base = limb.isLeg() ? 0.6F : 0.2F;
                break;
            case BLUNT:
                base = 0.4F;
                break;
            default:
                base = 0.2F;
                break;
        }
        return clampF(base * energyFactor, 0.0F, 0.85F);
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
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
