package dev.reme.worldseam;

/**
 * Native client chunk window for a world that may become Minecraft.level
 * within the same frame. A 5x5 remote seam prefetch is NOT a substitute for
 * the full vanilla active-world view radius.
 *
 * Runtime ClientChunkCache uses extra storage padding internally. Its
 * constructor argument is the configured view distance, not a number of
 * redirected packet rings.
 */
public final class SeamNativeViewDistance {
    private SeamNativeViewDistance() {}
    public static final int MIN = 3;
    public static final int MAX = 32;
    public static final int PREFETCH_RADIUS = 2;

    public static int normalize(int requested) {
        return Math.max(MIN, Math.min(MAX, requested));
    }

    public static boolean shouldRequireVanillaFarChunk(int clientDistance) {
        return normalize(clientDistance) >= 6;
    }

    /** Four chunks out is never part of the separate 5x5 prewarm. */
    public static int farChunkX(int centerX) {
        return Math.addExact(centerX,PREFETCH_RADIUS+2);
    }
}
