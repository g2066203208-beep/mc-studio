package dev.reme.mcstudio.climb;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression for v0.10 bug: RELEASE was replayed every frame forever. */
class PeakAnimationPolicyTest {
    @Test void releaseRunsOnceThenReallyReturnsToVanilla() {
        int action = PeakAnimationPolicy.HANG;
        int deadline = -1;
        int count = 0;
        for (int t=100;t<120;t++) {
            int next = PeakAnimationPolicy.next(action, PeakAnimationPolicy.NONE,t,deadline);
            if (next != action && PeakAnimationPolicy.isRelease(next)) {
                count++;
                deadline=t+PeakAnimationPolicy.RELEASE_DURATION_TICKS;
            }
            action = next;
            if (t < 109) assertEquals(PeakAnimationPolicy.RELEASE,action);
        }
        assertEquals(1,count,"Do not restart RELEASE on every game tick");
        assertEquals(PeakAnimationPolicy.NONE, action,"Vanilla walk/idle must be restored");
    }
    @Test void roofReleaseAlsoFinishes() {
        int a = PeakAnimationPolicy.next(PeakAnimationPolicy.CEILING_CRAWL,
                PeakAnimationPolicy.NONE,0,-1);
        assertEquals(PeakAnimationPolicy.CEILING_RELEASE,a);
        assertEquals(a,PeakAnimationPolicy.next(a,PeakAnimationPolicy.NONE,4,9));
        assertEquals(PeakAnimationPolicy.NONE,
                PeakAnimationPolicy.next(a,PeakAnimationPolicy.NONE,9,9));
    }
    @Test void jumpUsesItsOwnAnimationAndRegripInterrupts() {
        assertEquals(PeakAnimationPolicy.WALL_JUMP,
                PeakAnimationPolicy.requested(true,9));
        assertEquals(PeakAnimationPolicy.WALL_JUMP,
                PeakAnimationPolicy.next(PeakAnimationPolicy.HANG,
                        PeakAnimationPolicy.WALL_JUMP,1,-1));
        assertEquals(PeakAnimationPolicy.HANG,
                PeakAnimationPolicy.next(PeakAnimationPolicy.WALL_JUMP,
                        PeakAnimationPolicy.HANG,3,-1));
        assertEquals(PeakAnimationPolicy.NONE,PeakAnimationPolicy.requested(false,9));
        assertEquals(PeakAnimationPolicy.NONE,PeakAnimationPolicy.requested(true,7));
    }
}
