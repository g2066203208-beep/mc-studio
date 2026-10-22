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

    public static boolean blocksRoutine(LivingPlanner.Intent intent,
                                        boolean guard, int energy) {
        return intent == LivingPlanner.Intent.ESCAPE
            || shouldRest(intent, guard, energy);
    }

    /** Change to apply on the 200-tick needs cadence; never per render frame. */
    public static int energyDelta(LivingPlanner.Intent intent, boolean guard,
                                  int energy, boolean sleepingInBed,
                                  boolean reachedHome, boolean afraid, boolean detained) {
        if (!shouldRest(intent, guard, energy) || afraid || detained) return -3;
        if (sleepingInBed) return 12;
        return reachedHome ? 2 : -3;
    }
}
