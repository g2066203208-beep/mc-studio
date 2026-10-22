package dev.reme.worldseam;

import net.minecraft.world.phys.Vec3;

/**
 * Mathematically exact frame transformation for translation-only Minecraft
 * dimension joins. Source: the Atlas-world / image-collider Tier-1 model:
 * an image contact's world point is expressed in the destination chart, but
 * its force and torque belong to ONE real rigid body's original chart.
 *
 * IMPORTANT: This class constructs a WRENCH; it does NOT write to Rapier or
 * implement native dual-world collision. A JNI native image-collider solver
 * must perform this transform PER CONTACT inside the native substep.
 *
 * Unlike rotating portals, WorldSeam's vertical joins have R=identity:
 *     image(p) = p + t, image(COM) = COM + t
 *     F_source = F_dest
 *     tau_source = (p_dest - t - COM_source) CROSS F_dest
 * The critical term is "-t"; forgetting it creates huge phantom torque
 * proportional to the 192-block OW/Nether frame offset.
 */
public final class SeamContactFrame {
    private SeamContactFrame() {}

    public record Wrench(Vec3 force,Vec3 torque,Vec3 contactInSource) {}

    public record Translation(double sourceToDestinationY) {
        public Translation {
            if(!Double.isFinite(sourceToDestinationY))
                throw new IllegalArgumentException("Nonfinite chart translation");
        }

        /** Exact source->destination mapping, including arbitrary seam heights. */
        public Vec3 imageOf(Vec3 source) {
            finite(source);
            return source.add(0.0,sourceToDestinationY,0.0);
        }

        public Vec3 sourceOf(Vec3 destination) {
            finite(destination);
            return destination.add(0.0,-sourceToDestinationY,0.0);
        }

        /** Source frame force and torque; not allowed to mutate rigid bodies. */
        public Wrench contactWrench(Vec3 realBodyCenterInSource,
                                    Vec3 imageContactPointInDestination,
                                    Vec3 contactForceInDestination) {
            finite(realBodyCenterInSource);
            finite(imageContactPointInDestination);
            finite(contactForceInDestination);
            Vec3 sourcePoint=sourceOf(imageContactPointInDestination);
            Vec3 torque=sourcePoint.subtract(realBodyCenterInSource)
                .cross(contactForceInDestination);
            finite(torque);
            return new Wrench(contactForceInDestination,torque,sourcePoint);
        }

        public Translation inverse() {
            return new Translation(-sourceToDestinationY);
        }
    }

    public static Translation between(double sourceBoundary,double destBoundary) {
        if(!Double.isFinite(sourceBoundary)||!Double.isFinite(destBoundary))
            throw new IllegalArgumentException("Nonfinite native seam planes");
        return new Translation(destBoundary-sourceBoundary);
    }

    private static void finite(Vec3 vector) {
        if(vector==null || !Double.isFinite(vector.x) ||
           !Double.isFinite(vector.y) || !Double.isFinite(vector.z))
            throw new IllegalArgumentException("Nonfinite contact vector");
    }
}
