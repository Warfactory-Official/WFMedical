package com.warfactory.medical.compat.tacz;

/**
 * Baked TACZ third-person arm pose for the humanoid rig.

 */
public final class TaczArmPose {

    // Arm Euler rotations
    public record Pose(double rightX, double rightY, double rightZ,
                       double leftX, double leftY, double leftZ) {
    }

    //baked states
    private static final Pose HOLD = new Pose(
            -1.20, -0.10, 0.00,   // right arm
            -1.35, 0.35, 0.00);   // left arm
    private static final Pose AIM = new Pose(
            -1.52, -0.08, 0.05,   // right arm
            -1.50, 0.22, -0.05);  // left arm

    private TaczArmPose() {
    }

     // The arm pose at {@code aimProgress} (0 = hold, 1 = full ADS),
    public static Pose resolve(float aimProgress) {
        double t = aimProgress < 0.0F ? 0.0 : (aimProgress > 1.0F ? 1.0 : aimProgress);
        return new Pose(
                lerp(t, HOLD.rightX, AIM.rightX),
                lerp(t, HOLD.rightY, AIM.rightY),
                lerp(t, HOLD.rightZ, AIM.rightZ),
                lerp(t, HOLD.leftX, AIM.leftX),
                lerp(t, HOLD.leftY, AIM.leftY),
                lerp(t, HOLD.leftZ, AIM.leftZ));
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }
}
