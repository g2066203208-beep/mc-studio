package dev.reme.mcstudio.climb;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeakWallJumpMathTest {
    @Test void wallJumpIsAlwaysBoundedAndAwayFromTheHitNormal() {
        Vec3 n = new Vec3(0,0,-1);
        for (Vec3 look : new Vec3[]{
                new Vec3(0,-1,1),new Vec3(0,0,1),
                new Vec3(1,1,-1),new Vec3(-1,.2,-1),Vec3.ZERO}) {
            Vec3 motion = ClimbSurfaceMath.wallJumpVelocity(look, n);
            assertTrue(motion.dot(n) >= .22, "must not tunnel inward");
            assertTrue(motion.length() <= .54 + 1.0e-6);
            assertTrue(Double.isFinite(motion.x+motion.y+motion.z));
        }
    }
    @Test void lookingTowardsAnotherHoldAimsTheDyno() {
        Vec3 normal = new Vec3(0,0,-1);
        Vec3 right = ClimbSurfaceMath.wallJumpVelocity(new Vec3(1,.65,0),normal);
        Vec3 left = ClimbSurfaceMath.wallJumpVelocity(new Vec3(-1,.65,0),normal);
        assertTrue(right.x > .2 && left.x < -.2);
        Vec3 high = ClimbSurfaceMath.wallJumpVelocity(new Vec3(0,.95,-.1),normal);
        Vec3 low = ClimbSurfaceMath.wallJumpVelocity(new Vec3(0,-.95,-.1),normal);
        assertTrue(high.y>low.y+.2);
    }
}
