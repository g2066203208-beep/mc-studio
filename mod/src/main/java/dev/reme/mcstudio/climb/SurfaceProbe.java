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
 * Geometric hit testing: vanilla collision VoxelShapes and Sable sub-level plot shapes.
 * All six face orientations are treated identically; there is no block allow-list.
 */
public final class SurfaceProbe {
    public static final double REACH = 2.75;
    private SurfaceProbe() {}

    @Nullable
    public static GripAnchor trace(Player player) {
        Level level = player.level();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(REACH));
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
        return level.isLoaded(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
