package dev.reme.worldseam;

import java.util.List;

/**
 * A snapshot can be re-queued by repeated chunk packets without changing
 * its actual block/light content. Keep its object identity and GPU VBO.
 */
public final class SeamStableSnapshot {
    private SeamStableSnapshot() {}
    public static <T> boolean unchanged(List<T> existing,List<T> incoming) {
        return existing==incoming || (existing!=null && existing.equals(incoming));
    }
}
