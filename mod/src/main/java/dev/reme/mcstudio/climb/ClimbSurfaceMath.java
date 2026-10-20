package dev.reme.mcstudio.climb;

import net.minecraft.world.phys.Vec3;

/** Contact-space motion and hard speed caps; completely independent of camera pitch. */
public final class ClimbSurfaceMath {
    public static final double WALL_SPEED = 0.17;
    public static final double CEILING_SPEED = 0.29;
    public static final double FLOOR_SPEED = 0.16;
    public static final double MAX_RELATIVE_SPEED = 0.36;
    public static final double MAX_PLATFORM_STEP = 0.14;
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
    /** Never add normal-facing camera velocity to the next frame's motor impulse. */
    public static Vec3 motor(Vec3 previous, Vec3 wanted, Vec3 normal) {
        Vec3 old = clamp(tangent(previous, normal), MAX_RELATIVE_SPEED);
        Vec3 goal = clamp(wanted, speed(normal));
        return clamp(old.scale(0.28).add(goal.scale(0.72)), MAX_RELATIVE_SPEED);
    }
}
