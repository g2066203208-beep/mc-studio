package dev.reme.worldseam;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DRAWABLE NATIVE-CHUNK VIEW: builds visible surfaces from the true remote
 * ClientLevel chunk data instead of relying solely on sparse preview packets.
 *
 * This is an instrumented first render path. It uses bounded vanilla block
 * model draws; it is not yet a framebuffer/stencil portal or fluid renderer.
 */
public final class SeamNativeGeometryClient {
    private record Cell(int x,int y,int z,BlockState state,int light) {}
    private static final int MAX_COLUMNS = 25;
    private static final int VERTICAL_SECTIONS = 10;
    private static final int MAX_BUILDS_PER_TICK = 2;
    private static final SeamGeometryQueue BUILD_QUEUE = new SeamGeometryQueue();
    private static final SeamRenderSamples DRAW_CPU_SAMPLES = new SeamRenderSamples(120);
    private static final SeamRenderBudget DRAW_BUDGET = new SeamRenderBudget();
    private static volatile long lastFrameDrawCpuNanos;
    private static ClientLevel queuedRemote;
    private static SeamGraph.Link queuedLink;
    private static final int[][] NEIGHBORS = {
        {-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}
    };
    private static final double MAX_DISTANCE_SQ = 192 * 192;
    private static final Map<Long,List<Cell>> columns = new LinkedHashMap<>();
    private static String source = "";
    private static String target = "";
    private static int sourceBoundary;
    private static int targetBoundary;
    private static int blocksBuilt;
    private static int lastFrameDrawn;
    private static int lastFrameEndStone;

