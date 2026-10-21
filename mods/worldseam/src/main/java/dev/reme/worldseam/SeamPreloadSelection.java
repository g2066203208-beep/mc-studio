package dev.reme.worldseam;

import java.util.List;

/**
 * Pure-Java seam view selector shared by all future renderer and stream
 * backends. Prevents flipping 5x5 chunk tickets when the player hovers
 * equidistant between Nether's Y0 and Y128 connection planes.
 *
 * ENTER and EXIT form a hysteresis band. Each target is scoped by source +
 * destination + plane; switching still happens when a different side is
 * genuinely closer by more than SWITCH_MARGIN.
 */
public final class SeamPreloadSelection {
    public static final double ENTER_RADIUS = 64.0;
    public static final double EXIT_RADIUS = 80.0;
    public static final double SWITCH_MARGIN = 12.0;

    public record Candidate(String scope, double distance) {
        public Candidate {
            if (scope==null || scope.isBlank() || !Double.isFinite(distance)
                || distance<0)
                throw new IllegalArgumentException("Invalid seam candidate");
        }
    }
    private SeamPreloadSelection() {}

    public static Candidate choose(List<Candidate> candidates, String previous) {
        if (candidates==null) throw new IllegalArgumentException("Missing candidates");
        Candidate best = null, retained = null;
        for (Candidate c : candidates) {
            if (c.scope().equals(previous) && c.distance()<=EXIT_RADIUS)
                retained=c;
            if (c.distance()<=ENTER_RADIUS &&
                (best==null || c.distance()<best.distance()))
                best=c;
        }
        // Only prewarm the second dimension when actually near a seam.
        if (retained==null) return best;
        if (best==null || retained.distance()<=best.distance()+SWITCH_MARGIN)
            return retained;
        return best;
    }
}
