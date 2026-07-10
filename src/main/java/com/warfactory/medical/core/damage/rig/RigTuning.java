package com.warfactory.medical.core.damage.rig;

import com.warfactory.medical.core.limb.LimbType;

/**
 * DEBUG-ONLY live tuning for the {@link HumanoidRig} limb boxes. Holds a <b>per-pose</b>, per-limb, per-field
 * additive delta (in model units, 1/16 block) that {@link HumanoidRig#compute} folds onto each part's fixed
 * spec so the six OBBs can be nudged in-game (position + size) until they line up, then baked into the source.
 *
 * <p><b>Per pose.</b> Standing, crouching, prone (swim/crawl/elytra) and downed silhouettes are posed
 * differently, so each {@link RigPose} carries its own independent delta set; nudging while tuning the
 * crouch pose never disturbs the standing boxes and vice-versa.</p>
 *
 * <p><b>Zero cost when off.</b> The hot path only reads {@link #deltas()} when {@link #ACTIVE} is true; with
 * {@code hitlocation.hitboxDebug=false} (the default) the rig is built from its literal spec with no tuning
 * work at all. {@link #ACTIVE} mirrors that config flag at load and can be flipped live by the
 * {@code /wfmedical hitbox debug} command.</p>
 *
 * <p><b>Threading.</b> The delta array is published copy-on-write behind a {@code volatile} reference, so
 * {@link #deltas()} readers (the server hit thread and the client render thread) always see a fully-built
 * snapshot without locking; the (rare) command-thread mutators are {@code synchronized} to serialise writes.
 * In single-player these statics are shared between the integrated server and the client, so a command nudge
 * moves both the actual hit boxes and the debug overlay at once – the intended tuning loop.</p>
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
     * Pose profiles that carry independent limb-box tuning. Resolved from the victim by
     * {@code HumanoidRig.resolvePose} with priority {@code DOWNED > PRONE > CROUCHING > STANDING}.
     * {@code PRONE} covers every body-horizontal pose (swimming, crawling, elytra).
     */
    public enum RigPose {
        STANDING, CROUCHING, PRONE, DOWNED;

        public static final RigPose[] VALUES = values();

        public static RigPose fromString(String s) {
            for (RigPose p : VALUES) {
                if (p.name().equalsIgnoreCase(s)) {
                    return p;
                }
            }
            return null;
        }

        public String lower() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * What the hands are doing, an arms-only tuning overlay layered ON TOP of the stance. {@link #NONE} is the
     * relaxed base (no overlay – the arms sit at their stance position); the rest raise/reposition the arms
     * (bow draw, TACZ gun hold/ADS, shield block) and each carries its own per-stance arm adjustment. Resolved
     * from the victim's held items by {@code HumanoidRig.resolveHandAction}.
     */
    public enum HandAction {
        NONE, BOW, GUN, BLOCK;

        public static final HandAction[] VALUES = values();

        public static HandAction fromString(String s) {
            for (HandAction h : VALUES) {
                if (h.name().equalsIgnoreCase(s)) {
                    return h;
                }
            }
            return null;
        }

        public String lower() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final int LIMBS = LimbType.VALUES.length;
    private static final int PER_POSE = LIMBS * FIELDS;
    private static final int HAND_ACTIONS = HandAction.VALUES.length;
    private static final int PER_HAND_POSE = HAND_ACTIONS * LIMBS * FIELDS;

    /**
     * HOT-PATH FLAG. When false (normal play), {@link HumanoidRig#compute} never touches the tuning at all.
     * Volatile: written from the command/config thread, read from the server hit thread and render thread.
     */
    public static volatile boolean ACTIVE = false;

    /**
     * Per-pose/limb/field additive deltas, flat: {@code deltas[base(pose,limb) + field.ordinal()]} (model
     * units). Copy-on-write – mutators publish a fresh array so {@link #deltas()} readers never see a torn
     * state.
     */
    private static volatile double[] deltas = new double[RigPose.VALUES.length * PER_POSE];

    /**
     * Limb currently emphasised by the debug overlay (the last one tuned), or {@code null} for none.
     */
    public static volatile LimbType highlight = null;

    private RigTuning() {
    }

    /**
     * The live delta snapshot; index with {@link #base}/{@link #index}. Read-only for callers – never mutate
     * the returned array (mutators replace it wholesale).
     */
    public static double[] deltas() {
        return deltas;
    }

    /**
     * Flat index of the first ({@link Field#OX}) delta scalar for {@code pose}/{@code limb}.
     */
    public static int base(RigPose pose, LimbType limb) {
        return pose.ordinal() * PER_POSE + limb.ordinal() * FIELDS;
    }

    public static int index(RigPose pose, LimbType limb, Field f) {
        return base(pose, limb) + f.ordinal();
    }

    public static double delta(RigPose pose, LimbType limb, Field f) {
        return deltas[index(pose, limb, f)];
    }

    /**
     * Set one pose/limb/field delta to an absolute value (model units) and mark that limb as the highlight.
     */
    public static synchronized void set(RigPose pose, LimbType limb, Field f, double value) {
        double[] next = deltas.clone();
        next[index(pose, limb, f)] = value;
        deltas = next;
        highlight = limb;
    }

    /**
     * Nudge one pose/limb/field delta by {@code amount} (model units); returns the new delta, highlights limb.
     */
    public static synchronized double add(RigPose pose, LimbType limb, Field f, double amount) {
        double[] next = deltas.clone();
        int idx = index(pose, limb, f);
        double v = next[idx] + amount;
        next[idx] = v;
        deltas = next;
        highlight = limb;
        return v;
    }

    /**
     * Clear every delta on every pose.
     */
    public static synchronized void reset() {
        deltas = new double[RigPose.VALUES.length * PER_POSE];
        highlight = null;
    }

    /**
     * Clear every delta on one pose.
     */
    public static synchronized void reset(RigPose pose) {
        double[] next = deltas.clone();
        int b = pose.ordinal() * PER_POSE;
        for (int i = 0; i < PER_POSE; i++) {
            next[b + i] = 0.0;
        }
        deltas = next;
    }

    /**
     * Clear every delta on one pose/limb.
     */
    public static synchronized void reset(RigPose pose, LimbType limb) {
        double[] next = deltas.clone();
        int b = base(pose, limb);
        for (int i = 0; i < FIELDS; i++) {
            next[b + i] = 0.0;
        }
        deltas = next;
    }

    /**
     * True if {@code pose}/{@code limb} carries any non-zero delta.
     */
    public static boolean hasTuning(RigPose pose, LimbType limb) {
        double[] d = deltas;
        int b = base(pose, limb);
        for (int i = 0; i < FIELDS; i++) {
            if (d[b + i] != 0.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Count of non-zero delta scalars across every pose.
     */
    public static int tunedCount() {
        double[] d = deltas;
        int n = 0;
        for (double v : d) {
            if (v != 0.0) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- per-(stance, hand-action) ARM overlay

    /**
     * Arms-only overlay deltas, added on top of the stance deltas when a hand action is active, flat:
     * {@code handDeltas[handBase(pose,hand,limb) + field.ordinal()]} (model units). Only the arm limbs and the
     * non-{@link HandAction#NONE} actions are ever written; the rest stay zero. Copy-on-write like {@link #deltas}.
     */
    private static volatile double[] handDeltas = new double[RigPose.VALUES.length * PER_HAND_POSE];

    public static double[] handDeltas() {
        return handDeltas;
    }

    /**
     * Flat index of the first ({@link Field#OX}) hand-overlay scalar for {@code pose}/{@code hand}/{@code limb}.
     */
    public static int handBase(RigPose pose, HandAction hand, LimbType limb) {
        return pose.ordinal() * PER_HAND_POSE + hand.ordinal() * (LIMBS * FIELDS) + limb.ordinal() * FIELDS;
    }

    public static double handDelta(RigPose pose, HandAction hand, LimbType limb, Field f) {
        return handDeltas[handBase(pose, hand, limb) + f.ordinal()];
    }

    public static synchronized void setHand(RigPose pose, HandAction hand, LimbType limb, Field f, double value) {
        double[] next = handDeltas.clone();
        next[handBase(pose, hand, limb) + f.ordinal()] = value;
        handDeltas = next;
        highlight = limb;
    }

    public static synchronized double addHand(RigPose pose, HandAction hand, LimbType limb, Field f, double amount) {
        double[] next = handDeltas.clone();
        int idx = handBase(pose, hand, limb) + f.ordinal();
        double v = next[idx] + amount;
        next[idx] = v;
        handDeltas = next;
        highlight = limb;
        return v;
    }

    public static synchronized void resetHand(RigPose pose, HandAction hand) {
        double[] next = handDeltas.clone();
        int b = pose.ordinal() * PER_HAND_POSE + hand.ordinal() * (LIMBS * FIELDS);
        for (int i = 0; i < LIMBS * FIELDS; i++) {
            next[b + i] = 0.0;
        }
        handDeltas = next;
    }

    public static synchronized void resetHand(RigPose pose, HandAction hand, LimbType limb) {
        double[] next = handDeltas.clone();
        int b = handBase(pose, hand, limb);
        for (int i = 0; i < FIELDS; i++) {
            next[b + i] = 0.0;
        }
        handDeltas = next;
    }

    // ---------------------------------------------------------------- per-stance envelope reach

    /**
     * The broad-phase envelope reach axes: {@link #HORIZONTAL} = X/Z per side, {@link #VERTICAL} = Y top+bottom.
     */
    public enum EnvAxis {
        HORIZONTAL, VERTICAL;

        public static final EnvAxis[] VALUES = values();

        public static EnvAxis fromString(String s) {
            if (s == null) {
                return null;
            }
            String t = s.toLowerCase(java.util.Locale.ROOT);
            if (t.equals("h") || t.equals("horizontal")) {
                return HORIZONTAL;
            }
            if (t.equals("v") || t.equals("vertical")) {
                return VERTICAL;
            }
            return null;
        }

        public String lower() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * The LIVE per-stance broad-phase envelope reach (blocks), flat {@code [pose*2 + axis]}. Seeded from the
     * config at load/reload ({@link #seedEnvelope}); the {@code /wfmedical hitbox envelope} commands mutate it
     * copy-on-write. {@code MedicalHitReg} reads it while tuning is {@link #ACTIVE} (otherwise it reads the
     * config directly), so a nudge resizes the drawn envelope and the real hit-scan box together.
     */
    private static volatile double[] envelope = new double[RigPose.VALUES.length * 2];

    public static double envReach(RigPose pose, EnvAxis axis) {
        return envelope[pose.ordinal() * 2 + axis.ordinal()];
    }

    /**
     * Replace the whole live envelope table (used to seed it from config). {@code hv} is {@code [pose*2+axis]}.
     */
    public static synchronized void seedEnvelope(double[] hv) {
        envelope = hv.clone();
    }

    public static synchronized void setEnv(RigPose pose, EnvAxis axis, double value) {
        double[] next = envelope.clone();
        next[pose.ordinal() * 2 + axis.ordinal()] = Math.max(0.0, value);
        envelope = next;
    }

    public static synchronized double addEnv(RigPose pose, EnvAxis axis, double amount) {
        double[] next = envelope.clone();
        int i = pose.ordinal() * 2 + axis.ordinal();
        double v = Math.max(0.0, next[i] + amount);
        next[i] = v;
        envelope = next;
        return v;
    }
}
