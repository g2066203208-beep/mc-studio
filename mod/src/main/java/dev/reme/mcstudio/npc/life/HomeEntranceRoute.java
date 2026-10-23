package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Real, bounded open-door route for residents returning to a bed INSIDE a
 * building. Vanilla A* aimed straight at a bed often stops on the outside
 * wall; "always move closer to home" road heuristics cannot first move AWAY
 * from the bed to reach the actual door. This stages an exterior door tile,
 * then an interior door tile. Both are physical, collision-checked feet
 * positions. No teleport, terrain editing, tickets or new persisted NBT.
 */
public final class HomeEntranceRoute {
    private HomeEntranceRoute() {}
    private static final int DOOR_SCAN_RADIUS = 16;

    public record Entrance(BlockPos outside, BlockPos inside) {}

    private static boolean standable(ServerLevel level, BlockPos at) {
        return level.hasChunkAt(at) && level.hasChunkAt(at.above())
            && level.hasChunkAt(at.below())
            && level.getBlockState(at).isAir()
            && level.getBlockState(at.above()).isAir()
            && level.getBlockState(at.below()).isSolid();
    }

    /**
     * Find a genuine OPEN wooden doorway leading toward this resident's home.
     * A closed iron jail gate or decorative solid wall is never a home entry.
     * All probes are loaded-only and bounded to 33x33x3 around saved home.
     */
    @Nullable public static Entrance nearestOpenDoor(ServerLevel level, BlockPos home) {
        BlockPos bestDoor = null;
        Entrance best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -DOOR_SCAN_RADIUS; dx <= DOOR_SCAN_RADIUS; dx++) {
                for (int dz = -DOOR_SCAN_RADIUS; dz <= DOOR_SCAN_RADIUS; dz++) {
                    BlockPos door = home.offset(dx, dy, dz);
                    if (!level.hasChunkAt(door)) continue;
                    BlockState state = level.getBlockState(door);
                    if (!(state.getBlock() instanceof DoorBlock)
                        || state.is(Blocks.IRON_DOOR)
                        || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                        || !state.getValue(DoorBlock.OPEN)) continue;
                    Direction normal = state.getValue(DoorBlock.FACING);
                    BlockPos a = door.relative(normal);
                    BlockPos b = door.relative(normal.getOpposite());
                    if (!standable(level, a) || !standable(level, b)) continue;
                    // A door belongs to the resident's house only if it
                    // opens from the farther outside tile into a tile closer
                    // to the resident's persisted indoor home/bed aisle.
                    double da = home.distSqr(a), db = home.distSqr(b);
                    if (da == db) continue;
                    BlockPos inside = da < db ? a : b;
                    BlockPos outside = da < db ? b : a;
                    double score = home.distSqr(door);
                    if (score < bestScore || (score == bestScore
                        && (bestDoor == null || door.asLong() < bestDoor.asLong()))) {
                        bestDoor = door.immutable();
                        best = new Entrance(outside.immutable(), inside.immutable());
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Attempts a true reachable A* path first. A distant entrance is reached
     * through local real paving via RoadNavigator, now targeting the DOOR
     * instead of a bed hidden behind a solid building facade.
     */
    public static boolean moveTowardEntrance(PlayerNpcEntity npc,
                                              ServerLevel level, double speed) {
        return moveTowardEntrance(npc, level, npc.homePos(), speed);
    }

    /** Same physical doorway routing for a REAL indoor workplace (library,
     * hospital, shop). The caller supplies the actual saved work coordinate.
     * Neither routine assumes the target building is the NPC's home.
     */
    public static boolean moveTowardEntrance(PlayerNpcEntity npc,
                                              ServerLevel level,
                                              BlockPos indoorGoal, double speed) {
        Entrance entrance = nearestOpenDoor(level, indoorGoal);
        if (entrance == null) return false;
        BlockPos here = npc.blockPosition();
        BlockPos target = here.distSqr(entrance.outside()) <= 2
            ? entrance.inside() : entrance.outside();
        if (here.equals(target)) return false;
        Path path = npc.getNavigation().createPath(target, 0);
        if (path != null && path.canReach()
            && npc.getNavigation().moveTo(path, speed)) {
            npc.setAction("walking physically to open residential doorway");
            return true;
        }
        if (RoadNavigator.moveToward(npc, level, target, speed)) {
            npc.setAction("approaching actual open home door along paved road");
            return true;
        }
        return false;
    }
}
