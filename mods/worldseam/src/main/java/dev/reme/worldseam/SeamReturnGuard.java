package dev.reme.worldseam;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Directional crossing hysteresis. A player entering a border is not allowed
 * to instantly exit back through that same border merely due to gravity,
 * residual momentum, or vanilla position corrections.
 *
 * Re-arm requires moving at least 1.25 blocks *into* the newly entered
 * dimension. This is a geometric safety condition, not a magic timer: it
 * works regardless of FPS/TPS and does not suppress other dimension links.
 */
public final class SeamReturnGuard {
    static final double REARM_DISTANCE = 1.25;
    static final double SAFE_INSET = 0.125;

    private record Arrival(String dimension, SeamGraph.EdgeSide entry) {}
    private final Map<UUID, Arrival> arrivals = new HashMap<>();

    public void landed(UUID id, String targetDimension, SeamGraph.EdgeSide entry) {
        arrivals.put(id, new Arrival(targetDimension,entry));
    }

    public boolean blocked(UUID id, String dim, SeamGraph.EdgeSide attemptedExit,
                           double y, double minY, double topY) {
        Arrival arrival=arrivals.get(id);
        if(arrival==null || !arrival.dimension().equals(dim)) return false;
        if(arrival.entry()!=attemptedExit) return false;

        double inside=attemptedExit==SeamGraph.EdgeSide.BOTTOM
            ? y-minY : topY-y;
        if(inside>=REARM_DISTANCE) {
            arrivals.remove(id);
            return false;
        }
        return true;
    }

    public static double safeY(SeamGraph.EdgeSide side,double minY,double topY) {
        return side==SeamGraph.EdgeSide.BOTTOM
            ? minY+SAFE_INSET : topY-SAFE_INSET;
    }

    public void clear() { arrivals.clear(); }
    public void forget(UUID id) { arrivals.remove(id); }
    public int size() { return arrivals.size(); }
}
