package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.pathfinder.Path;

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
        boolean guard = npc.profession() == PlayerNpcEntity.Profession.GUARD;
        boolean wantsSleep = npc.mode() == PlayerNpcEntity.Mode.LIVE
            && npc.intent() == LivingPlanner.Intent.SLEEP
            && (RestPolicy.shouldRest(npc.intent(), guard, npc.energy())
                || RestPolicy.lateWatchRecovery(guard,
                    level.getDayTime() + npc.dailyShiftTicks(), npc.energy()))
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
        if (bed != null) {
            // The persisted bed is owned by this NPC, not an expendable
            // navigation cache. A visiting player sleeping in it, an
            // unloaded chunk, or a blocked aisle must NOT erase its NBT claim.
            if (!level.hasChunkAt(bed)) {
                npc.setAction("assigned home bed chunk unloaded; waiting without forced chunk load");
                return true;
            }
            if (!valid(level, bed) || claimedByOther(npc, level, bed)) {
                npc.setAssignedBedPos(null);
                bed = null;
            } else if (occupied(level, bed)) {
                npc.setAction("own bed temporarily occupied; retain claim and wait");
                return true;
            }
        }
        if (bed == null) {
            // Scan staggered over many NPCs; no global per-tick searches.
            if (!MovementPolicy.due(tick, npc.getId(), RESCAN_INTERVAL))
                return false;
            if (!reserveNearbyBed(npc, level)) return false;
            bed = npc.assignedBedPos();
        }
        if (bed == null) return false;

        // Being 2 blocks away across a WALL is not arriving at a real bed.
        // Select a traversable, loaded-side foot tile first; vanilla walking
        // must actually reach that tile before vanilla startSleeping is used.
        BlockPos nearSide = accessibleSide(level, bed, npc.blockPosition());
        if (nearSide == null) {
            // Obstructed today does not mean the resident has moved house.
            npc.getNavigation().stop();
            npc.setAction("bedside temporarily obstructed; retain saved bed ownership");
            return true;
        }
        // Euclidean distance <=2 admitted the wrong side of a wall or a
        // diagonal tile. The feet must stand ON an actual free orthogonal
        // neighbor of this bed's HEAD, not merely somewhere nearby.
        if (isActualBedside(level, bed, npc.blockPosition())) {
            if (!assignedBedAvailable(npc, level, bed)) {
                npc.setAssignedBedPos(null);
                return false;
            }
            npc.getNavigation().stop();
            npc.startSleeping(bed); // one real Minecraft BedBlock, no scripted teleport
            if (npc.isSleeping()) {
                npc.setAction("sleeping in an actual Minecraft bed after walking to bedside");
                return true;
            }
            // A native bed occupancy or sleeping restriction can fail; retry,
            // NEVER convert the action string into a fake sleeping status.
            npc.setAction("reached bedside but vanilla sleep did not start");
            return true;
        }

        if (npc.getNavigation().isDone()
            || MovementPolicy.due(tick, npc.getId(), 40)) {
            // The shortest Euclidean direction is often through a house wall.
            // Choose the FULL native A* route that REACHES one of four sides.
            Path realRoute = reachableBedSide(npc, level, bed);
            if (realRoute != null && npc.getNavigation().moveTo(realRoute, 1.00)) {
                npc.setAction("walking on fully reachable vanilla A-star route to real bedside");
            } else {
                // While outside, continue towards the apartment door/home
                // instead of dropping a valid bed claim just because the
                // inner-room path is temporarily unavailable.
                // Bed/home coordinates lie INSIDE a real walled house.
                // Routing straight toward that hidden target leaves the NPC
                // pacing at the nearest solid facade. Instead, walk to an
                // actually OPEN, loaded wooden doorway, first OUTSIDE then
                // INSIDE; re-probe the native bedside from the interior.
                // This route uses no new saved fields and never teleports.
                if (HomeEntranceRoute.moveTowardEntrance(npc, level, 0.98)) {
                    // The entrance helper records the physical stage.
                } else {
                    var homeRoute = npc.getNavigation().createPath(npc.homePos(), 0);
                    if (homeRoute != null && homeRoute.canReach()
                        && npc.getNavigation().moveTo(homeRoute, 0.98))
                        npc.setAction("walking through native reachable route home");
                    else if (RoadNavigator.moveToward(npc, level, npc.homePos(), 0.94))
                        npc.setAction("following actual road/door route home before bed");
                    else
                        npc.setAction("real house/bed path blocked; no fake teleport or sleeping");
                }
            }
        }
        return true; // preserve the real navigation through bedtime
    }

    /** Maximum four native A* probes per slow (40-tick) sleep replan.
     * Partial paths which terminate at the OUTSIDE wall are explicitly
     * rejected by Path.canReach(); a failed route never proves arrival.
     */
    @Nullable private static Path reachableBedSide(PlayerNpcEntity npc,
                                                   ServerLevel level, BlockPos bed) {
        Path best = null;
        int nodes = Integer.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = bed.relative(dir);
            if (!isStandingSide(level, side)) continue;
            Path candidate = npc.getNavigation().createPath(side, 0);
            if (candidate != null && candidate.canReach()
                && candidate.getNodeCount() < nodes) {
                nodes = candidate.getNodeCount();
                best = candidate;
            }
        }
        return best;
    }

    /** Package-local deterministic physical arrival gate used by native GameTests. */
    public static boolean isActualBedside(ServerLevel level, BlockPos head, BlockPos feet) {
        if (feet.getY() != head.getY()
            || Math.abs(feet.getX() - head.getX())
                + Math.abs(feet.getZ() - head.getZ()) != 1)
            return false;
        return isStandingSide(level, feet);
    }

    private static boolean isStandingSide(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.hasChunkAt(pos.below())
            && level.getBlockState(pos).isAir()
            && level.getBlockState(pos.above()).isAir()
            && level.getBlockState(pos.below()).isSolid();
    }

    @Nullable private static BlockPos accessibleSide(ServerLevel level,
                                                     BlockPos bed, BlockPos from) {
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = bed.relative(dir);
            if (!isStandingSide(level, pos)) continue;
            double dist = from.distSqr(pos);
            if (dist < bestDistance) { best = pos; bestDistance = dist; }
        }
        return best;
    }
}
