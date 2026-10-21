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
    private static final int MAX_COLUMNS = 9;
    private static final int VERTICAL_SECTIONS = 7;
    private static final int MAX_DRAW = 3500;
    private static final double MAX_DISTANCE_SQ = 144 * 144;
    private static final Map<Long,List<Cell>> columns = new LinkedHashMap<>();
    private static String source = "";
    private static String target = "";
    private static int sourceBoundary;
    private static int targetBoundary;
    private static int blocksBuilt;
    private static int lastFrameDrawn;
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

        SeamGraph.Link link = SeamGraph.from(packet.source()).stream()
            .filter(l -> l.target().equals(packet.target())).findFirst().orElse(null);
        if (link == null) return;
        String scope = packet.source() + ">" + packet.target();
        if (!source.concat(">").concat(target).equals(scope)) reset();
        source = packet.source();
        target = packet.target();
        sourceBoundary = (int) SeamGraph.boundaryY(link.exit(),
            mc.level.getMinBuildHeight(),
            SeamBounds.top(source,mc.level.getMaxBuildHeight()));
        targetBoundary = (int) SeamGraph.boundaryY(link.entry(),
            remote.getMinBuildHeight(),
            SeamBounds.top(target,remote.getMaxBuildHeight()));

        var cache = remote.getChunkSource();
        if (!cache.hasChunk(packet.chunkX(),packet.chunkZ())) return;
        LevelChunk chunk = cache.getChunk(packet.chunkX(),packet.chunkZ(),false);
        if (chunk == null) return;

        long packed = ((long)packet.chunkX() << 32) | (packet.chunkZ() & 0xffffffffL);
        List<Cell> visible = new ArrayList<>(2500);
        int originX = packet.chunkX() << 4;
        int originZ = packet.chunkZ() << 4;
        boolean fromTop = link.entry() == SeamGraph.EdgeSide.TOP;
        int yStart = fromTop ? targetBoundary - VERTICAL_SECTIONS * 16 : targetBoundary;
        int yEnd = fromTop ? targetBoundary : targetBoundary + VERTICAL_SECTIONS * 16;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y=yStart;y<yEnd;y++) {
            if (y<remote.getMinBuildHeight() || y>=remote.getMaxBuildHeight()) continue;
            int localY = y & 15;
            var section = chunk.getSection(chunk.getSectionIndex(y));
            for (int z=0;z<16;z++) for(int x=0;x<16;x++) {
                BlockState state = section.getBlockState(x,localY,z);
                if (state.isAir() || state.getRenderShape()!=RenderShape.MODEL) continue;
                if (isBuried(chunk,x,y,z)) continue;
                int visibleY = sourceBoundary + y - targetBoundary;
                pos.set(originX+x,y,originZ+z);
                int blockLight=remote.getBrightness(LightLayer.BLOCK,pos);
                int skyLight=remote.getBrightness(LightLayer.SKY,pos);
                visible.add(new Cell(originX+x,visibleY,originZ+z,state,
                    LightTexture.pack(blockLight,skyLight)));
            }
        }
        columns.remove(packed);
        columns.put(packed,List.copyOf(visible));
        while (columns.size()>MAX_COLUMNS)
            columns.remove(columns.keySet().iterator().next());
        blocksBuilt=columns.values().stream().mapToInt(List::size).sum();
        lastArrivalTick=mc.level.getGameTime();
    }

    private static boolean isBuried(LevelChunk chunk,int x,int y,int z) {
        if (x<=0 || x>=15 || z<=0 || z>=15) return false;
        if (y<=chunk.getMinBuildHeight() || y+1>=chunk.getMaxBuildHeight()) return false;
        // Do not invoke remote level.getBlockState: that could touch missing
        // neighboring chunks. Neighbor reads stay within this loaded column.
        for (int[] d:new int[][]{{-1,0,0},{1,0,0},{0,-1,0},
                                {0,1,0},{0,0,-1},{0,0,1}}) {
            int ny=y+d[1];
            var neighbor=chunk.getSection(chunk.getSectionIndex(ny))
                .getBlockState(x+d[0],ny&15,z+d[2]);
            if (!neighbor.canOcclude()) return false;
        }
        return true;
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)
            return;
        lastFrameDrawn=0;
        Minecraft mc=Minecraft.getInstance();
        if (mc.level==null || !mc.level.dimension().location().toString().equals(source)) {
            if (!source.isEmpty()) reset();
            return;
        }
        if (mc.level.getGameTime()-lastArrivalTick>240) {
            reset();
            return;
        }
        if (columns.isEmpty() || event.getPoseStack()==null) return;
        PoseStack pose=event.getPoseStack();
        Vec3 camera=event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        outer:
        for (List<Cell> cells:columns.values()) for (Cell c:cells) {
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
            if (lastFrameDrawn>=MAX_DRAW) break outer;
        }
        if (lastFrameDrawn>0) buffers.endBatch();
    }

    public static int visibleBlockCount() { return blocksBuilt; }
    public static int lastFrameDrawn() { return lastFrameDrawn; }
    public static int columnCount() { return columns.size(); }
    public static String scope() { return source.isEmpty() ? "-" : source+">"+target; }

    public static void reset() {
        columns.clear();
        blocksBuilt=0;
        lastFrameDrawn=0;
        source="";
        target="";
        lastArrivalTick=0;
    }
}
