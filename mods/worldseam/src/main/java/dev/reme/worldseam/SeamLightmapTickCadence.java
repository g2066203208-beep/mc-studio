package dev.reme.worldseam;

/**
 * Match vanilla's once-per-ClientLevel-tick lightmap flicker update.
 *
 * A foreign LevelRenderer can draw multiple times per client tick, or a
 * dimension handoff can replace the secondary ClientLevel while preserving
 * the same game-time value. Both cases must keep independent cadence.
 * This class does not synthesize light or advance a foreign world.
 */
final class SeamLightmapTickCadence {
    private Object world;
    private long lastGameTime;

    boolean shouldTick(Object currentWorld, long gameTime) {
        if (currentWorld == null)
            throw new IllegalArgumentException("Secondary lightmap requires a real ClientLevel");
        if (world != currentWorld || lastGameTime != gameTime) {
            world = currentWorld;
            lastGameTime = gameTime;
            return true;
        }
        return false;
    }

    void reset() {
        world = null;
        lastGameTime = 0;
    }
}
