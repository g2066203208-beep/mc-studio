package dev.reme.worldseam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Coalesces native chunk surface rebuilds from incoming packets.
 * Pure Java so geometry scheduling can be tested without an OpenGL context.
 *
 * Packet handlers enqueue; the client tick performs a strictly bounded amount
 * of actual block traversal, nearest to the player's view first.
 */
public final class SeamGeometryQueue {
    private final Set<Long> pending = new LinkedHashSet<>();

    public static long pack(int x, int z) {
        return ((long)x << 32) | (z & 0xffffffffL);
    }

    public static int x(long packed) { return (int)(packed >> 32); }
    public static int z(long packed) { return (int)packed; }

    public void invalidate(int cx, int cz) {
        pending.add(pack(cx, cz));
        pending.add(pack(cx - 1, cz));
        pending.add(pack(cx + 1, cz));
        pending.add(pack(cx, cz - 1));
        pending.add(pack(cx, cz + 1));
    }

    /**
     * Discard departed-window work and process at most budget columns.
     * This avoids packet-burst stalls, unnecessary hidden columns, and
     * starvation of the central seam by newly arrived outer-ring chunks.
     */
    public List<Long> drain(int budget, int centerX, int centerZ, int radius) {
        if (budget < 0 || radius < 0 || radius > 8)
            throw new IllegalArgumentException("Invalid geometry build budget/window");
        pending.removeIf(key ->
            Math.abs((long)x(key) - centerX) > radius ||
            Math.abs((long)z(key) - centerZ) > radius
        );
        if (budget == 0 || pending.isEmpty()) return List.of();
        var closest = new ArrayList<>(pending);
        closest.sort(Comparator.comparingLong(key -> {
            long dx = (long)x(key) - centerX, dz = (long)z(key) - centerZ;
            return dx * dx + dz * dz;
        }));
        var selected = List.copyOf(closest.subList(0, Math.min(budget, closest.size())));
        pending.removeAll(selected);
        return selected;
    }

    public int pending() { return pending.size(); }
    public void clear() { pending.clear(); }
}
