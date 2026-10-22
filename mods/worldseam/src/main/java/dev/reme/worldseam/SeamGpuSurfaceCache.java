package dev.reme.worldseam;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistent GPU mesh cache for the remote dimension's real ClientLevel
 * section surface. A section is tessellated ONCE into native Minecraft
 * MeshData/VertexBuffer objects and can then be drawn every frame without
 * rebuilding thousands of block models.
 *
 * This is a bounded, incremental intermediate backend, NOT the complete
 * second LevelRenderer/Iris/Voxy pipeline. Respect a conservative retained
 * vertex-memory budget, a per-frame upload budget and exact Slice identity
 * so stale meshes never survive an inbound remote chunk change.
 *
 * All methods must execute on Minecraft's render thread.
 */
public final class SeamGpuSurfaceCache {
    private static final long MAX_ESTIMATED_GPU_BYTES=96L*1024*1024;
    private static final int MAX_UPLOAD_CELLS_PER_FRAME=700;
    private static final int MAX_UPLOAD_SLICES_PER_FRAME=2;
    private static final int MAX_BUILDERS_PER_SLICE=16;

    private record Layer(RenderType type,VertexBuffer gpu) {}
    private static final class Entry {
        final List<Layer> layers;
        final int cellCount;
        final int endStoneCount;
        final long estimatedBytes;
        long lastFrame;
        Entry(List<Layer> layers,int cellCount,int endStoneCount,long bytes,long tick) {
            this.layers=layers;
            this.cellCount=cellCount;
            this.endStoneCount=endStoneCount;
            this.estimatedBytes=bytes;
            this.lastFrame=tick;
        }
    }

    // Identity matters: a rebuilt slice replaces the previous one even when
    // both have the same sectionY or happen to contain equal blocks.
    private final Map<SeamNativeGeometryClient.Slice,Entry> entries=
        new IdentityHashMap<>();
    private long estimatedBytes;
    private long frame;
    private long compiledSlices;
    private long evictedSlices;
    private long uploadedCells;
    private int drawnCells;
    private int drawnEndStone;
    private int builtThisFrame;
    private int drawnLayers;
    private long lastBuildNanos;

    /** Call before drawing, with all currently retained CPU slice identities. */
    public void prune(Set<SeamNativeGeometryClient.Slice> live) {
        var it=entries.entrySet().iterator();
        while(it.hasNext()) {
            var entry=it.next();
            if(!live.contains(entry.getKey())) {
                close(entry.getValue());
                it.remove();
                evictedSlices++;
            }
        }
    }

    private void close(Entry e) {
        estimatedBytes-=e.estimatedBytes;
        for(var layer:e.layers)layer.gpu().close();
    }

    private void ensureSpace(long bytes) {
        while(!entries.isEmpty() &&
              estimatedBytes+bytes>MAX_ESTIMATED_GPU_BYTES) {
            var oldest=entries.entrySet().stream()
                .min(Comparator.comparingLong(e->e.getValue().lastFrame))
                .orElseThrow();
            close(oldest.getValue());
            entries.remove(oldest.getKey());
            evictedSlices++;
        }
    }

    /**
     * Dynamic RenderTypes are necessary for third-party block models and
     * resource packs. Do NOT assume all blocks have DefaultVertexFormat.BLOCK:
     * renderSingleBlock selects ITEM/ENTITY formats for many model types.
     */
    private Entry compile(SeamNativeGeometryClient.Slice slice,
                          Minecraft mc) {
        var buffers=new LinkedHashMap<RenderType,BufferBuilder>();
        var allocations=new ArrayList<ByteBufferBuilder>();
        var layers=new ArrayList<Layer>();
        int stone=0;
        // For a cached mesh the vertices are LOCAL to the native 16x16x16
        // section. Camera motion only changes the modelView matrix at draw.
        var pose=new PoseStack();
        double ox=slice.bounds().minX;
        double oy=slice.bounds().minY;
        double oz=slice.bounds().minZ;
        MultiBufferSource source=type -> {
            BufferBuilder b=buffers.get(type);
            if(b!=null)return b;
            if(buffers.size()>=MAX_BUILDERS_PER_SLICE) {
                throw new IllegalStateException(
                    "Too many block model RenderTypes in one native section");
            }
            ByteBufferBuilder raw=new ByteBufferBuilder(64*1024);
            allocations.add(raw);
            b=new BufferBuilder(raw,type.mode(),type.format());
            buffers.put(type,b);
            return b;
        };
        try {
            for(var cell:slice.cells()) {
                pose.pushPose();
                try {
                    pose.translate(cell.x()-ox,cell.y()-oy,cell.z()-oz);
                    mc.getBlockRenderer().renderSingleBlock(
                        cell.state(),pose,source,cell.light(),
                        OverlayTexture.NO_OVERLAY);
                } finally {
                    pose.popPose();
                }
                if(cell.state().is(net.minecraft.world.level.block.Blocks.END_STONE))
                    stone++;
            }
            // Upload once; retain only the dedicated GPU VBO, not the
            // potentially large temporary native CPU ByteBufferBuilders.
            for(var entry:buffers.entrySet()) {
                MeshData mesh=entry.getValue().build();
                if(mesh==null)continue;
                try {
                    VertexBuffer gpu=new VertexBuffer(VertexBuffer.Usage.STATIC);
                    try {
                        gpu.bind();
                        gpu.upload(mesh);
                        layers.add(new Layer(entry.getKey(),gpu));
                    } catch(Throwable error) {
                        gpu.close();
                        throw error;
                    } finally {
                        VertexBuffer.unbind();
                    }
                } finally {
                    mesh.close();
                }
            }
            // Conservative occupancy estimate. Exact GL driver allocations
            // cannot be read reliably; never describe this as a hard VRAM cap.
            long estimate=SeamGpuBudget.estimateBytes(slice.cells().size(),
                Math.max(1,layers.size()));
            return new Entry(List.copyOf(layers),
                slice.cells().size(),stone,estimate,frame);
        } catch(Throwable failure) {
            for(var layer:layers)layer.gpu().close();
            throw failure;
        } finally {
            for(var raw:allocations)raw.close();
        }
    }

