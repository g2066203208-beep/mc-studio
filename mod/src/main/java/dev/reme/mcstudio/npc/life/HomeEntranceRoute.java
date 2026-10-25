package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Real, bounded open-door route for residents returning to a bed INSIDE a
 * building. Vanilla A* aimed straight at a bed often stops on the outside
 * wall; "always move closer to home" road heuristics cannot first move AWAY
 * from the bed to reach the actual door. This stages an exterior door tile,
 * then an interior door tile. Both are physical, collision-checked feet
 * positions. No teleport, terrain editing or chunk tickets. Pending door\n * ownership uses separate SavedData; existing NPC NBT remains unchanged.
 */
public final class HomeEntranceRoute {
    private HomeEntranceRoute() {}
    private static final int DOOR_SCAN_RADIUS = 16;
    public record Entrance(BlockPos outside, BlockPos inside) {}

    // A one-block wooden doorway cannot be approached by an entire crowd at
    // once: two active vanilla A* paths can push both walkers onto the same
    // outside tile and neither crosses. Serialize the FINAL door approach,
    // never the town's whole pavement. Transient weak owners avoid old-NBT
    // changes and do not retain despawned NPCs or unloaded worlds.
    private static final Map<ServerLevel, Map<Long, DoorPassage>> PASSAGES =
        new WeakHashMap<>();
    private static final int MAX_PASSAGES = 256;
    private static final int PASSAGE_STALE_TICKS = 160;

    private static final class DoorPassage {
        final WeakReference<PlayerNpcEntity> owner;
        long lastSeenTick;
        DoorPassage(PlayerNpcEntity npc, long tick) {
            owner = new WeakReference<>(npc);
            lastSeenTick = tick;
        }
    }

    /**
     * A resident may walk toward the physical threshold only when it owns
     * this door's short-lived passage. The next resident gets its turn as
     * soon as the first has ACTUALLY crossed to the indoor side, died, left
     * this dimension or stopped refreshing its journey. This is a navigation
     * courtesy queue, not a lock on the vanilla block or a teleport.
     */
    private static boolean mayApproachDoor(PlayerNpcEntity npc,
            ServerLevel level, BlockPos door, Entrance entrance) {
        long now = level.getGameTime();
        Map<Long, DoorPassage> passages =
            PASSAGES.computeIfAbsent(level, unused -> new HashMap<>());
        // Bounded cleanup also prevents a disappeared resident from holding
        // its door forever. The weak owner does not pin the entity in memory.
        passages.entrySet().removeIf(entry -> {
            DoorPassage passage = entry.getValue();
            PlayerNpcEntity owner = passage.owner.get();
            return owner == null || !owner.isAlive() || owner.level() != level
                || now < passage.lastSeenTick
                || now - passage.lastSeenTick > PASSAGE_STALE_TICKS;
        });
        long key = door.asLong();
        DoorPassage passage = passages.get(key);
        if (passage != null) {
            PlayerNpcEntity owner = passage.owner.get();
            BlockPos feet = owner.blockPosition();
            // Being in the door block is NOT yet successful passage. Only
            // the genuine indoor side releases the next waiting resident.
            boolean crossed = feet.distSqr(entrance.inside())
                    < feet.distSqr(entrance.outside())
                && !feet.equals(door)
                && feet.distSqr(entrance.inside()) <= 4;
            if (crossed) {
                passages.remove(key);
                passage = null;
            } else if (owner != npc) {
                return false;
            }
        }
        if (passage == null && passages.size() >= MAX_PASSAGES) return false;
        passages.put(key, new DoorPassage(npc, now));
        return true;
    }

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
                    // A doorway must have both matching vanilla halves. A
                    // damaged/replaced upper half is not a traversable door,
                    // even if the lower block still advertises OPEN=true.
                    // Never route through a closed redstone-powered door.
                    if (!level.hasChunkAt(door.above())) continue;
                    BlockState upper = level.getBlockState(door.above());
                    if (upper.getBlock() != state.getBlock()
                        || upper.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                        || upper.getValue(DoorBlock.FACING) != state.getValue(DoorBlock.FACING)
                        || upper.getValue(DoorBlock.HINGE) != state.getValue(DoorBlock.HINGE)
                        || upper.getValue(DoorBlock.OPEN) != state.getValue(DoorBlock.OPEN)
                        || upper.getValue(DoorBlock.POWERED) != state.getValue(DoorBlock.POWERED)
                        || (!state.getValue(DoorBlock.OPEN)
                            && state.getValue(DoorBlock.POWERED))) continue;
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
     * written to existing NPC entity NBT; ownership uses new SavedData.
     */
    // SavedData is independent of legacy resident NBT and survives world saves.
    private static boolean canTrackNpcOpenedDoor(ServerLevel level, BlockPos door) {
        return NpcOpenedDoorData.get(level).canTrack(door);
    }

