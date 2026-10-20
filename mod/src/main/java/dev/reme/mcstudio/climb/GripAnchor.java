package dev.reme.mcstudio.climb;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A contact is stored in the coordinate space of the block it belongs to.
 * For a Sable sub-level, "localPoint" and "localNormal" are plot coordinates.
 */
public record GripAnchor(@Nullable UUID structureId, BlockPos block, Vec3 localPoint, Vec3 localNormal) {
    public boolean dynamic() {
        return structureId != null;
    }

    @Nullable
    public Contact resolve(Level level) {
        if (!level.isLoaded(block)) return null;
        VoxelShape shape = level.getBlockState(block).getCollisionShape(level, block);
        if (shape.isEmpty()) return null;
        if (!dynamic()) {
            // Do not retain an anchor after a surface is removed/replaced with a disjoint shape.
            if (!withinShapeBounds(shape)) return null;
            return new Contact(localPoint, localNormal);
        }
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, block);
        if (sub == null || !structureId.equals(sub.getUniqueId()) || !withinShapeBounds(shape)) return null;
        return new Contact(sub.logicalPose().transformPosition(localPoint),
                sub.logicalPose().transformNormal(localNormal).normalize());
    }

    private boolean withinShapeBounds(VoxelShape shape) {
        Vec3 p = localPoint.subtract(block.getX(), block.getY(), block.getZ());
        // Allow a small tolerance for contact exactly on any of the six face planes.
        return p.x >= shape.min(net.minecraft.core.Direction.Axis.X) - 0.03
                && p.x <= shape.max(net.minecraft.core.Direction.Axis.X) + 0.03
                && p.y >= shape.min(net.minecraft.core.Direction.Axis.Y) - 0.03
                && p.y <= shape.max(net.minecraft.core.Direction.Axis.Y) + 0.03
                && p.z >= shape.min(net.minecraft.core.Direction.Axis.Z) - 0.03
                && p.z <= shape.max(net.minecraft.core.Direction.Axis.Z) + 0.03;
    }

    public record Contact(Vec3 point, Vec3 normal) {}
}
