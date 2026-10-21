package dev.reme.worldseam;

/**
 * A physical blast remains centered on its original side of a vertical
 * connection plane. The neighboring dimension observes the SAME blast sphere
 * in a 1:1 Y mapping (not a second explosion centered inside the destination).
 *
 * Coordinates intentionally may be just outside the destination build height:
 * this is how only the crossing cap of the sphere reaches the other world.
 */
public final class SeamBlastGeometry {
    public static final float MAX_RADIUS = 8.0f;
    private SeamBlastGeometry() {}

    public record Plan(double mappedY, float radius, double distanceToPlane) {}

    /**
     * Minecraft's native explosion ray tracer cannot originate outside the
     * destination's actual build height (e.g. Overworld below Y=-64).
     * Move the origin just inside the real range and reduce power by exactly
     * the shift so that maximum inward reach cannot grow.
     *
     * This is a bounded native-explosion approximation of a spherical cap,
     * NOT a perfect cross-level obstacle-aware ray tracer.
     */
    public static Plan forNativeBuildRange(Plan plan,int minY,int maxY) {
        if(plan==null || maxY<=minY) return null;
        double y=Math.max(minY+0.125,Math.min(maxY-0.125,plan.mappedY()));
        double shift=Math.abs(y-plan.mappedY());
        float newRadius=(float)(plan.radius()-shift);
        if(newRadius<=0 || !Float.isFinite(newRadius))return null;
        return new Plan(y,newRadius,plan.distanceToPlane());
    }

    public static Plan map(double sourceY, double sourcePlane,
                           double targetPlane, float explosionRadius) {
        if (!Double.isFinite(sourceY) || !Double.isFinite(sourcePlane)
                || !Double.isFinite(targetPlane)
                || !Float.isFinite(explosionRadius)
                || explosionRadius <= 0) return null;
        // Bounded to prevent accidentally loading vast remote regions when
        // a custom mod detonates a giant explosion.
        float effective = Math.min(explosionRadius, MAX_RADIUS);
        double distance = Math.abs(sourceY - sourcePlane);
        if (distance >= effective) return null;
        return new Plan(targetPlane + (sourceY - sourcePlane), effective, distance);
    }
}
