package dev.reme.worldseam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Do not materialize a player or animal inside old roof/floor bedrock. */
public final class SeamArrival {
    private SeamArrival() {}


    /**
     * The destination foot may be inside one world while the head, hull or
     * wings remain in the other. Check ONE body's actual envelope in both
     * worlds, clipped exactly at the logical contact plane.
     *
     * This is a real native Minecraft voxel collision query, not a fake
     * "passable" flag, not copied blocks, and not a forced chunk load.
     * No claim is made that this is Sable's multi-world Rapier solver.
     */
    public static boolean hasJoinedClearance(ServerLevel source,ServerLevel target,
                                             SeamGraph.Link link,double sourceBoundary,
                                             double targetBoundary,double x,double y,
                                             double z,Entity entity) {
        double half=entity.getBbWidth()*0.5;
        double tall=entity.getBbHeight();
        if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) ||
            !(half>0.0) || !(tall>0.0) || half>32 || tall>64)
            return false;
        var body=new net.minecraft.world.phys.AABB(x-half,y,z-half,
            x+half,y+tall,z+half);
        SeamBodySplit.Split split=SeamBodySplit.atTargetBoundary(
            body,link.entry(),targetBoundary,sourceBoundary);
        return clearSection(target,split.targetPart(),
                SeamStackRuntime.bottom(target),SeamStackRuntime.top(target)) &&
            clearSection(source,split.sourcePart(),
                SeamStackRuntime.bottom(source),SeamStackRuntime.top(source));
    }

    private static boolean clearSection(ServerLevel level,
                                        net.minecraft.world.phys.AABB body,
                                        int logicalMin,int logicalMax) {
        if(body==null)return true;
        if(body.minY<logicalMin || body.maxY>logicalMax)return false;
        int minCx=Math.floorDiv((int)Math.floor(body.minX),16);
        int maxCx=Math.floorDiv((int)Math.ceil(body.maxX)-1,16);
        int minCz=Math.floorDiv((int)Math.floor(body.minZ),16);
        int maxCz=Math.floorDiv((int)Math.ceil(body.maxZ)-1,16);
        if(maxCx-minCx>4 || maxCz-minCz>4)return false;
        for(int cx=minCx;cx<=maxCx;cx++)
            for(int cz=minCz;cz<=maxCz;cz++)
                if(!level.hasChunk(cx,cz))return false;
        // Query Minecraft's actual native VoxelShape solver. The null entity
        // prevents the source-world player from colliding with themself.
        return level.noCollision((Entity)null,body);
    }

    public static boolean hasClearance(ServerLevel level, BlockPos feet, Entity entity) {
        int height = Math.max(1, (int) Math.ceil(entity.getBbHeight()));
        for (int i = 0; i < height; i++) {
            BlockPos pos = feet.above(i);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
                return false;
        }
        return true;
    }
}
