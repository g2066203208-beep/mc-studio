package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Local, bounded road preference over vanilla pathfinding (not a global road network). */
public final class RoadNavigator {
    private RoadNavigator() {}

    public static boolean moveToward(PlayerNpcEntity npc, ServerLevel level,
                                     BlockPos destination, double speed) {
        if (!level.hasChunkAt(destination)) {
            npc.setAction("destination chunk unloaded; not force loading");
            return false;
        }
        double current = npc.blockPosition().distSqr(destination);
        if (current < 5) return false;
        BlockPos best = null;
        double bestScore = 0;
        BlockPos center = npc.blockPosition();
        // Use existing foot paths if they make progress toward destination.
        // Scan floor only within 7 blocks; no new chunk loads or global A*.
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            BlockPos foot = center.offset(dx, 0, dz);
            if (!level.hasChunkAt(foot)) continue;
            if (!level.getBlockState(foot).isAir()) continue;
            BlockState floor = level.getBlockState(foot.below());
            if (!(floor.is(Blocks.DIRT_PATH) || floor.is(Blocks.GRAVEL) ||
                floor.is(Blocks.COBBLESTONE) || floor.is(Blocks.STONE_BRICKS) ||
                floor.is(Blocks.MOSSY_COBBLESTONE))) continue;
            double d = foot.distSqr(destination);
            double progress = current - d;
            if (progress <= 10) continue;
            double score = progress - dx * dx * 0.08 - dz * dz * 0.08;
            if (score > bestScore) { bestScore = score; best = foot.immutable(); }
        }
        BlockPos waypoint = best == null ? destination : best;
        boolean moving = npc.getNavigation().moveTo(waypoint.getX() + 0.5,
            waypoint.getY(), waypoint.getZ() + 0.5, speed);
        if (moving) {
            npc.setAction(best == null ? "navigating toward destination"
                : "following nearby road toward destination");
        }
        return moving;
    }
}
