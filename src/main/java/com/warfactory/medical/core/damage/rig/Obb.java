package com.warfactory.medical.core.damage.rig;

import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.world.phys.Vec3;

/**
 * An oriented bounding box tagged with the {@link LimbType} it represents. Pure math &mdash; the only
 * Minecraft type referenced is {@link Vec3}. Lives in the victim's entity-local frame (feet origin,
 * Y-up, X = right, Z = front); the incoming attack ray is rotated into that frame before testing.
 *
 * <p>Axes are orthonormal; {@link #half} holds the positive half-extent along each corresponding axis.</p>
 */
public record Obb(Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ, Vec3 half, LimbType limb) {

    private static double excess(double d, double h) {
        double a = Math.abs(d) - h;
        return Math.max(a, 0.0);
    }

    /**
     * Entry distance {@code t} (in units of {@code dir}'s length) at which the ray {@code origin + t*dir}
     * first enters this box, or {@link Double#POSITIVE_INFINITY} on a miss. Slab test performed in the
     * box-local frame by projecting the ray onto the three axes. A ray originating inside the box
     * returns {@code 0}. Fully deterministic.
     */
    public double rayEntry(Vec3 origin, Vec3 dir) {
        double px = origin.x - center.x;
        double py = origin.y - center.y;
        double pz = origin.z - center.z;
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        // Three axis slabs, fully inlined and allocation-free (no scratch Vec3 / double[]) since this runs
        // once per OBB per hit; the running [tMin,tMax] is the ray-vs-box overlap. Any empty slab -> miss.
        // --- X slab ---
        double po = px * axisX.x + py * axisX.y + pz * axisX.z;
        double d = dir.x * axisX.x + dir.y * axisX.y + dir.z * axisX.z;
        if (Math.abs(d) < 1.0e-9) {
            if (po < -half.x || po > half.x) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double t1 = (-half.x - po) / d;
            double t2 = (half.x - po) / d;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            if (t1 > tMin) {
                tMin = t1;
            }
            if (t2 < tMax) {
                tMax = t2;
            }
            if (tMin > tMax) {
                return Double.POSITIVE_INFINITY;
            }
        }
        // --- Y slab ---
        po = px * axisY.x + py * axisY.y + pz * axisY.z;
        d = dir.x * axisY.x + dir.y * axisY.y + dir.z * axisY.z;
        if (Math.abs(d) < 1.0e-9) {
            if (po < -half.y || po > half.y) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double t1 = (-half.y - po) / d;
            double t2 = (half.y - po) / d;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            if (t1 > tMin) {
                tMin = t1;
            }
            if (t2 < tMax) {
                tMax = t2;
            }
            if (tMin > tMax) {
                return Double.POSITIVE_INFINITY;
            }
        }
        // --- Z slab ---
        po = px * axisZ.x + py * axisZ.y + pz * axisZ.z;
        d = dir.x * axisZ.x + dir.y * axisZ.y + dir.z * axisZ.z;
        if (Math.abs(d) < 1.0e-9) {
            if (po < -half.z || po > half.z) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double t1 = (-half.z - po) / d;
            double t2 = (half.z - po) / d;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            if (t1 > tMin) {
                tMin = t1;
            }
            if (t2 < tMax) {
                tMax = t2;
            }
            if (tMin > tMax) {
                return Double.POSITIVE_INFINITY;
            }
        }
        if (tMax < 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(tMin, 0.0);
    }

    /**
     * True when {@code p} lies within the box (inclusive of the surface).
     */
    public boolean contains(Vec3 p) {
        double qx = p.x - center.x;
        double qy = p.y - center.y;
        double qz = p.z - center.z;
        return Math.abs(qx * axisX.x + qy * axisX.y + qz * axisX.z) <= half.x
                && Math.abs(qx * axisY.x + qy * axisY.y + qz * axisY.z) <= half.y
                && Math.abs(qx * axisZ.x + qy * axisZ.y + qz * axisZ.z) <= half.z;
    }

    /**
     * Squared distance from {@code p} to the nearest point on/in the box (0 when inside). Used by the
     * point-only fallback (explosion entry) to pick the closest limb.
     */
    public double distanceSq(Vec3 p) {
        double qx = p.x - center.x;
        double qy = p.y - center.y;
        double qz = p.z - center.z;
        double ex = excess(qx * axisX.x + qy * axisX.y + qz * axisX.z, half.x);
        double ey = excess(qx * axisY.x + qy * axisY.y + qz * axisY.z, half.y);
        double ez = excess(qx * axisZ.x + qy * axisZ.y + qz * axisZ.z, half.z);
        return ex * ex + ey * ey + ez * ez;
    }
}
