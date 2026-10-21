package dev.reme.mcstudio.climb;

/** Client animation transitions: pure Java so JUnit can catch a release replay loop. */
public final class PeakAnimationPolicy {
    public static final int NONE = -1, HANG = 0, ASCEND = 1, TRAVERSE = 2;
    public static final int MANTLE = 3, CEILING_HANG = 4, CEILING_CRAWL = 5, CEILING_TRAVERSE = 6;
    public static final int RELEASE = 7, ROOF_ENTRY = 8, WALL_JUMP = 9, CEILING_RELEASE = 10;
    public static final int RELEASE_DURATION_TICKS = 9;
    private PeakAnimationPolicy() {}

    public static int requested(boolean active, int action) {
        return active && ((action >= HANG && action <= CEILING_TRAVERSE)
                || action == ROOF_ENTRY || action == WALL_JUMP) ? action : NONE;
    }

    public static int next(int current, int desired, int tick, int releaseUntil) {
        if (desired != NONE) return desired;
        if (current == NONE) return NONE;
        if (current == RELEASE || current == CEILING_RELEASE)
            return tick >= releaseUntil ? NONE : current;
        return current >= CEILING_HANG && current <= CEILING_TRAVERSE
                ? CEILING_RELEASE : RELEASE;
    }

    public static boolean isRelease(int action) {
        return action == RELEASE || action == CEILING_RELEASE;
    }
}