    private static void rememberNpcOpenedDoor(ServerLevel level, BlockPos door) {
        NpcOpenedDoorData.get(level).remember(door, level.getGameTime());
    }

    /**
     * A human player's real right-click takes ownership of a wooden door.
     * Otherwise an NPC's pending auto-close watch could shut a door the
     * player deliberately closed and re-opened before the 50-tick timeout.
     * This listener never cancels vanilla interaction or modifies the door.
     * Both halves map to the same lower-block watch key.
     */
    public static void onPlayerUseDoor(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || !level.getServer().isSameThread()) return;
        BlockPos clicked = event.getPos();
        if (!level.hasChunkAt(clicked)) return;
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof DoorBlock)) return;
        relinquishNpcDoorForPlayer(level, clicked);
    }

    /**
     * Remove only the matching NPC auto-close watch when a player operates
     * either half of the door. Kept separate from the event for native
     * regression testing; this never opens, closes or teleports anything.
     */
    public static void relinquishNpcDoorForPlayer(ServerLevel level, BlockPos clicked) {
        if (!level.getServer().isSameThread() || !level.hasChunkAt(clicked)) return;
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof DoorBlock)) return;
        BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
            ? clicked.below() : clicked;
        NpcOpenedDoorData.get(level).relinquish(lower);
    }

    /**
     * NeoForge's per-level post tick continues to service NPC-opened doors
     * even after the last resident dies, despawns or leaves this dimension.
     * It never loads chunks and only touches the bounded pending-door set.
     */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) tickOpenedDoors(level);
    }

    /**
     * Called from the real server-level post tick, at most ONCE per
     * dimension/game tick. A wooden door closes only after the opening grace
     * period and only when its physical threshold is clear of ALL living
     * entities (including players and other NPCs). An occupied entrance stays
     * open, never closes into a person. Powered, replaced, broken, mismatched
     * or unloaded doors are left alone. Pending watches remain bounded (256
     * per dimension) even during long occupancy or temporary chunk unload;
     * an age-based expiry would strand a real wooden door open forever.
     */
    public static void tickOpenedDoors(ServerLevel level) {
        if (!level.getServer().isSameThread()) return;
        NpcOpenedDoorData.get(level).tickOpenedDoors(level);
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
        // Residents already on the actual indoor threshold do not need to
        // reserve an exterior approach. Other residents must take turns;
        // waiting does not bypass walls, move an entity or modify a door.
        boolean alreadyInside = here.equals(inside);
        if (!alreadyInside && !mayApproachDoor(npc, level, door, entrance)) {
            npc.getNavigation().stop();
            npc.setAction("waiting for another resident to clear real wooden doorway");
            return true;
        }
        if (here.distSqr(outside) <= 2 || here.distSqr(inside) <= 2) {
            BlockState state = level.getBlockState(door);
            if (state.getBlock() instanceof DoorBlock block
                && DoorBlock.isWoodenDoor(state)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && !state.getValue(DoorBlock.POWERED)
                && !state.getValue(DoorBlock.OPEN)) {
                // Never open an untracked 257th door: without a watch it
                // could remain open indefinitely after the NPC walks away.
                if (!canTrackNpcOpenedDoor(level, door)) {
                    npc.setAction("waiting for safe wooden-door tracking capacity");
                    return false;
                }
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
        // A resident rounding the SIDE of a house can be geometrically
        // closer to the inside tile even though a solid facade separates
        // them. If native A* cannot reach that tile, approach the real
        // outside threshold instead of repeatedly navigating into the wall.
        // Never switch a resident already standing on the indoor threshold
        // back outside; the next bed/work planner tick owns that case.
        if (target.equals(inside) && !here.equals(inside)
            && (path == null || !path.canReach())) {
            Path outsidePath = npc.getNavigation().createPath(outside, 0);
            if (outsidePath != null && outsidePath.canReach()) {
                target = outside;
                path = outsidePath;
            }
        }
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
