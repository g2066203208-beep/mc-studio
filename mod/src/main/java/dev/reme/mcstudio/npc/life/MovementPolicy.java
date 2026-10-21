package dev.reme.mcstudio.npc.life;

/** Pure math for settlement boundaries and deterministic AI cadence. */
public final class MovementPolicy {
    public static final int MIN_RADIUS = 16, MAX_RADIUS = 128;
    private MovementPolicy() {}

    public static int radius(int requested) {
        return Math.clamp(requested, MIN_RADIUS, MAX_RADIUS);
    }

    public static boolean within(int hx, int hz, int x, int z, int radius) {
        long dx = (long) x - hx, dz = (long) z - hz;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    public static int phase(int entityId, int interval) {
        if (interval <= 0) throw new IllegalArgumentException("non-positive interval");
        return Math.floorMod(entityId, interval);
    }

    public static boolean due(long tick, int entityId, int interval) {
        return Math.floorMod(tick, interval) == phase(entityId, interval);
    }

    public static int dailyShift(int personalitySeed) {
        // Spread morning schedules by at most 20 in-game minutes.
        return Math.floorMod(personalitySeed * 53, 800) - 400;
    }
}