    public void draw(List<SeamNativeGeometryClient.Slice> visible,
                     Minecraft mc,PoseStack pose,
                     double cameraX,double cameraY,double cameraZ) {
        frame++;
        builtThisFrame=0;
        drawnCells=0;
        drawnEndStone=0;
        drawnLayers=0;

        // Upload small nearby sections first. Keep other already-built
        // distant VBOs; no hardcoded 3,500 visible-block cap anymore.
        int budget=MAX_UPLOAD_CELLS_PER_FRAME;
        for(var slice:visible) {
            if(entries.containsKey(slice))continue;
            int cells=slice.cells().size();
            if(builtThisFrame>=MAX_UPLOAD_SLICES_PER_FRAME)break;
            if(cells>budget && builtThisFrame>0)break;
            long started=System.nanoTime();
            Entry item=compile(slice,mc);
            lastBuildNanos=Math.max(0,System.nanoTime()-started);
            ensureSpace(item.estimatedBytes);
            entries.put(slice,item);
            estimatedBytes+=item.estimatedBytes;
            compiledSlices++;
            uploadedCells+=cells;
            builtThisFrame++;
            budget-=cells;
        }

        // Render depth-writing opaque geometry first and translucent layers
        // after, respecting each layer's own state/shader.
        for(int pass=0;pass<2;pass++) {
            for(var slice:visible) {
                Entry mesh=entries.get(slice);
                if(mesh==null)continue;
                mesh.lastFrame=frame;
                Matrix4f world=new Matrix4f(RenderSystem.getModelViewMatrix())
                    .mul(pose.last().pose())
                    .translate((float)(slice.bounds().minX-cameraX),
                               (float)(slice.bounds().minY-cameraY),
                               (float)(slice.bounds().minZ-cameraZ));
                for(var layer:mesh.layers) {
                    RenderType type=layer.type();
                    if((type.sortOnUpload()?1:0)!=pass)continue;
                    type.setupRenderState();
                    try {
                        var shader=RenderSystem.getShader();
                        if(shader!=null) {
                            layer.gpu().bind();
                            try {
                                layer.gpu().drawWithShader(
                                    world,RenderSystem.getProjectionMatrix(),shader);
                                drawnLayers++;
                            } finally {
                                VertexBuffer.unbind();
                            }
                        }
                    } finally {
                        type.clearRenderState();
                    }
                }
                if(pass==0) {
                    drawnCells+=mesh.cellCount;
                    drawnEndStone+=mesh.endStoneCount;
                }
            }
        }
    }

    public int readySlices(){return entries.size();}
    public int builtThisFrame(){return builtThisFrame;}
    public int drawnCells(){return drawnCells;}
    public int drawnEndStone(){return drawnEndStone;}
    public int drawnLayers(){return drawnLayers;}
    public long estimatedBytes(){return estimatedBytes;}
    public long compiledSlices(){return compiledSlices;}
    public long evictedSlices(){return evictedSlices;}
    public long uploadedCells(){return uploadedCells;}
    public double lastBuildMs(){return lastBuildNanos/1_000_000.0;}

    public void clear() {
        for(var e:entries.values())close(e);
        entries.clear();
        estimatedBytes=0;
        frame=0;
        drawnCells=drawnEndStone=builtThisFrame=drawnLayers=0;
        lastBuildNanos=0;
    }
}
