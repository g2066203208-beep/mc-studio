package dev.reme.worldseam;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Strict dimension-aware, bounded remote chunk-section cache.
 * Sections are neither ClientLevel chunks nor spoofed vanilla packets.
 * Ownership is keyed by a server-side stream revision and scope.
 */
public final class SeamSectionCache {
    public static final int MAX_SECTIONS = 48;
    public record Key(int chunkX, int sectionY, int chunkZ) {}
    public record Frame(String source, String target, String scope,
                        long revision, boolean clear, Key key,
                        int sourceBoundary, int targetBoundary,
                        SeamSectionCodec.Encoded content) {
        public Frame {
            Objects.requireNonNull(source);
            Objects.requireNonNull(target);
            Objects.requireNonNull(scope);
            if (!clear && ((key == null) != (content == null)))
                throw new IllegalArgumentException("Section key/content must both exist or both be null");
        }
    }
    public record Entry(int sourceBoundary, int targetBoundary,
                        SeamSectionCodec.Encoded encoded) {}

    private String source = "";
    private String target = "";
    private String scope = "";
    private long lastRevision = -1;
    private final LinkedHashMap<Key, Entry> sections =
        new LinkedHashMap<>(64, .75f, true);

    public boolean apply(Frame frame) {
        if (frame.revision() <= lastRevision) return false;
        if (frame.clear()) {
            sections.clear();
            source = frame.source();
            target = frame.target();
            scope = "";
        } else if (frame.key() == null) {
            // A heartbeat renews freshness without recompiling any models.
            // Heartbeats cannot switch the active dimension or viewport.
            if (!source.equals(frame.source()) ||
                !target.equals(frame.target()) || !scope.equals(frame.scope()))
                return false;
        } else {
            if (!source.equals(frame.source()) ||
                !target.equals(frame.target()) ||
                !scope.equals(frame.scope())) {
                sections.clear();
                source = frame.source();
                target = frame.target();
                scope = frame.scope();
            }
            sections.put(frame.key(), new Entry(frame.sourceBoundary(),
                frame.targetBoundary(), frame.content()));
            while (sections.size() > MAX_SECTIONS)
                sections.remove(sections.keySet().iterator().next());
        }
        lastRevision = frame.revision();
        return true;
    }

    public boolean isFor(String currentDimension) {
        return !scope.isEmpty() && source.equals(currentDimension);
    }
    public int size() { return sections.size(); }
    public long lastRevision() { return lastRevision; }
    public Map<Key, Entry> snapshot() { return Map.copyOf(sections); }
    public void reset() {
        sections.clear();
        source = "";
        target = "";
        scope = "";
        lastRevision = -1;
    }
}
