package dev.reme.worldseam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Protocol-independent snapshot/delta state machine. Rendering/networking must
 * never conflate updates from different dimensions, seam sides or view origins.
 */
public final class SeamFrameState {
    public record Position(int x, int y, int z) {}
    public record Visual(int stateId, int light) {
        public Visual {
            if (stateId < 0 || light < 0 || light > 255)
                throw new IllegalArgumentException("Invalid block visual");
        }
    }
    // null visual = deletion; full frames contain no deletions.
    public record Change(Position position, Visual visual) {}
    public record Frame(String dimension, String scope, long revision,
                        boolean full, List<Change> changes) {
        public Frame {
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(scope);
            changes = List.copyOf(changes);
        }
    }

    private String dimension = "";
    private String scope = "";
    private long revision = -1;
    private final Map<Position, Visual> blocks = new HashMap<>();

    public String dimension() { return dimension; }
    public String scope() { return scope; }
    public long revision() { return revision; }
    public Map<Position, Visual> blocks() { return Map.copyOf(blocks); }

    /**
     * Apply only ordered frames. A scope switch requires a new full frame,
     * so a lost/late incremental update can never paint the wrong dimension.
     */
    public boolean accept(Frame frame) {
        if (frame.revision() <= revision) return false;
        if (!frame.full() &&
            (!frame.dimension().equals(dimension) || !frame.scope().equals(scope))) {
            return false;
        }
        if (frame.full()) {
            blocks.clear();
            dimension = frame.dimension();
            scope = frame.scope();
        }
        for (Change change : frame.changes()) {
            if (change.visual() == null) blocks.remove(change.position());
            else blocks.put(change.position(), change.visual());
        }
        revision = frame.revision();
        return true;
    }

    public void clear() {
        dimension = "";
        scope = "";
        revision = -1;
        blocks.clear();
    }

    /** Only changed cells (including deletions); no full-volume resend. */
    public static List<Change> difference(
        Map<Position, Visual> previous,
        Map<Position, Visual> next
    ) {
        List<Change> delta = new ArrayList<>();
        for (var e : next.entrySet()) {
            if (!Objects.equals(previous.get(e.getKey()), e.getValue())) {
                delta.add(new Change(e.getKey(), e.getValue()));
            }
        }
        for (Position pos : previous.keySet()) {
            if (!next.containsKey(pos)) delta.add(new Change(pos, null));
        }
        return List.copyOf(delta);
    }

    public static List<Change> complete(Map<Position, Visual> next) {
        List<Change> changes = new ArrayList<>(next.size());
        next.forEach((pos, visual) -> changes.add(new Change(pos, visual)));
        return List.copyOf(changes);
    }
}
