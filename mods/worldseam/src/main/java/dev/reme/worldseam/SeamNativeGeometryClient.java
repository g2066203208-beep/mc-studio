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
    private static final int MAX_DRAW = 3500;
    private static final double MAX_DISTANCE_SQ = 192 * 192;
    private static final Map<Long,List<Cell>> columns = new LinkedHashMap<>();
    private static String source = "";
    private static String target = "";
    private static int sourceBoundary;
    private static int targetBoundary;
    private static int blocksBuilt;
    private static int lastFrameDrawn;
    private static int lastFrameEndStone;
    private static long lastArrivalTick;

    private SeamNativeGeometryClient() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(SeamNativeGeometryClient::render);
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

        // Correct seam visibility requires both sides of a CHUNK boundary.
        // On each arrival update this column plus its four direct neighbors:
        // an old column must stop drawing hidden faces when a neighbor loads.
        rebuildColumn(remote,link,packet.chunkX(),packet.chunkZ());
        rebuildColumn(remote,link,packet.chunkX()-1,packet.chunkZ());
        rebuildColumn(remote,link,packet.chunkX()+1,packet.chunkZ());
        rebuildColumn(remote,link,packet.chunkX(),packet.chunkZ()-1);
        rebuildColumn(remote,link,packet.chunkX(),packet.chunkZ()+1);
        while(columns.size()>MAX_COLUMNS)
            columns.remove(columns.keySet().iterator().next());
        blocksBuilt=columns.values().stream().mapToInt(List::size).sum();
        lastArrivalTick=mc.level.getGameTime();
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
        for(int[] d:new int[][]{
            {-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}
        }) {
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

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)
            return;
        lastFrameDrawn=0;
        lastFrameEndStone=0;
        Minecraft mc=Minecraft.getInstance();
        if (mc.level==null || !mc.level.dimension().location().toString().equals(source)) {
            if (!source.isEmpty()) reset();
            return;
        }
        if (mc.level.getGameTime()-lastArrivalTick>2400) {
            reset();
            return;
        }
        if (columns.isEmpty() || event.getPoseStack()==null) return;
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
        int perColumn=Math.max(64,MAX_DRAW / Math.max(1,ordered.size()));
        outer:
        for(int pass=0;pass<=MAX_DRAW/perColumn;pass++)
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
            if (lastFrameDrawn>=MAX_DRAW) break outer;
                }
            }
        if (lastFrameDrawn>0) buffers.endBatch();
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
    public static String scope() { return source.isEmpty() ? "-" : source+">"+target; }

    public static void reset() {
        columns.clear();
        blocksBuilt=0;
        lastFrameDrawn=0;
        lastFrameEndStone=0;
        source="";
        target="";
        lastArrivalTick=0;
    }
}
