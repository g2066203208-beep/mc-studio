package dev.reme.worldseam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.HashSet;
import dev.reme.worldseam.SeamGpuSectionRenderer.Cell;
import dev.reme.worldseam.SeamGpuSectionRenderer.Key;
import dev.reme.worldseam.SeamGpuSectionRenderer.Section;
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
    /** Immutable native 16^3 segment; clip ONCE before drawing its models. */
    private record Slice(int sectionY,List<Cell> cells,AABB bounds,Section gpuSection) {}
    private static final Logger LOG=LogUtils.getLogger();
    private static final SeamGpuSectionRenderer GPU=new SeamGpuSectionRenderer();
    private static boolean gpuDisabled=Boolean.getBoolean("worldseam.legacyBlockPreview");
    private static volatile boolean gpuReloadPending;
    private static int lastFrameGpuModels;
    private static int lastFrameGpuSections;
    private static long lastFrameGpuCpuNanos;
    private static final double NEAR_PLANE_RENDER_RANGE = 80.0;
    private static int lastVisibleSlices;
    private static int lastCulledSlices;
    private static final int MAX_COLUMNS = 25;
    private static final int VERTICAL_SECTIONS = 10;
    // 4 native 16³ slices per client tick, replacing 2 whole 160-block
    // columns (~10x larger worst-case scan) per tick.
    private static final int MAX_SECTION_BUILDS_PER_TICK = 4;
    private static final SeamSectionWorkQueue BUILD_QUEUE=new SeamSectionWorkQueue();
    private static int lastTickRebuildSlices;
    private static final SeamRenderSamples DRAW_CPU_SAMPLES = new SeamRenderSamples(120);
    private static final SeamRenderSamples REBUILD_CPU_SAMPLES = new SeamRenderSamples(120);
    private static final SeamRenderBudget DRAW_BUDGET = new SeamRenderBudget();
    private static final SeamRenderBuffers OWNED_BUFFERS = new SeamRenderBuffers();
    private static volatile long lastFrameDrawCpuNanos;
    private static ClientLevel queuedRemote;
    private static SeamGraph.Link queuedLink;
    private static final int[][] NEIGHBORS = {
        {-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}
    };
    private static final double MAX_DISTANCE_SQ = 192 * 192;
    private static final Map<Long,List<Slice>> columns = new LinkedHashMap<>();
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

        // Deduplicate all touched 16³ sections; a later packet supersedes
        // the queued work for the same section. Neighbor columns invalidate
        // edge faces, but no full-column scans happen on packet arrival.
        queuedRemote=remote;
        queuedLink=link;
        int low=link.entry()==SeamGraph.EdgeSide.TOP
            ? targetBoundary-VERTICAL_SECTIONS*16 : targetBoundary;
        int high=low+VERTICAL_SECTIONS*16;
        BUILD_QUEUE.invalidateWithNeighbors(
            packet.chunkX(),packet.chunkZ(),low,high);
    }

    /**
     * Rebuild EXACTLY ONE native 16³ section, or remove an absent/air slice.
     * Cached other sections remain drawable; no unfinished full-column
     * temporary model is exposed between client ticks.
     */
    private static void rebuildSection(ClientLevel remote,SeamGraph.Link link,
                                      int cx,int cz,int sy) {
        long packed=SeamGeometryQueue.pack(cx,cz);
        var cache=remote.getChunkSource();
        if(!cache.hasChunk(cx,cz)) {
            publishSection(packed,sy,null);
            return;
        }
        LevelChunk chunk=cache.getChunk(cx,cz,false);
        if(chunk==null) {
            publishSection(packed,sy,null);
            return;
        }
        boolean descending=link.entry()==SeamGraph.EdgeSide.TOP;
        int bandLow=descending?targetBoundary-VERTICAL_SECTIONS*16:targetBoundary;
        int bandHigh=bandLow+VERTICAL_SECTIONS*16;
        int begin=Math.max(Math.max(sy*16,bandLow),remote.getMinBuildHeight());
        int end=Math.min(Math.min(sy*16+16,bandHigh),remote.getMaxBuildHeight());
        if(begin>=end) {
            publishSection(packed,sy,null);
            return;
        }
        var section=chunk.getSection(chunk.getSectionIndex(begin));
        if(section.hasOnlyAir()) {
            publishSection(packed,sy,null);
            return;
        }
        int ox=cx<<4,oz=cz<<4;
        var cells=new ArrayList<Cell>(512);
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int y=begin;y<end;y++) {
            for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                BlockState state=section.getBlockState(x,y&15,z);
                if(state.isAir()||state.getRenderShape()!=RenderShape.MODEL)continue;
                if(isBuried(cache,chunk,x,y,z))continue;
                pos.set(ox+x,y,oz+z);
                int block=remote.getBrightness(LightLayer.BLOCK,pos);
                int sky=remote.getBrightness(LightLayer.SKY,pos);
                cells.add(new Cell(ox+x,
                    SeamSectionLayout.mappedBlockY(y,sourceBoundary,targetBoundary),
                    oz+z,state,LightTexture.pack(block,sky)));
            }
        }
        Slice replacement=null;
        if(!cells.isEmpty()) {
            int firstY=SeamSectionLayout.mappedSectionMinY(
                sy*16,sourceBoundary,targetBoundary);
            var sectionCells=List.copyOf(cells);
            var bounds=new AABB(ox,firstY,oz,ox+16,firstY+16,oz+16);
            var gpuSection=new Section(new Key(packed,sy),ox,firstY,oz,
                bounds,sectionCells);
            replacement=new Slice(sy,sectionCells,bounds,gpuSection);
        }
        publishSection(packed,sy,replacement);
    }

    /** Atomic replacement of one completed slice; keep all other slices. */
    private static void publishSection(long key,int sy,Slice replacement) {
        var old=columns.get(key);
        var next=new ArrayList<Slice>(old==null?1:old.size()+1);
        if(old!=null) {
            for(var prior:old) {
                if(prior.sectionY()==sy)
                    blocksBuilt-=prior.cells().size();
                else next.add(prior);
            }
        }
        if(replacement!=null) {
            next.add(replacement);
            blocksBuilt+=replacement.cells().size();
            GPU.changed(replacement.gpuSection());
        } else {
            GPU.removed(new Key(key,sy));
        }
        if(next.isEmpty())columns.remove(key);
        else columns.put(key,List.copyOf(next));
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


    /** Resource packs change baked block UVs and RenderTypes. Reload GPU meshes
     *  on the real Minecraft client thread; never destroy VBOs on a worker. */
    public static void invalidateGpuOnReload() {
        gpuReloadPending=true;
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(gpuReloadPending) {
            gpuReloadPending=false;
            GPU.release();
            for(var col:columns.values())
                for(var slice:col)GPU.changed(slice.gpuSection());
        }
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
        int nearestSy=Math.floorDiv(
            targetBoundary-(queuedLink.entry()==SeamGraph.EdgeSide.TOP?1:0),16);
        var queued=BUILD_QUEUE.drain(MAX_SECTION_BUILDS_PER_TICK,
            cx,cz,2,nearestSy);
        lastTickRebuildSlices=queued.size();
        if(!queued.isEmpty()) {
            long started=System.nanoTime();
            for(var job:queued)
                rebuildSection(queuedRemote,queuedLink,
                    job.chunkX(),job.chunkZ(),job.sectionY());
            REBUILD_CPU_SAMPLES.add(Math.max(0,System.nanoTime()-started));
        }

        if(changed) {
            var alive=new HashSet<Key>();
            for(var entry:columns.entrySet())
                for(var slice:entry.getValue())
                    alive.add(new Key(entry.getKey(),slice.sectionY()));
            GPU.trimTo(alive);
            // Pruned columns were outside the moving 5x5 visible window.
            // Per-section replacements maintain totals in O(1); only
            // moving a whole window requires a bounded recount.
            int count=0;
            for(var group:columns.values())for(var slice:group)
                count+=slice.cells().size();
            blocksBuilt=count;
        }
        while(columns.size()>MAX_COLUMNS) {
            long dropped=columns.keySet().iterator().next();
            for(var slice:columns.get(dropped))
                GPU.removed(new Key(dropped,slice.sectionY()));
            columns.remove(dropped);
            int count=0;
            for(var group:columns.values())for(var slice:group)
                count+=slice.cells().size();
            blocksBuilt=count;
        }
        // PREBUILD persistent GPU meshes during the bounded 20TPS client
        // update, rather than tying completion rate to an extremely slow
        // software-OpenGL render FPS. RenderLevelStage only draws meshes
        // already uploaded, while the fallback fills the remaining holes.
        // We are on the Minecraft client/render thread here: no GL worker
        // calls, no vanilla RenderBuffers or active-world state mutation.
        if(!gpuDisabled && !columns.isEmpty() &&
            SeamSectionLayout.nearPlane(mc.player.getY(),
                sourceBoundary,NEAR_PLANE_RENDER_RANGE)) {
            var pendingVisible=new ArrayList<Section>();
            for(var col:columns.values())
                for(var slice:col)
                    pendingVisible.add(slice.gpuSection());
            try {
                GPU.compileVisible(mc,pendingVisible,mc.player.position());
            } catch(RuntimeException | Error ex) {
                LOG.error("WorldSeam GPU tick pre-upload failed; falling back to safe section preview",ex);
                gpuDisabled=true;
                GPU.release();
            }
        }
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)
            return;
        lastFrameDrawn=0;
        lastFrameEndStone=0;
        lastFrameGpuModels=0;
        lastFrameGpuSections=0;
        lastFrameGpuCpuNanos=0;
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
        // Only the active seam's physically nearby view deserves GPU work.
        // Chunk prewarming continues independently for immediate handoff.
        if(!SeamSectionLayout.nearPlane(mc.player.getY(),
                sourceBoundary,NEAR_PLANE_RENDER_RANGE)) return;
        long drawStarted=System.nanoTime();
        PoseStack pose=event.getPoseStack();
        Vec3 camera=event.getCamera().getPosition();
        // NEVER use Minecraft's shared entity/block-entity BufferSource here.
        // A remote-frame endBatch() could flush foreign pending shader draws.

        // Broad-phase CPU culling using immutable per-16^3 bounds, not a NEW
        // AABB allocation + frustum test for EACH of thousands of cells.
        // A slice AABB encloses every mapped block for custom world heights.
        var visible=new ArrayList<Slice>();
        lastCulledSlices=0;
        for(var col:columns.values())for(var slice:col) {
            if(event.getFrustum().isVisible(slice.bounds()))
                visible.add(slice);
            else lastCulledSlices++;
        }
        lastVisibleSlices=visible.size();
        if(visible.isEmpty())return;
        if(!gpuDisabled) {
            try {
                long gpuStart=System.nanoTime();
                var sections=new ArrayList<Section>(visible.size());
                for(var slice:visible)sections.add(slice.gpuSection());
                GPU.compileVisible(mc,sections,camera);
                GPU.draw(sections,pose,camera);
                lastFrameGpuModels=GPU.lastFrameModelsVisible();
                lastFrameGpuSections=GPU.lastFrameCachedSections();
                lastFrameEndStone=GPU.lastFrameEndStoneModels();
                lastFrameGpuCpuNanos=Math.max(0,System.nanoTime()-gpuStart);
                // All cached sections stay visible with NO per-frame model
                // selection cap. Only sections still uploading need the
                // legacy fallback. This fixes the previous "floating sparse
                // blocks" visual defect without a 50ms model budget.
                visible.removeIf(slice->GPU.ready(new Key(
                    SeamGeometryQueue.pack(
                        Math.floorDiv((int)slice.bounds().minX,16),
                        Math.floorDiv((int)slice.bounds().minZ,16)),
                    slice.sectionY())));
                lastFrameDrawn=lastFrameGpuModels;
                if(visible.isEmpty()) {
                    lastFrameDrawCpuNanos=lastFrameGpuCpuNanos;
                    DRAW_CPU_SAMPLES.add(lastFrameGpuCpuNanos);
                    return;
                }
            } catch (RuntimeException | Error failure) {
                LOG.error("WorldSeam GPU section pipeline failed; falling back to safe legacy model drawing",failure);
                gpuDisabled=true;
                GPU.release();
            }
        }
        MultiBufferSource.BufferSource buffers=OWNED_BUFFERS.source();

        // Prioritize true camera-proximal slices while spreading the CPU
        // budget across adjacent visible sections; packet arrival order
        // must not permanently starve the important central seam.
        visible.sort(java.util.Comparator.comparingDouble(slice -> {
            AABB box=slice.bounds();
            double dx=(box.minX+box.maxX)*0.5-camera.x;
            double dy=(box.minY+box.maxY)*0.5-camera.y;
            double dz=(box.minZ+box.maxZ)*0.5-camera.z;
            return dx*dx+dy*dy+dz*dz;
        }));
        int frameLimit=DRAW_BUDGET.limit();
        // The GPU path already drew all cached section models this frame.
        // Keep the fallback bounded ONLY for not-yet-uploaded sections.
        int drawnFallback=0;
        int perSlice=Math.max(8,frameLimit/Math.max(1,visible.size()));
        int longest=0;
        for(var slice:visible)longest=Math.max(longest,slice.cells().size());
        try {
        outer:
        for(int pass=0;pass*perSlice<longest;pass++) {
            int startIndex=pass*perSlice;
            for(var slice:visible) {
                List<Cell> cells=slice.cells();
                int endIndex=Math.min(cells.size(),startIndex+perSlice);
                for(int at=startIndex;at<endIndex;at++) {
                    Cell c=cells.get(at);
                    double dx=c.x()+.5-camera.x;
                    double dy=c.y()+.5-camera.y;
                    double dz=c.z()+.5-camera.z;
                    if(dx*dx+dy*dy+dz*dz>MAX_DISTANCE_SQ)continue;
                    pose.pushPose();
                    try {
                        pose.translate(c.x()-camera.x,c.y()-camera.y,c.z()-camera.z);
                        mc.getBlockRenderer().renderSingleBlock(
                            c.state(),pose,buffers,c.light(),OverlayTexture.NO_OVERLAY
                        );
                    } finally {
                        pose.popPose();
                    }
                    lastFrameDrawn++;
                    drawnFallback++;
                    if(c.state().is(Blocks.END_STONE))lastFrameEndStone++;
                    if(drawnFallback>=frameLimit)break outer;
                }
            }
        }
        } finally {
            // Always submit ONLY WorldSeam's own buffers, including if a
            // third-party baked model throws during a draw. Do not touch
            // mc.renderBuffers() and never leave queued partial geometry.
            OWNED_BUFFERS.flush();
        }
        if (lastFrameDrawn>0) {
            lastFrameDrawCpuNanos=Math.max(0,System.nanoTime()-drawStarted);
            DRAW_CPU_SAMPLES.add(lastFrameDrawCpuNanos);
            DRAW_BUDGET.observe(drawnFallback,
                Math.max(0,lastFrameDrawCpuNanos-lastFrameGpuCpuNanos));
        }
    }

    public static int countEndStone() {
        int count=0;
        for(var col:columns.values())for(var slice:col)
            for(Cell cell:slice.cells())
                if(cell.state().is(Blocks.END_STONE))count++;
        return count;
    }
    public static int lastFrameEndStone() { return lastFrameEndStone; }
    public static int visibleBlockCount() { return blocksBuilt; }
    public static int lastFrameDrawn() { return lastFrameDrawn; }
    public static int columnCount() { return columns.size(); }
    public static int visibleSlices() { return lastVisibleSlices; }
    public static int culledSlices() { return lastCulledSlices; }
    public static int pendingRebuilds() { return BUILD_QUEUE.size(); }
    public static int gpuCachedSections() { return GPU.cachedCount(); }
    public static int gpuPendingSections() { return GPU.pendingCount(); }
    public static int lastFrameGpuModels() { return lastFrameGpuModels; }
    public static int lastFrameGpuSections() { return lastFrameGpuSections; }
    public static double lastGpuSceneCoverage() {
        return lastVisibleSlices==0?0.0:
            (double)lastFrameGpuSections/lastVisibleSlices;
    }
    public static long gpuDrawCalls() { return GPU.drawCalls(); }
    public static long gpuUploads() { return GPU.uploads(); }
    public static boolean gpuBackendDisabled() { return gpuDisabled; }
    public static double lastFrameGpuCpuMs() {return lastFrameGpuCpuNanos/1_000_000.0;}
    public static int lastTickRebuildSlices() { return lastTickRebuildSlices; }
    /** All remote fallback paths use WorldSeam-owned GPU buffers only. */
    public static MultiBufferSource.BufferSource fallbackBuffers() {
        return OWNED_BUFFERS.source();
    }
    public static void flushFallbackBuffers() { OWNED_BUFFERS.flush(); }
    public static boolean ownBuffersActive() { return OWNED_BUFFERS.active(); }
    public static long bufferCreations() { return OWNED_BUFFERS.creations(); }
    public static long bufferFlushes() { return OWNED_BUFFERS.flushes(); }
    public static long bufferDisposals() { return OWNED_BUFFERS.disposals(); }
    public static double p95RebuildCpuMs() { return REBUILD_CPU_SAMPLES.p95Ms(); }
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
        lastVisibleSlices=0;
        lastCulledSlices=0;
        source="";
        target="";
        queuedRemote=null;
        queuedLink=null;
        BUILD_QUEUE.clear();
        GPU.release();
        gpuReloadPending=false;
        lastFrameGpuModels=0;
        lastFrameGpuSections=0;
        lastFrameGpuCpuNanos=0;
        lastTickRebuildSlices=0;
        DRAW_CPU_SAMPLES.clear();
        REBUILD_CPU_SAMPLES.clear();
        OWNED_BUFFERS.release();
        DRAW_BUDGET.reset();
        lastFrameDrawCpuNanos=0;
    }
}
