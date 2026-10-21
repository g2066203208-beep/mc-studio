package dev.reme.mcstudio.npc.life;

/**
 * Deterministic, cheap rotating patrol points around a resident guard post.
 * All choices are local and never require global scans.
 */
public final class PatrolPlanner {
    public record Step(int dx, int dz) {}
    private PatrolPlanner() {}

    public static Step choose(long tick, int guardId) {
        int quarter = (int) Math.floorMod(Math.floorDiv(tick, 260L) +
            Math.floorMod(guardId, 4), 4);
        return switch (quarter) {
            case 0 -> new Step(8, 0);
            case 1 -> new Step(0, 8);
            case 2 -> new Step(-8, 0);
            default -> new Step(0, -8);
        };
    }
}
