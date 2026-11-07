package com.warfactory.medical.damage;

import com.warfactory.medical.body.limb.LimbType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The defaults: threshold 0.35, drain 0.25/s, rifle round ~10.7 energy. */
class StaggerTest {

    private static final float THRESHOLD = 0.35F;
    private static final float DECAY = 0.25F / 20.0F;
    private static final float RIFLE = 10.66F;

    private static boolean hit(Stagger.Meter m, Stagger.Zone z, float energy, float jitter, long tick) {
        return m.add(Stagger.power(energy, z, jitter), tick, THRESHOLD, DECAY, z.ticks());
    }

    @Test
    void aRifleHeadHitAlwaysStaggers() {
        for (float jitter : new float[]{0.75F, 1.0F, 1.25F}) {
            assertTrue(hit(new Stagger.Meter(), Stagger.Zone.HEAD, RIFLE, jitter, 0));
        }
    }

    @Test
    void aTorsoHitIsAChanceAndASecondOneInABurstSettlesIt() {
        assertFalse(hit(new Stagger.Meter(), Stagger.Zone.TORSO, RIFLE, 0.75F, 0));
        assertTrue(hit(new Stagger.Meter(), Stagger.Zone.TORSO, RIFLE, 1.25F, 0));
        Stagger.Meter m = new Stagger.Meter();
        assertFalse(hit(m, Stagger.Zone.TORSO, RIFLE, 0.75F, 0));
        assertTrue(hit(m, Stagger.Zone.TORSO, RIFLE, 0.75F, 3));
    }

    @Test
    void aLimbNeedsTwoHitsAndSpacedShotsDoNotStack() {
        Stagger.Meter burst = new Stagger.Meter();
        assertFalse(hit(burst, Stagger.Zone.ARM_L, RIFLE, 1.0F, 0));
        assertTrue(hit(burst, Stagger.Zone.ARM_L, RIFLE, 1.0F, 2));
        Stagger.Meter spaced = new Stagger.Meter();
        assertFalse(hit(spaced, Stagger.Zone.ARM_L, RIFLE, 1.0F, 0));
        assertFalse(hit(spaced, Stagger.Zone.ARM_L, RIFLE, 1.0F, 100));
    }

    @Test
    void armourThatStopsTheRoundBarelyMovesTheMeter() {
        // Plate: ~0.9 through + 40% of 10.8 as momentum.
        assertEquals(0.15F, Stagger.power(0.9F + 4.3F, Stagger.Zone.TORSO, 1.0F), 0.02F);
        Stagger.Meter m = new Stagger.Meter();
        assertFalse(hit(m, Stagger.Zone.TORSO, 5.2F, 1.0F, 0));
        assertFalse(hit(m, Stagger.Zone.TORSO, 5.2F, 1.0F, 2));
        assertTrue(hit(m, Stagger.Zone.TORSO, 5.2F, 1.0F, 4));
    }

    @Test
    void aStaggerEmptiesTheMeterAndIsNotRetriggeredWhileItRuns() {
        Stagger.Meter m = new Stagger.Meter();
        assertTrue(hit(m, Stagger.Zone.HEAD, RIFLE, 1.0F, 0));
        assertEquals(0.0F, m.value(), 1.0E-6F);
        assertFalse(hit(m, Stagger.Zone.HEAD, RIFLE, 1.0F, 5));
        assertTrue(hit(m, Stagger.Zone.HEAD, RIFLE, 1.0F, 40));
    }

    @Test
    void everyLimbMapsToAZone() {
        for (LimbType lt : LimbType.VALUES) {
            assertEquals(lt.isLeg(), Stagger.Zone.of(lt).isLeg());
        }
    }
}
