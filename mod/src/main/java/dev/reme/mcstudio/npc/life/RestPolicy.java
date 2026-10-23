package dev.reme.mcstudio.npc.life;

import net.minecraft.core.BlockPos;

/**
 * Physical bedtime policy shared by the server's needs, jobs and visual pose.
 * "Decided to sleep" and "actually reached home" are different states.
 * Native physical bed sleep is managed by ResidentBedLife; fallback at home
 * without a bed is intentionally much slower.
 */
public final class RestPolicy {
    private static final int HOME_DISTANCE_SQUARED = 6;
    private static final int GUARD_WATCH_ENERGY = 35;
    private RestPolicy() {}

    public static boolean atHome(BlockPos current, BlockPos home) {
        return current != null && home != null
            && current.distSqr(home) <= HOME_DISTANCE_SQUARED;
    }

    public static boolean guardOnWatch(boolean guard, int energy) {
        return guard && energy >= GUARD_WATCH_ENERGY;
    }

    public static boolean shouldRest(LivingPlanner.Intent intent,
                                     boolean guard, int energy) {
        return intent == LivingPlanner.Intent.SLEEP && !guardOnWatch(guard, energy);
    }

    /**
     * Rotating guards still need a finite nightly rest opportunity. A guard
     * above the emergency 35-energy watch threshold could previously patrol
     * all night and only become tired AFTER sunrise, never using its bed.
     * This is an actual time/need gate, not a test-specific instant sleep:
     * the officer must still walk home through a real door to a native bed.
     */
    public static boolean lateWatchRecovery(boolean guard, long shiftedDayTime,
                                            int energy) {
        long clock = Math.floorMod(shiftedDayTime, 24000L);
        return guard && clock >= 18000L && energy < 55;
    }

    public static boolean blocksRoutine(LivingPlanner.Intent intent,
                                        boolean guard, int energy) {
        return intent == LivingPlanner.Intent.ESCAPE
            || shouldRest(intent, guard, energy);
    }

    /** Change to apply on the 200-tick needs cadence; never per render frame. */
    public static int energyDelta(LivingPlanner.Intent intent, boolean guard,
                                  int energy, boolean sleepingInBed,
                                  boolean reachedHome, boolean afraid, boolean detained) {
        if (afraid || detained) return -3;
        // Once a tired watch officer is PHYSICALLY asleep in a vanilla bed,
        // real rest restores energy just like every other citizen; the
        // regular watch threshold must not make bed sleep drain energy.
        if (intent == LivingPlanner.Intent.SLEEP && sleepingInBed) return 12;
        if (!shouldRest(intent, guard, energy)) return -3;
        return reachedHome ? 2 : -3;
    }
}
