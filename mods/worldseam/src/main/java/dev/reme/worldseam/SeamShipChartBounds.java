package dev.reme.worldseam;

import net.minecraft.world.phys.AABB;

/**
 * An immutable, one-body, two-chart *geometry* projection for moving Sable
 * structures. Sable remains the ONLY physical body owner; this class does not
 * manufacture a second rigid body, move blocks, or issue teleport requests.
 *
 * Physics-solvers may consume the separate clipped hull volumes when they
 * implement native image colliders and contact impulse remapping.
 */
public final class SeamShipChartBounds {
    private SeamShipChartBounds() {}

    public record Projection(AABB homePart,AABB neighboringPart,
                             double translationY,boolean straddles) {}

    public static Projection project(AABB homeBounds,
                                      SeamGraph.Link link,
                                      double homeBoundary,
                                      double neighborBoundary) {
        if(homeBounds==null || link==null ||
           !Double.isFinite(homeBoundary) ||
           !Double.isFinite(neighborBoundary))
            throw new IllegalArgumentException("invalid ship projection");
        double dy=neighborBoundary-homeBoundary;
        if(!Double.isFinite(dy))throw new IllegalArgumentException("invalid seam translation");
        AABB mapped=homeBounds.move(0.0,dy,0.0);
        SeamBodySplit.Split split=SeamBodySplit.atTargetBoundary(
            mapped,link.entry(),neighborBoundary,homeBoundary);
        return new Projection(split.sourcePart(),split.targetPart(),dy,
            split.sourcePart()!=null && split.targetPart()!=null);
    }
}
