package dev.reme.mcstudio.climb;

/**
 * Deterministic scalar rules shared by server/client. Isolated from Minecraft
 * so they can be run under JUnit without booting a game or loading an actual world.
 */
public final class GripRules {
    public static final double MAX_EXTENSION = 1.85;
    private GripRules() {}

    public static boolean validIntent(int incoming, int previous, float forward, float strafe) {
        return incoming > previous && Float.isFinite(forward) && Float.isFinite(strafe)
                && Math.abs(forward) <= 1.001f && Math.abs(strafe) <= 1.001f;
    }

    public static double inwardCorrection(double distance) {
        if (!Double.isFinite(distance) || distance <= MAX_EXTENSION) return 0;
        return Math.min(0.20, (distance - MAX_EXTENSION) * 0.40);
    }

    public static boolean snapTooFar(double carrierMotionPerTick) {
        return !Double.isFinite(carrierMotionPerTick) || carrierMotionPerTick > 1.5;
    }

    public static double allowedSpeed(double carrierMotionPerTick) {
        return 0.33 + Math.max(0.0, Math.min(1.5, carrierMotionPerTick));
    }
}
