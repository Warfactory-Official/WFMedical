package com.warfactory.medical;

import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the unit-test harness itself, so that a green run of the real tests means something.
 *
 * <p>A test suite that silently runs against a half-initialised Minecraft is worse than no suite: it
 * reports pass for assertions that never had the data to fail. These three checks are the floor --
 * JUnit runs, the mod's own classes link, and Bootstrap has actually populated the registries.
 */
class HarnessSmokeTest {

    @Test
    void junitRuns() {
        assertEquals(4, 2 + 2);
    }

    @Test
    void modClassesLink() {
        assertEquals(6, LimbType.VALUES.length, "LimbType should be head/torso/2 arms/2 legs");
        assertNotNull(new Vec3(1.0, 2.0, 3.0));
    }

    @Test
    void minecraftIsBootstrapped() {
        // If FML's JUnit bootstrap did not run, the registries are empty rather than absent, and every
        // registry-dependent assertion below would pass vacuously. Assert on content, not on non-null.
        assertTrue(BuiltInRegistries.ENTITY_TYPE.size() > 100,
                "entity registry looks unbootstrapped: " + BuiltInRegistries.ENTITY_TYPE.size() + " entries");
        assertNotNull(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ARMOR_STAND));
        assertEquals("minecraft:armor_stand",
                BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ARMOR_STAND).toString());
    }
}
