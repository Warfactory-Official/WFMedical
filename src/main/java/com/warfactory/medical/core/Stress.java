package com.warfactory.medical.core;

/** Stress (fear), 0..1: pain and suppression raise it, calm time lowers it, faster the higher it is. Pure. */
public final class Stress {

    /**
     * @param painThreshold    perceived pain above which pain raises stress
     * @param painGain         stress/s at full pain
     * @param regenBase        stress/s calm recovery floor
     * @param regenScale       extra stress/s per unit stress while calm
     * @param regenDelayTicks  calm needs this long since the last rise
     * @param darkLightLevel   light at or below this => no recovery
     * @param suppressionGain  stress per round passing at zero distance
     * @param suppressionRange blocks; gain falls linearly to 0 at this distance
     * @param cigaretteRelief  stress removed by one cigarette
     */
    public record Params(boolean enabled, float painThreshold, float painGain, float regenBase, float regenScale,
                         int regenDelayTicks, int darkLightLevel, float suppressionGain, float suppressionRange,
                         float cigaretteRelief) {
    }

    private Stress() {
    }

    /** Stress/s from pain: 0 at {@code painThreshold}, {@code painGain} at pain 1. */
    public static float painRate(float pain, Params p) {
        if (pain <= p.painThreshold()) {
            return 0.0F;
        }
        return p.painGain() * Math.min((pain - p.painThreshold()) / (1.0F - p.painThreshold()), 1.0F);
    }

    /** Stress/s recovered at {@code stress} while calm. */
    public static float regenRate(float stress, Params p) {
        return p.regenBase() + p.regenScale() * stress;
    }

    /** Stress added by a round passing {@code distance} blocks from the eyes. */
    public static float suppression(double distance, Params p) {
        if (distance >= p.suppressionRange()) {
            return 0.0F;
        }
        return p.suppressionGain() * (1.0F - (float) (distance / p.suppressionRange()));
    }

    /**
     * @param calm light above {@code darkLightLevel} and no rise within {@code regenDelayTicks}
     * @return stress after {@code seconds}; pain rise wins over recovery in the same step
     */
    public static float advance(float stress, float pain, boolean calm, double seconds, Params p) {
        float rise = painRate(pain, p);
        float next = stress + (float) (rise * seconds);
        if (rise == 0.0F && calm) {
            next -= (float) (regenRate(stress, p) * seconds);
        }
        return Math.clamp(next, 0.0F, 1.0F);
    }
}
