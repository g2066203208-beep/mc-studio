package dev.reme.mcstudio.climb;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Geometric hit testing: vanilla outline VoxelShapes and Sable sub-level plot shapes.
 * All six face orientations are treated identically; there is no block allow-list.
 */
public final class SurfaceProbe {
    public static final double REACH = 2.75;
    private SurfaceProbe() {}

    @Nullable
    public static GripAnchor trace(Player player) {
        return castFrom(player, player.getEyePosition(), player.getLookAngle());
    }

    /** Probe a predicted shoulder position toward the current climbing face.
     *  Static and Sable-local contacts are both supported by the same cast. */
    @Nullable
    public static GripAnchor traceOffset(Player player, Vec3 offset, Vec3 surfaceNormal) {
        if (offset.lengthSqr() < 0.0001 || surfaceNormal.lengthSqr() < 0.0001) return null;
        return castFrom(player, player.getEyePosition().add(offset), surfaceNormal.normalize().scale(-1));
    }

    /**
     * Actively explore new physical faces at walls, outside corners and
     * overhead slabs. A single ray back into the old wall could never find
     * the ceiling's downward face, which caused the missing animation switch.
     * Rays share the same Sable-local hit testing as ordinary wall probes.
     */
    /** Check the two in-face coordinates in the BLOCK'S OWN local axes.
     * This works even on a rotated Sable sub-level; world XYZ edges do not.
     * Only near a face edge should the expensive cross-face fan compete
     * with a valid existing roof/wall hold.
     */
    public static boolean nearFaceEdge(GripAnchor anchor) {
        Vec3 p = anchor.localPoint().subtract(
                anchor.block().getX(), anchor.block().getY(), anchor.block().getZ());
        Vec3 n = anchor.localNormal();
        double border = .24;
        boolean x = p.x < border || p.x > 1-border;
        boolean y = p.y < border || p.y > 1-border;
        boolean z = p.z < border || p.z > 1-border;
        if (Math.abs(n.x) > .72) return y || z;
        if (Math.abs(n.y) > .72) return x || z;
        return x || y;
    }

    @Nullable
    public static GripAnchor traceTransition(Player player, GripAnchor.Contact old, Vec3 movement) {
        if (movement.lengthSqr() < .001) return null;
        Vec3 dir = movement.normalize();
        Vec3 n = old.normal().normalize();
        Vec3 eye = player.getEyePosition();
        Vec3 predicted = old.point().add(dir.scale(.78));
        // Fan out from the camera AND the free space near the *old physical hit*.
        // On a ceiling edge, scanning only at eye height skips the next wall;
        // on a wall edge, the old-normal cast may never see the ceiling's underside.
        Vec3[] origins = {
                eye, eye.add(dir.scale(.36)), eye.add(dir.scale(.72)),
                old.point().add(n.scale(.32)).add(dir.scale(.24)),
                old.point().add(n.scale(.48)).add(dir.scale(.72)),
                eye.add(0,.38,0), eye.add(0,-.30,0)
        };
        Vec3[] rays = {
                n.scale(-1), dir,
                new Vec3(Math.signum(dir.x),0,0),
                new Vec3(0, 1, 0), new Vec3(0,-1,0),
                new Vec3(0,0,Math.signum(dir.z)),
                dir.subtract(n.scale(dir.dot(n))).add(n.scale(-.55))
        };
        GripAnchor best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Vec3 origin : origins) {
            for (Vec3 ray : rays) {
                if (ray.lengthSqr() < .01) continue;
                GripAnchor candidate = castFrom(player, origin, ray);
                if (candidate == null) continue;
                GripAnchor.Contact c = candidate.resolve(player.level());
                if (c == null || !ClimbSurfaceMath.canTurn(n,c.normal(),dir)) continue;
                double fromOld = c.point().distanceTo(old.point());
                if (fromOld > 1.74 || player.getEyePosition().distanceTo(c.point()) > REACH + .12) continue;
                double score = c.point().distanceToSqr(predicted) + origin.distanceToSqr(c.point())*.12;
                if (score < bestScore) { bestScore = score; best = candidate; }
            }
        }
        return best;
    }

    @Nullable
    private static GripAnchor castFrom(Player player, Vec3 start, Vec3 direction) {
        Level level = player.level();
        Vec3 end = start.add(direction.normalize().scale(REACH));
        BlockHitResult vanilla = cast(level, player, start, end);
        GripAnchor best = null;
        double bestDistance = REACH * REACH + 0.0001;
        if (valid(level, vanilla)) {
            bestDistance = start.distanceToSqr(vanilla.getLocation());
            best = new GripAnchor(null, vanilla.getBlockPos(), vanilla.getLocation(),
                    Vec3.atLowerCornerOf(vanilla.getDirection().getNormal()));
        }

        // Sable stores blocks at very distant plot coordinates; world raycasts alone miss them.
        AABB worldRayBounds = new AABB(start, end).inflate(0.2);
        for (SubLevelAccess sub : SableCompanion.INSTANCE.getAllIntersecting(level, new BoundingBox3d(worldRayBounds))) {
            Vec3 localStart = sub.logicalPose().transformPositionInverse(start);
            Vec3 localEnd = sub.logicalPose().transformPositionInverse(end);
            BlockHitResult hit = cast(level, player, localStart, localEnd);
            if (!valid(level, hit)) continue;
            UUID structureId = sub.getUniqueId();
            if (!structureId.equals(SableCompanion.INSTANCE.getContaining(level, hit.getBlockPos()) == null
                    ? null : SableCompanion.INSTANCE.getContaining(level, hit.getBlockPos()).getUniqueId())) continue;
            Vec3 worldPoint = sub.logicalPose().transformPosition(hit.getLocation());
            double distance = start.distanceToSqr(worldPoint);
            if (distance < bestDistance && distance <= REACH * REACH) {
                bestDistance = distance;
                best = new GripAnchor(structureId, hit.getBlockPos(), hit.getLocation(),
                        Vec3.atLowerCornerOf(hit.getDirection().getNormal()));
            }
        }
        return best;
    }

    private static BlockHitResult cast(Level level, Player player, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    private static boolean valid(Level level, BlockHitResult hit) {
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        return level.isLoaded(pos) && !level.getBlockState(pos).isAir()
                && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
