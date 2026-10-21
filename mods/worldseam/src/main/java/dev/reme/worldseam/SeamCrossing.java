package dev.reme.worldseam;

/**
 * Continuous crossing of the **physical** dimension connection plane.
 *
 * Entity position never has to exceed Minecraft's build height for the
 * transfer to start. Near-plane motion triggers a bounded handoff into the
 * adjacent world's valid border, and the old overshoot rule is a fallback.
 * The physical collision test in SeamArrival remains mandatory.
 */
public final class SeamCrossing {
    private static final double APPROACH_BAND = 0.5;
    private static final double DIRECTION_EPSILON = 0.015625;
    private static final double LANDING_OFFSET = 0.125;

    private SeamCrossing() {}

    public record Decision(boolean crossing, boolean early, double arrivalY) {
        static Decision none() { return new Decision(false,false,Double.NaN); }
    }

    public static Decision evaluate(SeamGraph.Link link, double currentY,
                                    double previousY, double velocityY,
                                    int sourceMinY, int sourceLogicalMaxY,
                                    double destinationBoundary) {
        double sourceBoundary = SeamGraph.boundaryY(link.exit(),
            sourceMinY, sourceLogicalMaxY);
        boolean upward = link.exit() == SeamGraph.EdgeSide.TOP;
        boolean moved = upward
            ? (currentY >= previousY + DIRECTION_EPSILON ||
                velocityY >= DIRECTION_EPSILON)
            : (currentY <= previousY - DIRECTION_EPSILON ||
                velocityY <= -DIRECTION_EPSILON);
        boolean nearInside = upward
            ? currentY >= sourceBoundary - APPROACH_BAND
                && currentY < sourceBoundary
            : currentY <= sourceBoundary + APPROACH_BAND
                && currentY >= sourceBoundary;
        boolean early = nearInside && moved;

        if (early) {
            return new Decision(true,true,destinationBoundary +
                (upward ? LANDING_OFFSET : -LANDING_OFFSET));
        }
        if (link.crossed(currentY, sourceMinY, sourceLogicalMaxY)) {
            return new Decision(true,false,
                link.targetY(currentY, sourceBoundary, destinationBoundary));
        }
        return Decision.none();
    }
}
