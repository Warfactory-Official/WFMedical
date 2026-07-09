package com.warfactory.medical.core.damage.rig;

/**
 * A loadable snapshot of the <b>static</b> limb-box geometry that {@link HumanoidRig} poses: the STANDING
 * {@link #base} spec, the per-pose additive {@link #poseAdjust}, and the per-(pose, hand-action) arm
 * {@link #handAdjust} overlays. All values are model units (1/16 block); each row is
 * {@code {ox,oy,oz, sx,sy,sz, px,py,pz}}.
 *
 * <p>This is only the fixed authored geometry &mdash; the animation ({@code HumanoidRig.setupAnim}) is NOT
 * data-driven and stays in code. {@link HumanoidRig} reads its box geometry through the active spec, which is
 * the built-in default unless {@link RigSpecIO} loads an override file. A loaded override starts from a copy
 * of the defaults and only replaces the rows the file specifies, so a partial file is always safe.</p>
 */
public final class RigSpec {

    /**
     * STANDING base spec, indexed {@code [LimbType.ordinal()][field]}.
     */
    public final double[][] base;
    /**
     * Per-pose additive adjustment, indexed {@code [RigPose.ordinal()][LimbType.ordinal()][field]}.
     */
    public final double[][][] poseAdjust;
    /**
     * Per-(pose, hand-action) arm overlay, indexed {@code [RigPose][HandAction][LimbType][field]}.
     */
    public final double[][][][] handAdjust;

    public RigSpec(double[][] base, double[][][] poseAdjust, double[][][][] handAdjust) {
        this.base = base;
        this.poseAdjust = poseAdjust;
        this.handAdjust = handAdjust;
    }

    /**
     * A deep, independently-mutable copy (so an overlay never mutates the shared defaults).
     */
    public RigSpec copy() {
        return new RigSpec(copy2(base), copy3(poseAdjust), copy4(handAdjust));
    }

    private static double[][] copy2(double[][] a) {
        double[][] out = new double[a.length][];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] == null ? null : a[i].clone();
        }
        return out;
    }

    private static double[][][] copy3(double[][][] a) {
        double[][][] out = new double[a.length][][];
        for (int i = 0; i < a.length; i++) {
            out[i] = copy2(a[i]);
        }
        return out;
    }

    private static double[][][][] copy4(double[][][][] a) {
        double[][][][] out = new double[a.length][][][];
        for (int i = 0; i < a.length; i++) {
            out[i] = copy3(a[i]);
        }
        return out;
    }
}
