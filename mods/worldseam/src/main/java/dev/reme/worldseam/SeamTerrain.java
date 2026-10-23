package dev.reme.worldseam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.event.level.ChunkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.mojang.logging.LogUtils;

/**
 * Only removes existing BEDROCK in opt-in connected plane bands; respects
 * arbitrary dimension IDs and user-configured seam planes per save.
 * Changing Y connection planes never secretly changes worldgen build height.
 */
public final class SeamTerrain {
    // ChunkEvent.Load is part of chunk publication. A block mutation here can
    // reenter Sable's Rapier voxel rebuild and synchronously await the chunk
    // which is STILL being published. The film CI's JVM dump caught exactly
    // that call chain. Defer all writes to a later, bounded server tick.
    private record Pending(ResourceKey<Level> dimension, int cx, int cz) {}
    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();
    private static final Set<Pending> QUEUED = ConcurrentHashMap.newKeySet();
    private static final int MAX_PENDING = 4096;
    private static final int MAX_POLLS_PER_TICK = 32;
    private static final int MAX_CARVES_PER_TICK = 1;
    private static MinecraftServer activeServer;
    private static long lastFullQueueWarningTick = Long.MIN_VALUE / 2;

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

    public static void onChunkLoad(ChunkEvent.Load event) {
        if(!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level))
            return;
        MinecraftServer server = level.getServer();
        if(server==null || !SeamServer.shouldCarveNewChunk(server)
            || carveBands(level).isEmpty())return;
        Pending job = new Pending(level.dimension(), event.getChunk().getPos().x,
            event.getChunk().getPos().z);
        // Never retain ChunkAccess from a load event: it may not yet be FULL.
        if(QUEUED.contains(job))return;
        if(QUEUED.size()>=MAX_PENDING) {
            long tick=server.getTickCount();
            if(tick-lastFullQueueWarningTick>=200) {
                lastFullQueueWarningTick=tick;
                LogUtils.getLogger().error(
                    "WORLDSEAM_TERRAIN_CARVE_BACKPRESSURE: {} pending chunks; refusing unsafe in-load mutation. Seam not yet cleared at {},{} in {}",
                    QUEUED.size(),job.cx(),job.cz(),job.dimension().location());
            }
            return;
        }
        if(QUEUED.add(job))PENDING.offer(job);
    }

    /** No blocking getChunk: a partially published chunk must never be touched. */
    private static boolean neighborsResident(ServerLevel level,int cx,int cz) {
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)
            if(level.getChunkSource().getChunkNow(cx+dx,cz+dz)==null)
                return false;
        return true;
    }

    /** Called AFTER world ticks; no terrain writes during ChunkEvent.Load. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server=event.getServer();
        if(activeServer!=server) {
            // Preserve jobs queued during initial startup of the first server.
            if(activeServer!=null) {
                PENDING.clear();
                QUEUED.clear();
            }
            activeServer=server;
        }
        if(!SeamServer.shouldCarveNewChunk(server)) {
            PENDING.clear();
            QUEUED.clear();
            return;
        }
        int polls=Math.min(PENDING.size(),MAX_POLLS_PER_TICK);
        int carved=0;
        for(int i=0;i<polls;i++) {
            Pending job=PENDING.poll();
            if(job==null)break;
            ServerLevel level=server.getLevel(job.dimension());
            if(level==null) {
                QUEUED.remove(job);
                continue;
            }
            var chunk=level.getChunkSource().getChunkNow(job.cx(),job.cz());
            if(chunk==null || !neighborsResident(level,job.cx(),job.cz())
                || carved>=MAX_CARVES_PER_TICK) {
                // FAIL CLOSED: no synchronous chunk generation. Retry when the
                // normal chunk scheduler makes all required neighbors resident.
                PENDING.offer(job);
                continue;
            }
            try {
                replaceGeneratedBedrock(level,chunk);
                carved++;
            } finally {
                QUEUED.remove(job);
            }
        }
    }

    public static void replaceGeneratedBedrock(ServerLevel level,ChunkAccess chunk) {
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
