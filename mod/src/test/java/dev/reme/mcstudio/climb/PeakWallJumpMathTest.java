package dev.reme.mcstudio.climb;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure math: does not depend on Minecraft classes on JUnit classpath. */
class PeakWallJumpMathTest {
    @Test void wallJumpIsAlwaysBoundedAndAwayFromTheHitNormal() {
        for (double[] look : new double[][]{
                {0,-1,1},{0,0,1},{1,1,-1},{-1,.2,-1},{0,0,0}}) {
            double[] v=WallJumpMath.velocity(look[0],look[1],look[2],0,-1);
            assertTrue(-v[2]>=.22, "must launch away from wall");
            assertTrue(Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2])<=.54+1e-6);
            assertTrue(Double.isFinite(v[0]+v[1]+v[2]));
        }
    }
    @Test void lookingTowardsAnotherHoldAimsTheDyno() {
        double[] right=WallJumpMath.velocity(1,.65,0,0,-1);
        double[] left=WallJumpMath.velocity(-1,.65,0,0,-1);
        assertTrue(right[0]>.2 && left[0]<-.2);
        double[] high=WallJumpMath.velocity(0,.95,-.1,0,-1);
        double[] low=WallJumpMath.velocity(0,-.95,-.1,0,-1);
        assertTrue(high[1]>low[1]+.2);
    }
    @Test void invalidNormalProducesNoImpulse() {
        assertArrayEquals(new double[]{0,0,0}, WallJumpMath.velocity(1,1,1,0,0));
    }
}
