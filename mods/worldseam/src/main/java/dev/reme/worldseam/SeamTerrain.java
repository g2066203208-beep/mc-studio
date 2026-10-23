package dev.reme.worldseam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import java.util.ArrayList;
import java.util.List;

/**
 * Only removes existing BEDROCK in opt-in connected plane bands; respects
 * arbitrary dimension IDs and user-configured seam planes per save.
 * Changing Y connection planes never secretly changes worldgen build height.
 */
public final class SeamTerrain {
    private SeamTerrain() {}

    public static List<SeamBounds.CarveRange> carveBands(ServerLevel level) {
        var bands=new ArrayList<SeamBounds.CarveRange>();
        for(var link:SeamStackRuntime.from(level.getServer(),
            level.dimension().location().toString())) {
            int y=SeamStackRuntime.boundary(level,link.exit());
            int min=level.getMinBuildHeight(), max=level.getMaxBuildHeight();
            int start=link.exit()==SeamGraph.EdgeSide.BOTTOM?y:y-8;
            int end=link.exit()==SeamGraph.EdgeSide.BOTTOM?y+7:y-1;
            start=Math.max(start,min);
            end=Math.min(end,max-1);
            if(start<=end)bands.add(new SeamBounds.CarveRange(start,end));
        }
        return List.copyOf(bands);
    }

    /**
     * Explicit, destructive one-column test for every active stack dimension.
     * Only BEDROCK is cleared; normal stone/netherrack can still obstruct
     * the camera until a genuine global seam renderer is complete.
     */
    public static int openTestColumn(MinecraftServer server,int x,int z) {
        int changed=0;
        for(SeamStack.Entry entry:SeamSettings.get(server).stack().entries()){
            ServerLevel world=server.getLevel(SeamServer.dimensionKey(entry.id()));
            if(world==null)continue;
            int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);
            world.getChunk(cx,cz);
            BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
            int ox=cx<<4,oz=cz<<4;
            for(var band:carveBands(world))
                for(int y=band.minY();y<=band.maxY();y++)
                    for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){
                        pos.set(ox+dx,y,oz+dz);
                        if(world.getBlockState(pos).is(Blocks.BEDROCK)
                           && world.setBlock(pos,Blocks.AIR.defaultBlockState(),3))
                            changed++;
                    }
        }
        return changed;
    }

    /**
     * Called only by SeamChunkGenerationMixin during the genuine worldgen
     * stage, BEFORE the fully loaded LevelChunk is published to NeoForge and
     * Sable/Rapier. Never carve a published LevelChunk in its Load callback:
     * its setBlockState fires Sable's physics block-change hook which may
     * recursively load chunks while a chunk FULL task is waiting (the real
     * 2026-10-23 integrated server JVM stack proved this hang).
     *
     * If the generation stage changes or the chunk is already full, fail
     * closed: the explicit test-world /fill remains available instead.
     */
    public static void replaceGeneratedBedrock(ServerLevel level,ChunkAccess chunk) {
        if(!(chunk instanceof ProtoChunk))return;
        MinecraftServer server=level.getServer();
        if(server==null || !SeamServer.shouldCarveNewChunk(server))return;
        List<SeamBounds.CarveRange> bands=carveBands(level);
        if(bands.isEmpty())return;
        int ox=chunk.getPos().getMinBlockX(),oz=chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(var band:bands)for(int y=band.minY();y<=band.maxY();y++){
            if(y<chunk.getMinBuildHeight() || y>=chunk.getMaxBuildHeight())continue;
            for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){
                pos.set(ox+dx,y,oz+dz);
                if(chunk.getBlockState(pos).is(Blocks.BEDROCK))
                    chunk.setBlockState(pos,Blocks.AIR.defaultBlockState(),false);
            }
        }
    }
}