    private SeamNativeGeometryClient() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(SeamNativeGeometryClient::render);
        NeoForge.EVENT_BUS.addListener(SeamNativeGeometryClient::tick);
        NeoForge.EVENT_BUS.addListener(
            (ClientPlayerNetworkEvent.LoggingOut e) -> reset()
        );
    }

    public static void onChunk(SeamChunkPayload packet, ClientLevel remote) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null ||
            !mc.level.dimension().location().toString().equals(packet.source()) ||
            !remote.dimension().location().toString().equals(packet.target())) return;

        SeamGraph.Link link = SeamStackClient.from(packet.source()).stream()
            .filter(l -> l.target().equals(packet.target())).findFirst().orElse(null);
        if (link == null) return;
        String scope = packet.source() + ">" + packet.target();
        if (!source.concat(">").concat(target).equals(scope)) reset();
        source = packet.source();
        target = packet.target();
        sourceBoundary = SeamStackClient.boundary(source,link.exit(),
            mc.level.getMinBuildHeight(),mc.level.getMaxBuildHeight());
        targetBoundary = SeamStackClient.boundary(target,link.entry(),
            remote.getMinBuildHeight(),remote.getMaxBuildHeight());

        var cache = remote.getChunkSource();
        if (!cache.hasChunk(packet.chunkX(),packet.chunkZ())) return;
        LevelChunk chunk = cache.getChunk(packet.chunkX(),packet.chunkZ(),false);
        if (chunk == null) return;

        // The old handler rebuilt five full 16x16x160 columns immediately
        // on EACH chunk packet, causing multi-million block scans in bursts.
        // Instead coalesce overlapping columns and rebuild at most two per
        // client tick, prioritizing the player's current chunk.
        queuedRemote=remote;
        queuedLink=link;
        BUILD_QUEUE.invalidate(packet.chunkX(),packet.chunkZ());
    }

    private static void rebuildColumn(ClientLevel remote,SeamGraph.Link link,
                                      int cx,int cz) {
        var cache=remote.getChunkSource();
        if(!cache.hasChunk(cx,cz))return;
        LevelChunk chunk=cache.getChunk(cx,cz,false);
        if(chunk==null)return;
        long packed=((long)cx << 32) | (cz & 0xffffffffL);
        List<Cell> visible=new ArrayList<>(2200);
        int ox=cx<<4,oz=cz<<4;
        boolean descending=link.entry()==SeamGraph.EdgeSide.TOP;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int depth=0;depth<VERTICAL_SECTIONS*16;depth++) {
            int y=descending?targetBoundary-1-depth:targetBoundary+depth;
            if(y<remote.getMinBuildHeight()||y>=remote.getMaxBuildHeight())continue;
            var section=chunk.getSection(chunk.getSectionIndex(y));
            for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                BlockState state=section.getBlockState(x,y&15,z);
                if(state.isAir() || state.getRenderShape()!=RenderShape.MODEL)continue;
                if(isBuried(cache,chunk,x,y,z))continue;
                pos.set(ox+x,y,oz+z);
                int block=remote.getBrightness(LightLayer.BLOCK,pos);
                int sky=remote.getBrightness(LightLayer.SKY,pos);
                visible.add(new Cell(ox+x,sourceBoundary+y-targetBoundary,oz+z,
                    state,LightTexture.pack(block,sky)));
            }
        }
        columns.remove(packed);
        columns.put(packed,List.copyOf(visible));
    }

    /**
     * Native chunk adjacency instead of the old "all 16x16 border voxels
     * are visible" rule which produced solid rectangular End-stone walls.
     * Missing neighbors are conservatively exposed, never synchronously
     * generated on the render thread.
     */
    private static boolean isBuried(
        net.minecraft.client.multiplayer.ClientChunkCache cache,
        LevelChunk chunk,int x,int y,int z) {
        for(int[] d:NEIGHBORS) {
            int nx=x+d[0],ny=y+d[1],nz=z+d[2];
            if(ny<chunk.getMinBuildHeight()||ny>=chunk.getMaxBuildHeight())
                return false;
            int cx=chunk.getPos().x+Math.floorDiv(nx,16);
            int cz=chunk.getPos().z+Math.floorDiv(nz,16);
            LevelChunk neighbor;
            if(cx==chunk.getPos().x && cz==chunk.getPos().z)
                neighbor=chunk;
            else {
                if(!cache.hasChunk(cx,cz))return false;
                neighbor=cache.getChunk(cx,cz,false);
                if(neighbor==null)return false;
            }
            BlockState state=neighbor.getSection(neighbor.getSectionIndex(ny))
                .getBlockState(Math.floorMod(nx,16),ny&15,Math.floorMod(nz,16));
            if(!state.canOcclude())return false;
        }
        return true;
    }


    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(source.isEmpty()) return;
        if(mc.level==null || mc.player==null ||
            !mc.level.dimension().location().toString().equals(source) ||
            !SeamClientWorldPool.hasPrewarmed(target) ||
            queuedRemote==null || queuedLink==null) {
            reset();
            return;
        }
        int cx=Math.floorDiv(mc.player.blockPosition().getX(),16);
        int cz=Math.floorDiv(mc.player.blockPosition().getZ(),16);
        boolean changed=columns.entrySet().removeIf(e -> {
            long p=e.getKey();
            return Math.abs((long)SeamGeometryQueue.x(p)-cx)>2 ||
                   Math.abs((long)SeamGeometryQueue.z(p)-cz)>2;
        });
        var queued=BUILD_QUEUE.drain(MAX_BUILDS_PER_TICK,cx,cz,2);
        for(long packed:queued) {
            rebuildColumn(queuedRemote,queuedLink,
                SeamGeometryQueue.x(packed),SeamGeometryQueue.z(packed));
        }
        if(changed || !queued.isEmpty()) {
            while(columns.size()>MAX_COLUMNS)
                columns.remove(columns.keySet().iterator().next());
            blocksBuilt=columns.values().stream().mapToInt(List::size).sum();
        }
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)
            return;
        lastFrameDrawn=0;
        lastFrameEndStone=0;
        Minecraft mc=Minecraft.getInstance();
        if (mc.level==null || mc.player==null ||
            !mc.level.dimension().location().toString().equals(source) ||
            !SeamClientWorldPool.hasPrewarmed(target)) {
            if (!source.isEmpty()) reset();
            return;
        }
        // A quiet, still-resident remote world must NOT expire after 2400
        // ticks without packets. Cache lifetime follows the actual remote
        // ClientLevel, not an arbitrary traffic timer.
        if (columns.isEmpty() || event.getPoseStack()==null) return;
        // Window pruning and geometry recount already happen in the 20 TPS
        // client tick. Doing another 25-column scan at EVERY render frame
        // (possibly >120 FPS) is redundant.
        long drawStarted=System.nanoTime();
        PoseStack pose=event.getPoseStack();
        Vec3 camera=event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        // Draw the player's closest columns first. A 3x3 native chunk
        // cache can contain >20k candidate surface models; arbitrary packet
        // arrival order must never starve the actual seam under the camera.
        var ordered=new ArrayList<>(columns.entrySet());
        ordered.sort(java.util.Comparator.comparingDouble(entry -> {
            long packed=entry.getKey();
            int cx=(int)(packed >> 32), cz=(int)(long)packed;
            double dx=(cx<<4)+8-camera.x,dz=(cz<<4)+8-camera.z;
            return dx*dx+dz*dz;
        }));
        int frameLimit=DRAW_BUDGET.limit();
        int perColumn=Math.max(16,frameLimit / Math.max(1,ordered.size()));
        outer:
        for(int pass=0;pass<=frameLimit/perColumn;pass++)
            for (var section:ordered) {
                List<Cell> cells=section.getValue();
                int startIndex=pass*perColumn;
                int endIndex=Math.min(cells.size(),startIndex+perColumn);
                for(int at=startIndex;at<endIndex;at++) {
                    Cell c=cells.get(at);
            double dx=c.x()+.5-camera.x,dy=c.y()+.5-camera.y,
                   dz=c.z()+.5-camera.z;
            if (dx*dx+dy*dy+dz*dz>MAX_DISTANCE_SQ) continue;
            if (!event.getFrustum().isVisible(new AABB(
                c.x(),c.y(),c.z(),c.x()+1,c.y()+1,c.z()+1))) continue;
            pose.pushPose();
            pose.translate(c.x()-camera.x,c.y()-camera.y,c.z()-camera.z);
            mc.getBlockRenderer().renderSingleBlock(
                c.state(),pose,buffers,c.light(),OverlayTexture.NO_OVERLAY
            );
            pose.popPose();
            lastFrameDrawn++;
            if (c.state().is(Blocks.END_STONE))lastFrameEndStone++;
            if (lastFrameDrawn>=frameLimit) break outer;
                }
            }
        if (lastFrameDrawn>0) {
            buffers.endBatch();
            lastFrameDrawCpuNanos=Math.max(0,System.nanoTime()-drawStarted);
            DRAW_CPU_SAMPLES.add(lastFrameDrawCpuNanos);
            DRAW_BUDGET.observe(lastFrameDrawn,lastFrameDrawCpuNanos);
        }
    }

    public static int countEndStone() {
        int count=0;
        for(var section:columns.values()) for(Cell cell:section)
            if(cell.state().is(Blocks.END_STONE))count++;
        return count;
    }
    public static int lastFrameEndStone() { return lastFrameEndStone; }
    public static int visibleBlockCount() { return blocksBuilt; }
    public static int lastFrameDrawn() { return lastFrameDrawn; }
    public static int columnCount() { return columns.size(); }
    public static int pendingRebuilds() { return BUILD_QUEUE.pending(); }
    /** Measures client CPU model submission only; never interpreted as GPU frame time. */
    public static double p95ModelCpuMs() { return DRAW_CPU_SAMPLES.p95Ms(); }
    public static int currentModelBudget() { return DRAW_BUDGET.limit(); }
    public static double lastModelCpuMs() { return lastFrameDrawCpuNanos/1_000_000.0; }
    public static String scope() { return source.isEmpty() ? "-" : source+">"+target; }

    public static void reset() {
        columns.clear();
        blocksBuilt=0;
        lastFrameDrawn=0;
        lastFrameEndStone=0;
        source="";
        target="";
        queuedRemote=null;
        queuedLink=null;
        BUILD_QUEUE.clear();
        DRAW_CPU_SAMPLES.clear();
        DRAW_BUDGET.reset();
        lastFrameDrawCpuNanos=0;
    }
}
