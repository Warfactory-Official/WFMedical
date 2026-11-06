package com.warfactory.medical.ragdoll;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollGeometryTest {
    @Test void aFallenLegUsesItsRotatedHalfExtents() {
        AABB box = RagdollGeometry.limbBounds(new Vec3(10, -59, 12), new Vec3(0.129, 0.375, 0.129),
                new Quaternionf().rotateZ((float) (Math.PI / 2)));
        assertNotNull(box);
        assertEquals(0.75, box.getXsize(), 0.000001);
        assertEquals(0.258, box.getYsize(), 0.000001);
        assertEquals(0.258, box.getZsize(), 0.000001);
        assertEquals(new Vec3(10, -59, 12), box.getCenter());
    }

    @Test void headBoundsKeepTheirActualSize() {
        AABB box = RagdollGeometry.limbBounds(Vec3.ZERO, new Vec3(0.25, 0.25, 0.25), new Quaternionf());
        assertNotNull(box);
        assertEquals(0.5, box.getXsize());
        assertEquals(0.5, box.getYsize());
        assertEquals(0.5, box.getZsize());
    }

    @Test void corruptTransformsCannotMakeHugeOrInvalidHitboxes() {
        Vec3 half = new Vec3(0.25, 0.25, 0.25);
        assertNull(RagdollGeometry.limbBounds(new Vec3(Double.NaN, 0, 0), half, new Quaternionf()));
        assertNull(RagdollGeometry.limbBounds(new Vec3(40_000_000, 0, 0), half, new Quaternionf()));
        assertNull(RagdollGeometry.limbBounds(Vec3.ZERO, half, new Quaternionf(0, 0, 0, 0)));
        assertNull(RagdollGeometry.limbBounds(Vec3.ZERO, half, new Quaternionf(Float.POSITIVE_INFINITY, 0, 0, 1)));
    }

    @Test void reachMeasuresTheNearestLimbSurfaceRatherThanItsCenter() {
        AABB box = new AABB(2.9, -0.25, -0.25, 3.4, 0.25, 0.25);
        assertTrue(RagdollGeometry.withinRange(box, Vec3.ZERO, 3));
        assertFalse(RagdollGeometry.withinRange(box, Vec3.ZERO, 2.89));
        assertEquals(new Vec3(2.9, 0, 0), RagdollGeometry.nearest(box, Vec3.ZERO));
        assertFalse(RagdollGeometry.withinRange(box, Vec3.ZERO, Double.NaN));
    }
}
