package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * Bounded, server-authoritative REAL vanilla-bed sleep. Vanilla LivingEntity
 * startSleeping/stopSleeping owns the animation and bed occupancy state; we do
 * not teleport or fabricate a sleeping NPC by setting a fake pose.
 *
 * Claims survive in the NPC's NBT. Only loaded blocks and loaded nearby NPCs
 * are considered. This does not issue chunk tickets or claim player beds.
 */
public final class ResidentBedLife {
    private ResidentBedLife() {}
    private static final int SEARCH_RADIUS = 8;
    private static final int SEARCH_VERTICAL = 3;
    private static final int RESCAN_INTERVAL = 200;

    private static boolean valid(ServerLevel level, BlockPos head) {
        if (head == null || !level.hasChunkAt(head)) return false;
        BlockState state = level.getBlockState(head);
        return state.getBlock() instanceof BedBlock
            && state.hasProperty(BedBlock.PART)
            && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    private static boolean occupied(ServerLevel level, BlockPos head) {
        return level.getBlockState(head).getValue(BedBlock.OCCUPIED);
    }

    private static boolean claimedByOther(PlayerNpcEntity npc, ServerLevel level,
                                          BlockPos head) {
        // No cross-world registry and no scan of all loaded entities.
        return !level.getEntitiesOfClass(PlayerNpcEntity.class,
            new AABB(head).inflate(24.0),
            other -> other != npc && other.isAlive()
                && head.equals(other.assignedBedPos())).isEmpty();
    }

    /** A fixed-size 17x7x17 search around this resident's persisted home. */
    public static boolean reserveNearbyBed(PlayerNpcEntity npc, ServerLevel level) {
        BlockPos origin = npc.homePos();
        if (!level.hasChunkAt(origin)) return false;
        BlockPos best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (int dy = -SEARCH_VERTICAL; dy <= SEARCH_VERTICAL; dy++)
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++)
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    if (dx * dx + dz * dz > SEARCH_RADIUS * SEARCH_RADIUS) continue;
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!valid(level, candidate) || occupied(level, candidate)
                        || claimedByOther(npc, level, candidate)
                        || accessibleSide(level, candidate, npc.blockPosition()) == null) continue;
                    double distance = origin.distSqr(candidate);
                    if (distance < nearest || distance == nearest
                        && (best == null || candidate.asLong() < best.asLong())) {
                        nearest = distance;
                        best = candidate.immutable();
                    }
                }
        if (best == null) return false;
        npc.setAssignedBedPos(best);
        return true;
    }

    /** A deliberate player bed assignment has the same safety rules as AI. */
    public static boolean claimSpecificBed(PlayerNpcEntity npc, ServerLevel level,
                                           BlockPos head) {
        BlockPos home = npc.homePos();
        if (home == null || !level.hasChunkAt(home)
            || Math.abs(head.getY() - home.getY()) > SEARCH_VERTICAL
            || Math.abs(head.getX() - home.getX()) > SEARCH_RADIUS
            || Math.abs(head.getZ() - home.getZ()) > SEARCH_RADIUS
            || !valid(level, head) || occupied(level, head)
            || claimedByOther(npc, level, head)
            || accessibleSide(level, head, npc.blockPosition()) == null)
            return false;
        if (npc.isSleeping()) npc.stopSleeping();
        npc.setAssignedBedPos(head);
        return true;
    }

    public static void forgetBed(PlayerNpcEntity npc) {
        if (npc.isSleeping()) npc.stopSleeping();
        npc.setAssignedBedPos(null);
    }

    private static boolean assignedBedAvailable(PlayerNpcEntity npc,
                                                 ServerLevel level, BlockPos bed) {
        return valid(level, bed) && !occupied(level, bed)
            && !claimedByOther(npc, level, bed);
    }

    /** Only call from the actual server entity tick. */
    public static boolean tick(PlayerNpcEntity npc, ServerLevel level, long tick) {
        boolean wantsSleep = npc.mode() == PlayerNpcEntity.Mode.LIVE
            && RestPolicy.shouldRest(npc.intent(),
                npc.profession() == PlayerNpcEntity.Profession.GUARD, npc.energy())
            && !npc.isAfraid(tick) && !npc.isDetained(tick);
        if (npc.isSleeping()) {
            BlockPos sleeping = npc.getSleepingPos().orElse(null);
            boolean destroyed = !valid(level, sleeping);
            if (!wantsSleep || destroyed) {
                npc.stopSleeping();
                if (destroyed) npc.setAssignedBedPos(null);
                npc.setAction(destroyed ? "bed removed; awake and finding new home"
                    : "woke up from real bed");
                return false;
            }
            npc.getNavigation().stop();
            return true;
        }
        if (!wantsSleep) return false;

        BlockPos bed = npc.assignedBedPos();
        if (bed != null && !assignedBedAvailable(npc, level, bed)) {
            // A removed, occupied or contested old bed is not a permanent claim.
            npc.setAssignedBedPos(null);
            bed = null;
        }
        if (bed == null) {
            // Scan staggered over many NPCs; no global per-tick searches.
            if (!MovementPolicy.due(tick, npc.getId(), RESCAN_INTERVAL))
                return false;
            if (!reserveNearbyBed(npc, level)) return false;
            bed = npc.assignedBedPos();
        }
        if (bed == null) return false;

        if (npc.blockPosition().distSqr(bed) <= 6) {
            // Recheck right before occupation: another player or villager
            // may have taken the bed since this NPC first reserved it.
            if (!assignedBedAvailable(npc, level, bed)) {
                npc.setAssignedBedPos(null);
                return false;
            }
            npc.getNavigation().stop();
            npc.startSleeping(bed); // native lying entity + bed occupancy
            if (npc.isSleeping()) {
                npc.setAction("sleeping in an actual Minecraft bed");
                return true;
            }
            return false;
        }

        // Vanilla A* is bounded by the mob's FOLLOW_RANGE. Requesting a
        // 20-40 block direct route from work to an indoor bed returns an
        // unreachable path, even though roads and doors are connected.
        // Commute using short real pavement waypoints first, then use the
        // exact indoor aisle path once within 10 blocks of the HOME.
        // This does not move the entity except through vanilla navigation.
        if (npc.blockPosition().distSqr(npc.homePos()) > 100) {
            if (MovementPolicy.due(tick, npc.getId(), 40)) {
                if (RoadNavigator.moveToward(npc, level, npc.homePos(), 1.0))
                    npc.setAction("walking home on physical streets before bed");
                else
                    npc.setAction("home commute path temporarily blocked");
            }
            return true;
        }
        // Once close, a bed is an indoor destination, NOT an outdoor road
        // waypoint. Only re-plan at 40-tick staggered intervals: at most
        // four bounded native A* requests per resident, no chunk tickets.
        if (MovementPolicy.due(tick, npc.getId(), 40)) {
            net.minecraft.world.level.pathfinder.Path route =
                reachableBedSide(npc, level, bed);
            if (route != null && npc.getNavigation().moveTo(route, 1.0)) {
                npc.setAction("walking through real doorway to reserved bed");
            } else {
                // A* to an indoor aisle can exceed FOLLOW_RANGE even when
                // Euclidean home distance is small: a resident must walk
                // around the outside wall to the actual open front door.
                BlockPos door = nearestOpenHomeDoor(npc, level);
                boolean moving = false;
                if (door != null && npc.blockPosition().distSqr(door) > 6) {
                    moving = RoadNavigator.moveToward(npc, level, door, 1.0);
                    if (moving)
                        npc.setAction("walking along streets to open home door");
                } else if (door != null) {
                    Direction facing = level.getBlockState(door)
                        .getValue(net.minecraft.world.level.block.DoorBlock.FACING);
                    BlockPos interior = door.relative(facing.getOpposite());
                    if (level.hasChunkAt(interior)
                        && level.getBlockState(interior).isAir()
                        && level.getBlockState(interior.above()).isAir()) {
                        moving = npc.getNavigation().moveTo(interior.getX() + 0.5,
                            interior.getY(), interior.getZ() + 0.5, 1.0);
                        if (moving)
                            npc.setAction("crossing actual open doorway into house");
                    }
                }
                if (!moving) {
                    // Preserve the claim; don't race other sleepers or fake
                    // a bed teleport when a real door/path is blocked.
                    npc.getNavigation().stop();
                    npc.setAction("home doorway or bed aisle unreachable");
                }
            }
        }
        return true; // keep ordinary AI from overwriting a live bed path
    }

    /** Find a nearby physically open vanilla home entrance, not a magic gate. */
    @Nullable private static BlockPos nearestOpenHomeDoor(
            PlayerNpcEntity npc, ServerLevel level) {
        BlockPos home = npc.homePos();
        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 12; dz++) {
                if (dx * dx + dz * dz > 144) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = home.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof
                            net.minecraft.world.level.block.DoorBlock)
                        || !state.hasProperty(
                            net.minecraft.world.level.block.DoorBlock.OPEN)
                        || !state.getValue(
                            net.minecraft.world.level.block.DoorBlock.OPEN)
                        || state.getValue(
                            net.minecraft.world.level.block.DoorBlock.HALF)
                            != net.minecraft.world.level.block.state.properties
                                .DoubleBlockHalf.LOWER) continue;
                    double score = npc.blockPosition().distSqr(pos)
                        + home.distSqr(pos) * 0.2;
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos.immutable();
                    }
                }
            }
        return best;
    }

    /**
     * Select a genuinely path-reachable floor square beside the assigned
     * bed. A geometrically free square behind a solid wall is not a route.
     * Never use teleport or force-load a chunk to complete a sleep task.
     */
    @Nullable private static net.minecraft.world.level.pathfinder.Path reachableBedSide(
            PlayerNpcEntity npc, ServerLevel level, BlockPos bed) {
        net.minecraft.world.level.pathfinder.Path best = null;
        int bestLength = Integer.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = bed.relative(direction);
            if (!level.hasChunkAt(side)
                || !level.getBlockState(side).isAir()
                || !level.getBlockState(side.above()).isAir()
                || !level.getBlockState(side.below()).isSolid()) continue;
            net.minecraft.world.level.pathfinder.Path path =
                npc.getNavigation().createPath(side, 0);
            if (path == null || !path.canReach()) continue;
            if (path.getNodeCount() < bestLength) {
                best = path;
                bestLength = path.getNodeCount();
            }
        }
        return best;
    }

    @Nullable private static BlockPos accessibleSide(ServerLevel level,
                                                     BlockPos bed, BlockPos from) {
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = bed.relative(dir);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.above()).isAir()
                || !level.getBlockState(pos.below()).isSolid()) continue;
            double dist = from.distSqr(pos);
            if (dist < bestDistance) { best = pos; bestDistance = dist; }
        }
        return best;
    }
}
