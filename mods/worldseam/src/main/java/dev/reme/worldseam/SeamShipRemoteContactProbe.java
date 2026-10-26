package dev.reme.worldseam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Server-only, native-world broad-phase contact *diagnostic* for one Sable
 * hull's image in the adjacent dimension.
 *
 * This queries REAL loaded block collision VoxelShapes, not fabricated stone
 * grids, only inside the configured logical slice of the second ServerLevel.
 * It NEVER creates/teleports ships and NEVER applies a force or an impulse.
 *
 * A hull AABB is conservative: reporting candidate intersections DOES NOT
 * mean a Sable block collider contacted that terrain. Before physics can be
 * enabled, transform each true body collider into the one Rapier solver and
 * merge both dimensions' contact manifolds with a single rigid-body handle.
 */
public final class SeamShipRemoteContactProbe {
    private SeamShipRemoteContactProbe() {}

    // A single ship must not monopolize the server thread while another mod
    // moves or grows its shape. A truncated probe must NEVER report clear.
    public static final int MAX_SCANNED_BLOCKS=4096;

    public record Result(int scanned,int solidCandidates,int missingChunks,
                         boolean truncated) {
        /** Strictly BROAD PHASE only; this does not prove physical contact. */
        public boolean hasCandidate(){return solidCandidates>0;}
        /** Not safe to interpret zero candidates as an empty world otherwise. */
        public boolean complete(){return !truncated && missingChunks==0;}
    }

    public static Result scan(ServerLevel level,AABB image) {
        if(level==null || image==null ||
           !Double.isFinite(image.minX)||!Double.isFinite(image.minY)||
           !Double.isFinite(image.minZ)||!Double.isFinite(image.maxX)||
           !Double.isFinite(image.maxY)||!Double.isFinite(image.maxZ)||
           image.minX>=image.maxX || image.minY>=image.maxY ||
           image.minZ>=image.maxZ)
            return new Result(0,0,0,true);

        // Never sample unrelated native blocks above the Nether logical Y128
        // roof even though its vanilla storage extends as far as Y256.
        int logicalMin=SeamStackRuntime.bottom(level);
        int logicalMax=SeamStackRuntime.top(level);
        if(image.minY<logicalMin || image.maxY>logicalMax)
            return new Result(0,0,0,true);
        int minX=(int)Math.floor(image.minX);
        int minY=(int)Math.floor(image.minY);
        int minZ=(int)Math.floor(image.minZ);
        int maxX=(int)Math.ceil(image.maxX)-1;
        int maxY=(int)Math.ceil(image.maxY)-1;
        int maxZ=(int)Math.ceil(image.maxZ)-1;

        // Inclusive voxel bounds. Avoid signed overflow in multiplication,
        // huge modded ship hulls and pathological NaN/Infinity AABBs.
        long nx=(long)maxX-minX+1,ny=(long)maxY-minY+1,nz=(long)maxZ-minZ+1;
        if(nx<=0 || ny<=0 || nz<=0 ||
           nx>MAX_SCANNED_BLOCKS || ny>MAX_SCANNED_BLOCKS ||
           nz>MAX_SCANNED_BLOCKS || nx*ny*nz>MAX_SCANNED_BLOCKS)
            return new Result(0,0,0,true);

        int scanned=0,candidates=0,unloaded=0;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);
            if(!level.hasChunk(cx,cz)) {
                unloaded+=(int)ny;
                continue; // NEVER synchronously fetch/generate missing chunks
            }
            for(int y=minY;y<=maxY;y++) {
                pos.set(x,y,z);
                scanned++;
                if(!level.getWorldBorder().isWithinBounds(pos)) {
                    // Cannot claim a physically empty neighbor off-world.
                    return new Result(scanned,candidates,unloaded,true);
                }
                var state=level.getBlockState(pos);
                if(state.isAir())continue;
                VoxelShape collision=state.getCollisionShape(level,pos);
                if(collision.isEmpty())continue;
                for(AABB shape:collision.toAabbs()) {
                    if(shape.move(x,y,z).intersects(image)) {
                        candidates++;
                        break;
                    }
                }
            }
        }
        return new Result(scanned,candidates,unloaded,false);
    }
}
