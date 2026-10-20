package dev.reme.mcstudio.climb;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClimbSurfaceMathTest {
    @Test void ceilingTraversesFasterWithoutGravityStyleSlowdown() {
        assertTrue(ClimbSurfaceMath.speed(new Vec3(0,-1,0))
                   >= ClimbSurfaceMath.speed(new Vec3(0,0,-1)) * 1.6);
        Vec3 motion = ClimbSurfaceMath.motor(new Vec3(0,-11,0),
                new Vec3(0,0,ClimbSurfaceMath.CEILING_SPEED),new Vec3(0,-1,0));
        assertEquals(0,motion.y,1e-6,"Ceiling tangent motor must reject existing gravity");
        assertTrue(motion.z>0.19,"Ceiling movement must not stall");
    }

    @Test void regrippingAnotherBlockCannotBecomePlatformVelocity() {
        assertEquals(Vec3.ZERO, ClimbSurfaceMath.platformDelta(
                new Vec3(4,3,7),new Vec3(4,4,7),false));
        assertEquals(Vec3.ZERO, ClimbSurfaceMath.platformDelta(
                new Vec3(1,1,1),new Vec3(40,50,60),true));
        Vec3 bounded=ClimbSurfaceMath.platformDelta(Vec3.ZERO,new Vec3(.4,0,0),true);
        assertTrue(bounded.length()<=ClimbSurfaceMath.MAX_PLATFORM_STEP+1e-6);
    }

    @Test void extremeOutdatedVelocityNeverLaunchesPlayer() {
        Vec3 n=new Vec3(0,0,-1);
        Vec3 motor=ClimbSurfaceMath.motor(new Vec3(900,300,-900),
                new Vec3(0,.17,0),n);
        assertTrue(motor.length() <= ClimbSurfaceMath.MAX_RELATIVE_SPEED+1e-8);
        assertEquals(0,motor.dot(n),1e-8);
    }

    @Test void lockedGripFacingDoesNotReadCameraRotation() {
        Vec3 wall=new Vec3(0,0,-1);
        float locked=ClimbSurfaceMath.facingYaw(wall, 87);
        assertEquals(0,locked,1e-4);
        Vec3 move=ClimbSurfaceMath.direction(wall,locked,1,0);
        assertTrue(move.y>.99);
        assertEquals(move,ClimbSurfaceMath.direction(wall,locked,1,0),
                "Rotating Minecraft camera must not change surface-locked motion");
        Vec3 roof=new Vec3(0,-1,0);
        assertEquals(87,ClimbSurfaceMath.facingYaw(roof,87),1e-4);
    }

    @Test void roofMovementDirectionIsAlwaysInContactPlane() {
        Vec3 roof=new Vec3(0,-1,0);
        Vec3 delta=ClimbSurfaceMath.direction(roof,37,1,1);
        assertEquals(0,delta.dot(roof),1e-8);
        assertTrue(delta.length()<=1.000001);
    }
}
