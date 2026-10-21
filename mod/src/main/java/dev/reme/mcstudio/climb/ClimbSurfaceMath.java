package dev.reme.mcstudio.climb;

import net.minecraft.world.phys.Vec3;

/** Contact-space motion and hard speed caps; completely independent of camera pitch. */
public final class ClimbSurfaceMath {
    public static final double WALL_SPEED = 0.17;
    public static final double CEILING_SPEED = 0.29;
    public static final double FLOOR_SPEED = 0.16;
    public static final double MAX_RELATIVE_SPEED = 0.36;
    public static final double MAX_PLATFORM_STEP = 0.14;
    public static final double WALL_CAMERA_GAP = 0.69;
    public static final double CEILING_CAMERA_GAP = 0.49;
    public static final double MAX_STANDOFF_CORRECTION = 0.115;
    private ClimbSurfaceMath() {}

    public static boolean ceiling(Vec3 normal) { return normal.y < -0.72; }
    public static boolean floor(Vec3 normal) { return normal.y > 0.72; }
    public static double speed(Vec3 normal) {
        return ceiling(normal) ? CEILING_SPEED : floor(normal) ? FLOOR_SPEED : WALL_SPEED;
    }
    public static Vec3 tangent(Vec3 vector, Vec3 normal) {
        return vector.subtract(normal.scale(vector.dot(normal)));
    }
    public static Vec3 clamp(Vec3 v, double limit) {
        return v.lengthSqr() <= limit * limit ? v : v.normalize().scale(limit);
    }
    /** A block-to-block change of anchor is never moving-platform velocity. */
    public static Vec3 platformDelta(Vec3 previous, Vec3 current, boolean sameMovingStructure) {
        if (!sameMovingStructure) return Vec3.ZERO;
        Vec3 delta = current.subtract(previous);
        // Changes above 0.65 per tick cannot be safely applied as an impulse.
        if (delta.lengthSqr() > 0.65 * 0.65) return Vec3.ZERO;
        return clamp(delta, MAX_PLATFORM_STEP);
    }
    /** On walls the character faces INTO the surface; ceilings retain grab heading. */
    public static float facingYaw(Vec3 normal, float grabYaw) {
        if (Math.abs(normal.y) > 0.72) return grabYaw;
        Vec3 toward = normal.scale(-1);
        return (float)Math.toDegrees(Math.atan2(-toward.x, toward.z));
    }
    /**
     * Input mapped into the climbing plane using a locked heading.
     * Wall W climbs up irrespective of camera pitch; roof W travels forwards.
     */
    public static Vec3 direction(Vec3 normal, float heading, float forward, float strafe) {
        Vec3 n = normal.normalize();
        double yaw = Math.toRadians(heading);
        Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = tangent(new Vec3(0,1,0), n);
        Vec3 along = Math.abs(n.y) < 0.72 && up.lengthSqr() > 0.01
                ? up.normalize() : tangent(facing,n).normalize();
        Vec3 sideways = tangent(right,n);
        if (sideways.lengthSqr() < 0.001) sideways = n.cross(along);
        if (sideways.lengthSqr() > 0.001) sideways = sideways.normalize();
        return clamp(along.scale(forward).add(sideways.scale(strafe)), 1);
    }
    /** Signed distance out from the actual hit plane; negative means inside. */
    public static double signedGap(Vec3 eye, Vec3 contact, Vec3 normal) {
        return eye.subtract(contact).dot(normal.normalize());
    }

    /** Spring-like clearance servo. The model and camera must stay outside blocks. */
    public static Vec3 standoff(Vec3 eye, Vec3 contact, Vec3 normal) {
        Vec3 n = normal.normalize();
        double target = ceiling(n) ? CEILING_CAMERA_GAP
                : floor(n) ? 0.80 : WALL_CAMERA_GAP;
        double gap = signedGap(eye, contact, n);
        return n.scale(Math.max(-MAX_STANDOFF_CORRECTION,
                Math.min(MAX_STANDOFF_CORRECTION, (target-gap)*0.32)));
    }

    /** Only select a 90 degree wall/roof/corner switch if travel faces the new plane. */
    public static boolean canTurn(Vec3 oldNormal, Vec3 newNormal, Vec3 travel) {
        Vec3 a=oldNormal.normalize(), b=newNormal.normalize();
        if (a.dot(b) > .72 || a.dot(b) < -.15) return false;
        return travel.lengthSqr() > .001 && b.dot(travel.normalize()) < -.18;
    }

    /** Roof W follows CURRENT camera yaw, not the remembered wall grab direction. */
    public static Vec3 cameraRelativeRoof(Vec3 normal, float cameraYaw,
                                           float forward, float strafe) {
        return direction(normal, cameraYaw, forward, strafe);
    }

    /** True only when the CAMERA is really beneath the new roof hand contact.
     * Before this physical clearance is met, do not update contact normal or pose. */
    public static boolean roofEntryReady(Vec3 eye, Vec3 roofPoint, Vec3 roofNormal) {
        if (!ceiling(roofNormal)) return false;
        Vec3 n = roofNormal.normalize();
        Vec3 lateral = tangent(roofPoint.subtract(eye), n);
        double gap = signedGap(eye, roofPoint, n);
        return lateral.lengthSqr() < .58*.58 && gap >= .19 && gap <= 1.12;
    }

    /** Physically travel toward the underside while old wall grip remains valid.
     * Never command a downward Y velocity during a wall-to-roof transfer. */
    public static Vec3 roofEntryVelocity(Vec3 eye, Vec3 roofPoint, Vec3 roofNormal) {
        Vec3 desiredEye = roofPoint.add(roofNormal.normalize().scale(CEILING_CAMERA_GAP));
        Vec3 to = desiredEye.subtract(eye);
        Vec3 horizontal = tangent(to, roofNormal);
        horizontal = clamp(horizontal.scale(.27), .16);
        double lift = Math.max(0, Math.min(.095, to.y*.27));
        return clamp(new Vec3(horizontal.x, Math.max(0, horizontal.y + lift), horizontal.z), .175);
    }

    /** Smooth visual body turning through the +/-180 boundary. Movement is not delayed. */
    public static float approachYaw(float current, float desired, float maxStep) {
        float delta = net.minecraft.util.Mth.wrapDegrees(desired - current);
        return current + net.minecraft.util.Mth.clamp(delta, -maxStep, maxStep);
    }

    /** Jump direction is computed by the exact same pure helper used in JUnit. */
    public static Vec3 wallJumpVelocity(Vec3 look, Vec3 surfaceNormal) {
        double[] v = WallJumpMath.velocity(look.x, look.y, look.z,
                surfaceNormal.x, surfaceNormal.z);
        return new Vec3(v[0], v[1], v[2]);
    }

    /** Never add normal-facing camera velocity to the next frame's motor impulse. */
    public static Vec3 motor(Vec3 previous, Vec3 wanted, Vec3 normal) {
        Vec3 old = clamp(tangent(previous, normal), MAX_RELATIVE_SPEED);
        Vec3 goal = clamp(wanted, speed(normal));
        return clamp(old.scale(0.28).add(goal.scale(0.72)), MAX_RELATIVE_SPEED);
    }
}
