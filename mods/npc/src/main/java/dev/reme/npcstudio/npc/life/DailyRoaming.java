package dev.reme.npcstudio.npc.life;

import dev.reme.npcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LIVE residents wander inside home jurisdiction, return if lost and never
 * demand unloaded chunks. Runs once per 160 ticks per resident, not per tick.
 */
public final class DailyRoaming {
    private DailyRoaming() {}

    public static boolean inRange(PlayerNpcEntity npc, BlockPos destination) {
        return MovementPolicy.within(
            npc.homePos().getX(), npc.homePos().getZ(),
            destination.getX(), destination.getZ(), npc.roamingRadius());
    }

    public static boolean tick(PlayerNpcEntity npc, ServerLevel level, long tick) {
        if (npc.mode() != PlayerNpcEntity.Mode.LIVE ||
            npc.escortLeader() != null) return false;
        BlockPos home = npc.homePos();
        boolean tooFar = !MovementPolicy.within(home.getX(), home.getZ(),
            npc.blockPosition().getX(), npc.blockPosition().getZ(),
            npc.roamingRadius() + 8);
        if (tooFar && MovementPolicy.due(tick, npc.getId(), 40)) {
            if (level.hasChunkAt(home)) {
                RoadNavigator.moveToward(npc, level, home, 1.12);
                npc.setAction("heading home; reached edge of life territory");
            }
            return true;
        }

        if (!MovementPolicy.due(tick, npc.getId(), 160)) return false;
        if (!npc.getNavigation().isDone() || npc.energy() < 26) return false;
        if (npc.intent() != LivingPlanner.Intent.IDLE &&
            npc.intent() != LivingPlanner.Intent.SOCIALIZE) return false;

        // No natural resident should leave home for a random wander at night.
        long clock = Math.floorMod(level.getDayTime() + npc.dailyShiftTicks(), 24000L);
        if (clock < 1500 || clock >= 12500) return false;
        BlockPos current = npc.blockPosition();
        int r = Math.min(npc.roamingRadius(), 24);
        for (int attempt = 0; attempt < 6; attempt++) {
            int dx = npc.getRandom().nextInt(2 * r + 1) - r;
            int dz = npc.getRandom().nextInt(2 * r + 1) - r;
            BlockPos target = current.offset(dx, 0, dz);
            if (current.distSqr(target) < 16 || !inRange(npc, target)
                || !level.hasChunkAt(target)) continue;
            // A cheap loaded-chunk walkable target test. Navigation validates
            // actual reachability; never transform terrain or load chunks.
            BlockState floor = level.getBlockState(target.below());
            if (!floor.isSolid() ||
                !level.getBlockState(target).isAir() ||
                !level.getBlockState(target.above()).isAir()) continue;
            boolean moved = RoadNavigator.moveToward(npc, level, target, 0.80);
            if (moved) {
                npc.setAction(npc.intent() == LivingPlanner.Intent.SOCIALIZE ?
                    "strolling around neighborhood looking for company" :
                    "strolling inside home territory");
                return true;
            }
        }
        return false;
    }
}
