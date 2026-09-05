package com.warfactory.medical.compat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TACZ bridge. TACZ delivers each bullet as <em>two</em> hurt events (its armour-piercing and
 * non-AP damage split), so without coalescing every gunshot would generate its trauma twice; the
 * per-bullet {@code claim} is the thing that stops it, and it is invisible in-game when it breaks --
 * the wound is simply twice as bad as the config says it should be.
 *
 * <p>The caches are static and process-wide with no reset hook, so every test here allocates its own
 * bullet ids rather than sharing them.
 */
class TaczHitCaptureTest {

    /** Bullet entity ids are arbitrary ints; a per-test counter keeps the shared static maps disjoint. */
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1_000_000);

    private static int freshId() {
        return NEXT_ID.incrementAndGet();
    }

    @Nested
    @DisplayName("hit capture")
    class Capture {

        @Test
        void capturedHitComesBackWithItsSegment() {
            int id = freshId();
            Vec3 point = new Vec3(1.0, 2.0, 3.0);
            Vec3 start = new Vec3(0.0, 2.0, 0.0);
            Vec3 end = new Vec3(10.0, 2.0, 0.0);
            TaczHitCapture.capture(id, point, start, end);

            var hit = TaczHitCapture.peek(id);
            assertTrue(hit.isPresent());
            assertEquals(point, hit.get().point());
            assertEquals(start, hit.get().start(), "the ray segment is what drives entry-order OBB "
                    + "classification; losing it silently downgrades to point-based banding");
            assertEquals(end, hit.get().end());
        }

        @Test
        void anUnknownBulletIsEmptyNotNull() {
            assertTrue(TaczHitCapture.peek(freshId()).isEmpty());
        }

        @Test
        void aSecondCaptureForTheSameBulletWins() {
            // A bullet that pierces and hits again in the same tick re-captures; the later segment is
            // the one that produced the hurt event now being handled.
            int id = freshId();
            TaczHitCapture.capture(id, Vec3.ZERO, Vec3.ZERO, new Vec3(1.0, 0.0, 0.0));
            TaczHitCapture.capture(id, new Vec3(5.0, 0.0, 0.0), new Vec3(4.0, 0.0, 0.0), new Vec3(6.0, 0.0, 0.0));
            assertEquals(new Vec3(5.0, 0.0, 0.0), TaczHitCapture.peek(id).orElseThrow().point());
        }

        @Test
        void damageIsCapturedSeparatelyFromGeometry() {
            int id = freshId();
            assertTrue(TaczHitCapture.totalDamage(id).isEmpty());
            TaczHitCapture.captureDamage(id, 7.5F);
            assertEquals(7.5, TaczHitCapture.totalDamage(id).orElseThrow(), 1.0e-6);
        }
    }

    @Nested
    @DisplayName("per-tick claim (the double-hurt guard)")
    class Claim {

        @Test
        void theFirstClaimInATickWinsAndTheSecondLoses() {
            // This IS the fix for TACZ's AP/non-AP split: two hurt events, one bullet, one trauma.
            int id = freshId();
            assertTrue(TaczHitCapture.claim(id, 100L), "first hurt event should claim the bullet");
            assertFalse(TaczHitCapture.claim(id, 100L), "second hurt event in the same tick must not");
        }

        @Test
        void repeatedClaimsInTheSameTickAllLose() {
            int id = freshId();
            assertTrue(TaczHitCapture.claim(id, 5L));
            for (int i = 0; i < 10; i++) {
                assertFalse(TaczHitCapture.claim(id, 5L), "claim " + i + " should still be rejected");
            }
        }

        @Test
        void aLaterTickClaimsAgain() {
            // The guard is per-tick, not per-bullet-forever: a piercing round that is still alive next
            // tick and hits a second victim has to be allowed to apply trauma again.
            int id = freshId();
            assertTrue(TaczHitCapture.claim(id, 1L));
            assertFalse(TaczHitCapture.claim(id, 1L));
            assertTrue(TaczHitCapture.claim(id, 2L), "a new tick is a new hit");
            assertFalse(TaczHitCapture.claim(id, 2L));
        }

        @Test
        void bulletsDoNotInterfereWithEachOther() {
            // A burst puts several bullets in flight on the same tick; claiming one must not consume
            // another's turn, or all but the first round of a burst would deal no trauma.
            int a = freshId();
            int b = freshId();
            int c = freshId();
            assertTrue(TaczHitCapture.claim(a, 42L));
            assertTrue(TaczHitCapture.claim(b, 42L));
            assertTrue(TaczHitCapture.claim(c, 42L));
            assertFalse(TaczHitCapture.claim(a, 42L));
            assertFalse(TaczHitCapture.claim(b, 42L));
            assertFalse(TaczHitCapture.claim(c, 42L));
        }

        @Test
        void anOutOfOrderEarlierTickIsTreatedAsANewHit() {
            // Pins current behaviour rather than endorsing it: the guard compares the stored tick for
            // EQUALITY, so a non-monotonic tick (a rewind, or a wrap) re-arms the claim rather than
            // rejecting it. Harmless while ticks advance, but it is the behaviour, so it is written down.
            int id = freshId();
            assertTrue(TaczHitCapture.claim(id, 10L));
            assertTrue(TaczHitCapture.claim(id, 9L), "an earlier tick is 'different', so it claims");
        }
    }

    @Nested
    @DisplayName("bounded caches")
    class Eviction {

        @Test
        void theHitCacheIsCappedSoUnconsumedShotsCannotGrowUnbounded() {
            // Shots that never produce a hurt event (a miss resolved against a block) leave their entry
            // behind. The LRU cap is what stops a long session leaking; 256 is the documented bound.
            int base = NEXT_ID.addAndGet(10_000);
            for (int i = 0; i < 400; i++) {
                TaczHitCapture.capture(base + i, Vec3.ZERO, Vec3.ZERO, new Vec3(1.0, 0.0, 0.0));
            }
            assertTrue(TaczHitCapture.peek(base + 399).isPresent(), "the newest entry must survive");
            assertTrue(TaczHitCapture.peek(base).isEmpty(),
                    "the eldest of 400 entries should have been evicted by the 256-entry cap");
        }

        @Test
        void evictingAClaimRe_armsTheDoubleHurtGuard() {
            // The consequence of the cap on the CLAIMED map, pinned deliberately: if more than 256 other
            // bullets are claimed between a bullet's two hurt events, its claim is evicted and the second
            // event is allowed through, applying trauma twice. That needs >256 bullets claimed inside a
            // single tick to happen for real, so it is a documented bound rather than a live bug.
            int id = freshId();
            assertTrue(TaczHitCapture.claim(id, 77L));
            assertFalse(TaczHitCapture.claim(id, 77L));

            int base = NEXT_ID.addAndGet(10_000);
            for (int i = 0; i < 300; i++) {
                TaczHitCapture.claim(base + i, 77L);
            }
            assertTrue(TaczHitCapture.claim(id, 77L),
                    "once evicted the guard cannot reject the duplicate any more");
        }
    }
}
