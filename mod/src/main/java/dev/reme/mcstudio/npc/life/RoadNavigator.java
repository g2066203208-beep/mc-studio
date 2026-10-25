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
        return moveTowardInternal(npc, level, destination, speed, false);
    }

    /**
     * A lawful, already handcuffed prisoner may cross the normal home radius
     * ONLY toward the physically built SOUTH intake of their own assigned
     * holding cell. Ordinary residents still obey their home mobility bounds.
     * No teleport, ticket, global navigation or changed save/NBT format.
     */
    public static boolean moveTowardCustodyIntake(PlayerNpcEntity npc,
                                                   ServerLevel level,
                                                   BlockPos destination,
                                                   double speed) {
        if (!level.getServer().isSameThread() || !npc.isAlive()
            || !npc.isDetained(level.getGameTime())
            || npc.detentionJail() == null
            || !destination.equals(npc.detentionJail().south(4)))
            return false;
        return moveTowardInternal(npc, level, destination, speed, true);
    }

    private static boolean moveTowardInternal(PlayerNpcEntity npc, ServerLevel level,
                                              BlockPos destination, double speed,
                                              boolean authorizedCustody) {
        if (npc.mode() == PlayerNpcEntity.Mode.LIVE &&
            npc.escortLeader() == null && !authorizedCustody &&
            !DailyRoaming.inRange(npc, destination)) {
            npc.setAction("destination outside home mobility radius");
            return false;
        }
        if (!level.hasChunkAt(destination)) {
            npc.setAction("destination chunk unloaded; not force loading");
            return false;
        }
        long tick = level.getGameTime();
        // Failed paths must be retried: never report movement solely because
        // a cooldown is active. Delay replanning only while a path actually runs.
        if (!npc.getNavigation().isDone() && !npc.canRepath(destination, tick)) return true;
        double current = npc.blockPosition().distSqr(destination);
        if (current < 5) return false;
        FootTrafficData traffic = FootTrafficData.get(level);
        // Only four physically grounded nearby waypoints can invoke vanilla
        // A*; old implementation picked ONE highest-scored surface even if
        // a solid building separated the NPC from it, then got permanently
        // stuck while a perfectly reachable sidewalk lay one block over.
        BlockPos[] candidates = new BlockPos[4];
        double[] scores = {0,0,0,0};
        BlockPos center = npc.blockPosition();
        // Use existing foot paths if they make progress toward destination.
        // Scan floor only within 7 blocks; no new chunk loads or global A*.
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            BlockPos foot = center.offset(dx, 0, dz);
            if (!level.hasChunkAt(foot)) continue;
            if (!level.getBlockState(foot).isAir()) continue;
            BlockState floor = level.getBlockState(foot.below());
            // Municipal road surfaces are REAL vanilla paving, not only
            // ancient footpaths. Previously the AI ignored every concrete
            // avenue and smooth-stone sidewalk, trying unreachable direct
            // straight lines through homes instead of the available streets.
            boolean physicalRoad = floor.is(Blocks.DIRT_PATH)
                || floor.is(Blocks.GRAVEL) || floor.is(Blocks.COBBLESTONE)
                || floor.is(Blocks.STONE_BRICKS)
                || floor.is(Blocks.MOSSY_COBBLESTONE)
                || floor.is(Blocks.GRAY_CONCRETE)
                || floor.is(Blocks.WHITE_CONCRETE)
                || floor.is(Blocks.YELLOW_CONCRETE)
                || floor.is(Blocks.SMOOTH_STONE)
                || floor.is(Blocks.POLISHED_ANDESITE);
            int knownTraffic = traffic.visits(foot);
            if (!physicalRoad && knownTraffic < 3) continue;
            if (!level.getBlockState(foot.above()).isAir()) continue;
            double d = foot.distSqr(destination);
            double progress = current - d;
            if (progress <= 10) continue;
            double score = progress - dx * dx * 0.08 - dz * dz * 0.08
                + Math.min(knownTraffic, 20) * 0.6;
            for(int slot=0;slot<candidates.length;slot++)
                if(score>scores[slot]){
                    for(int prev=candidates.length-1;prev>slot;prev--){
                        candidates[prev]=candidates[prev-1];
                        scores[prev]=scores[prev-1];
                    }
                    candidates[slot]=foot.immutable();
                    scores[slot]=score;
                    break;
                }
        }
        for (BlockPos waypoint:candidates) {
            if(waypoint==null)break;
            // Actual vanilla A* must accept a reachable route. The first
            // candidate can be behind real blocks despite sharing a sidewalk
            // height. Never declare a ghost route just for its lower distance.
            // PathNavigation.moveTo(double,...) can accept a PARTIAL path
            // ending at a wall. Such a path looked active (isDone=false)
            // even though the resident stood on the same tile all night.
            // A street waypoint must be reachable by native A* before
            // claiming that this leg of the journey is underway.
            var path = npc.getNavigation().createPath(waypoint, 0);
            if (path != null && path.canReach()
                && npc.getNavigation().moveTo(path, speed)) {
                npc.setAction("following reachable real paved street waypoint");
                return true;
            }
        }
        // Do not leave a resident repeatedly walking a partial path into
        // a solid facade. The caller may try a real door or another route;
        // an unreachable destination is NOT a successful commute.
        var directPath = npc.getNavigation().createPath(destination, 0);
        if (directPath == null || !directPath.canReach()) return false;
        boolean direct = npc.getNavigation().moveTo(directPath, speed);
        if (direct) npc.setAction("navigating fully reachable vanilla route");
        return direct;
    }
}
