package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
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
    private static final int AUTO_CLOSE_DELAY_TICKS = 50;
    private static final int MAX_TRACKED_DOORS_PER_LEVEL = 256;
    private static final int MAX_TRACKED_AGE_TICKS = 2400;

    // Transient, server-thread-only state. No new world NBT, chunk tickets,
    // or changes to player-opened doors. Weak keys avoid retaining a world
    // after unloading; values contain no references to the ServerLevel key.
    private static final Map<ServerLevel, DoorWatch> NPC_OPENED_DOORS =
        new WeakHashMap<>();

    private static final class DoorWatch {
        final Map<BlockPos, Long> openedAt = new HashMap<>();
        long lastProcessedTick = Long.MIN_VALUE;
    }

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
        return nearestDoor(level, home, false);
    }

    /** Route planning may also find a closed, hand-openable wooden door.
     * The public open-only query stays strict for callers that require
     * immediate passage; a closed iron or redstone-only gate is never used.
     */
    @Nullable private static Entrance nearestDoor(ServerLevel level,
            BlockPos home, boolean includeClosedWood) {
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
                        || (!state.getValue(DoorBlock.OPEN)
                            && (!includeClosedWood
                                || !DoorBlock.isWoodenDoor(state)))) continue;
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
     * Select a real foot waypoint without sending an already-indoor resident
     * back outside. The old rule only checked distance to the exterior tile:
     * a citizen deep inside the house would target the street again whenever
     * bedside A* temporarily failed (for example during crowd congestion).
     *
     * Outside citizens still approach the outside tile first; only someone
     * standing at the entrance or on its interior side targets the inside.
     * This chooses a navigation target, never changes the entity position.
     */
    public static BlockPos nextDoorStage(Entrance entrance, BlockPos here) {
        BlockPos outside = entrance.outside();
        BlockPos inside = entrance.inside();
        if (here.distSqr(outside) <= 2 || here.distSqr(inside) < here.distSqr(outside))
            return inside;
        return outside;
    }

    /**
     * Track ONLY doors actually opened by an NPC. Vanilla player-opened doors
     * are not ours to close. Memory is capped per loaded dimension and never
     * written to the existing world save format.
     */
    private static void rememberNpcOpenedDoor(ServerLevel level, BlockPos door) {
        DoorWatch watch = NPC_OPENED_DOORS.computeIfAbsent(level, ignored -> new DoorWatch());
        if (watch.openedAt.size() < MAX_TRACKED_DOORS_PER_LEVEL
            || watch.openedAt.containsKey(door)) {
            watch.openedAt.put(door.immutable(), level.getGameTime());
        }
    }

    /**
     * Called from each real NPC server tick, but processed at most ONCE per
     * dimension/game tick. A wooden door closes only after the opening grace
     * period and only when its physical threshold is clear of ALL living
     * entities (including players and other NPCs). An occupied entrance stays
     * open, never closes into a person. Powered, replaced, broken, mismatched
     * or unloaded doors are left alone. Expired watches are forgotten rather
     * than interfering with an entrance controlled by somebody else.
     */
    public static void tickOpenedDoors(ServerLevel level) {
        if (!level.getServer().isSameThread()) return;
        DoorWatch watch = NPC_OPENED_DOORS.get(level);
        if (watch == null) return;
        long now = level.getGameTime();
        if (watch.lastProcessedTick == now) return;
        watch.lastProcessedTick = now;
        Iterator<Map.Entry<BlockPos, Long>> iterator = watch.openedAt.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> pending = iterator.next();
            long age = now - pending.getValue();
            if (age < 0 || age > MAX_TRACKED_AGE_TICKS) {
                iterator.remove();
                continue;
            }
            if (age < AUTO_CLOSE_DELAY_TICKS) continue;
            BlockPos door = pending.getKey();
            if (!level.hasChunkAt(door) || !level.hasChunkAt(door.above())) {
                iterator.remove();
                continue;
            }
            BlockState lower = level.getBlockState(door);
            BlockState upper = level.getBlockState(door.above());
            if (!(lower.getBlock() instanceof DoorBlock)
                || !DoorBlock.isWoodenDoor(lower)
                || upper.getBlock() != lower.getBlock()
                || lower.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                || upper.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                || lower.getValue(DoorBlock.FACING) != upper.getValue(DoorBlock.FACING)
                || lower.getValue(DoorBlock.HINGE) != upper.getValue(DoorBlock.HINGE)
                || lower.getValue(DoorBlock.OPEN) != upper.getValue(DoorBlock.OPEN)
                || lower.getValue(DoorBlock.POWERED) != upper.getValue(DoorBlock.POWERED)
                || !lower.getValue(DoorBlock.OPEN)
                || lower.getValue(DoorBlock.POWERED)) {
                iterator.remove();
                continue;
            }
            // Inflated vanilla doorway AABB covers both standing sides and
            // the two-block height. Never close on a passerby or a player.
            if (!level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(door).inflate(1.0D, 1.0D, 1.0D),
                    LivingEntity::isAlive).isEmpty()) continue;
            ((DoorBlock) lower.getBlock()).setOpen(null, level, lower, door, false);
            iterator.remove();
        }
        if (watch.openedAt.isEmpty()) NPC_OPENED_DOORS.remove(level);
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
        if (!level.getServer().isSameThread() || !npc.isAlive()) return false;
        Entrance entrance = nearestDoor(level, indoorGoal, true);
        if (entrance == null) return false;
        BlockPos here = npc.blockPosition();
        // Only a resident actually standing beside their selected house/work
        // entrance may operate its wooden door. Vanilla setOpen updates BOTH
        // halves and emits the genuine opening sound; no block is fabricated.
        // Never open iron doors, powered gates or doors in unloaded chunks.
        BlockPos outside = entrance.outside(), inside = entrance.inside();
        BlockPos door = new BlockPos((outside.getX() + inside.getX()) / 2,
            outside.getY(), (outside.getZ() + inside.getZ()) / 2);
        if (here.distSqr(outside) <= 2 || here.distSqr(inside) <= 2) {
            BlockState state = level.getBlockState(door);
            if (state.getBlock() instanceof DoorBlock block
                && DoorBlock.isWoodenDoor(state)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && !state.getValue(DoorBlock.OPEN)) {
                block.setOpen(npc, level, state, door, true);
                if (level.getBlockState(door).getValue(DoorBlock.OPEN)) {
                    rememberNpcOpenedDoor(level, door);
                    npc.setAction("physically opened the wooden home/work door");
                }
            }
        }
        BlockPos target = nextDoorStage(entrance, here);
        if (here.equals(target)) return false;
        Path path = npc.getNavigation().createPath(target, 0);
        if (path != null && path.canReach()
            && npc.getNavigation().moveTo(path, speed)) {
            npc.setAction("walking through a real home/work doorway");
            return true;
        }
        if (RoadNavigator.moveToward(npc, level, target, speed)) {
            npc.setAction("approaching actual home/work door along paved road");
            return true;
        }
        return false;
    }
}
