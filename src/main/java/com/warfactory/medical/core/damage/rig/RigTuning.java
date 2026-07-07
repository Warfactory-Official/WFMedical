package com.warfactory.medical.core.damage.rig;

import com.warfactory.medical.core.limb.LimbType;

/**
 * DEBUG-ONLY live tuning for the {@link HumanoidRig} limb boxes. Holds a per-limb, per-field additive delta
 * (in model units, 1/16 block) that {@link HumanoidRig#compute} folds onto each part's fixed base spec so
 * the six OBBs can be nudged in-game (position + size) until they line up, then baked back into the source.
 *
 * <p><b>Zero cost when off.</b> The hot path only reads {@link #deltas()} when {@link #ACTIVE} is true; with
 * {@code hitlocation.hitboxDebug=false} (the default) the rig is built from the literal base spec with no
 * tuning work at all. {@link #ACTIVE} mirrors that config flag at load and can be flipped live by the
 * {@code /wfmedical hitbox debug} command.</p>
 *
 * <p><b>Threading.</b> The delta array is published copy-on-write behind a {@code volatile} reference, so
 * {@link #deltas()} readers (the server hit thread and the client render thread) always see a fully-built
 * snapshot without locking; the (rare) command-thread mutators are {@code synchronized} to serialise writes.
 * In single-player these statics are shared between the integrated server and the client, so a command nudge
 * moves both the actual hit boxes and the debug overlay at once — the intended tuning loop.</p>
 */
public final class RigTuning {

    /**
     * Number of tunable scalars per limb (matches {@link Field} and the leading nine {@code Part} ctor args).
     */
    public static final int FIELDS = 9;

    /**
     * The nine tunable scalars of a rig part, in {@code Part}-constructor order (all model units, 1/16 block).
     */
    public enum Field {
        OX, OY, OZ,   // cube min-corner offset from the pivot
        SX, SY, SZ,   // cube size
        PX, PY, PZ;   // pivot (rotation centre + base translation)

        public static final Field[] VALUES = values();

        public static Field fromString(String s) {
            for (Field f : VALUES) {
                if (f.name().equalsIgnoreCase(s)) {
                    return f;
                }
            }
            return null;
        }

        public String lower() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * HOT-PATH FLAG. When false (normal play), {@link HumanoidRig#compute} never touches the tuning at all.
     * Volatile: written from the command/config thread, read from the server hit thread and render thread.
     */
    public static volatile boolean ACTIVE = false;

    /**
     * Per-limb additive deltas, flat: {@code deltas[limb.ordinal()*FIELDS + field.ordinal()]} (model units).
     * Copy-on-write — mutators publish a fresh array so readers of {@link #deltas()} never see a torn state.
     */
    private static volatile double[] deltas = new double[LimbType.VALUES.length * FIELDS];

    /**
     * Limb currently emphasised by the debug overlay (the last one tuned), or {@code null} for none.
     */
    public static volatile LimbType highlight = null;

    private RigTuning() {
    }

    /**
     * The live delta snapshot; index with {@link #index}. Read-only for callers — never mutate the returned
     * array (mutators replace it wholesale).
     */
    public static double[] deltas() {
        return deltas;
    }

    public static int index(LimbType limb, Field f) {
        return limb.ordinal() * FIELDS + f.ordinal();
    }

    public static double delta(LimbType limb, Field f) {
        return deltas[index(limb, f)];
    }

    /**
     * Set one limb/field delta to an absolute value (model units) and mark that limb as the render highlight.
     */
    public static synchronized void set(LimbType limb, Field f, double value) {
        double[] next = deltas.clone();
        next[index(limb, f)] = value;
        deltas = next;
        highlight = limb;
    }

    /**
     * Nudge one limb/field delta by {@code amount} (model units); returns the new delta and highlights the limb.
     */
    public static synchronized double add(LimbType limb, Field f, double amount) {
        double[] next = deltas.clone();
        int idx = index(limb, f);
        double v = next[idx] + amount;
        next[idx] = v;
        deltas = next;
        highlight = limb;
        return v;
    }

    /**
     * Clear every delta on every limb.
     */
    public static synchronized void reset() {
        deltas = new double[LimbType.VALUES.length * FIELDS];
        highlight = null;
    }

    /**
     * Clear every delta on one limb.
     */
    public static synchronized void reset(LimbType limb) {
        double[] next = deltas.clone();
        int b = limb.ordinal() * FIELDS;
        for (int i = 0; i < FIELDS; i++) {
            next[b + i] = 0.0;
        }
        deltas = next;
    }

    /**
     * True if any limb currently carries a non-zero delta.
     */
    public static boolean hasAnyTuning() {
        double[] d = deltas;
        for (double v : d) {
            if (v != 0.0) {
                return true;
            }
        }
        return false;
    }
}
