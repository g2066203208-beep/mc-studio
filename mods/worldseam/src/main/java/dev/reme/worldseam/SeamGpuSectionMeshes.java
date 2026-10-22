package dev.reme.worldseam;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

/**
 * EXPERIMENTAL world-aware remote chunk section geometry.
 *
 * Unlike renderSingleBlock(), each compiled section uses Minecraft's real
 * BlockRenderDispatcher.renderBatched(world, pos, checkSides=true) ONCE
 * to bake ambient occlusion, biome tint, neighbor face culling and block
 * models into a persistent GPU VertexBuffer. Drawing subsequently costs
 * one draw per visible section/layer instead of thousands of model calls.
 *
 * Current scope: ordinary solid/cutout/cutout_mipped BakedModels only.
 * Custom/translucent/fluid/BE models remain on the existing fallback. The
 * experimental path cannot claim full world, sky, shader or Voxy support.
 */
public final class SeamGpuSectionMeshes {
    private static final Logger LOG=LogUtils.getLogger();
    // Covers the entire typical 5x5 visible section ring without
    // repeatedly evicting an on-screen section, unlike the old 128 cap.
    private static final int MAX_MESHES=192;
    private static final int MAX_BAKED_CELLS_PER_FRAME=180;
    private static final long MAX_BAKE_CPU_NANOS=4_000_000L;
    private static final RenderType[] LAYERS={
        RenderType.solid(),RenderType.cutoutMipped(),RenderType.cutout(),
        RenderType.translucent()
    };
    private record Key(int cx,int cz,int sy) {}
    private record Compiled(SeamNativeGeometryClient.Slice origin,
                            Map<RenderType,VertexBuffer> vbos,
                            int cells,int endStone,int fluidCells) implements AutoCloseable {
        @Override public void close() {
            for(var vbo:vbos.values())vbo.close();
        }
    }
    private static final class Building implements AutoCloseable {
        final SeamNativeGeometryClient.Slice slice;
        final ByteBufferBuilder[] owners=new ByteBufferBuilder[LAYERS.length];
        final BufferBuilder[] builders=new BufferBuilder[LAYERS.length];
        final RandomSource random=RandomSource.create(0);
        final PoseStack pose=new PoseStack();
        int index;
        boolean unsupported;
        Building(SeamNativeGeometryClient.Slice slice) {
            this.slice=slice;
            for(int i=0;i<LAYERS.length;i++) {
                owners[i]=new ByteBufferBuilder(256*1024);
                builders[i]=new BufferBuilder(owners[i],
                    VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);
            }
        }
        @Override public void close() {
            for(var owner:owners)if(owner!=null)owner.close();
        }
    }

    // LinkedHashMap insertion order bounds VRAM usage independently of the
    // number of chunks the active native world has streamed in.
    private final LinkedHashMap<Key,Compiled> mesh=new LinkedHashMap<>();
    private final Map<Key,SeamNativeGeometryClient.Slice> unsupported=new LinkedHashMap<>();
    // Camera-nearest, pre-sorted visible section set. Evictions MUST not
    // preferentially discard the very meshes required this frame.
    private final Set<Key> protectedVisible=new HashSet<>();
    private Building building;
    private final SeamRenderSamples bakeSamples=new SeamRenderSamples(120);
    private long uploaded;
    private long evicted;
    private long failedSlices;
    private long lastGpuDrawn;
    private long lastGpuCells;
    private long lastGpuFluidCells;

    public static boolean enabled() {
        return Boolean.getBoolean("worldseam.gpuSections");
    }
    public void invalidate(SeamNativeGeometryClient.Slice newSlice,int cx,int cz,int sy) {
        if(!enabled())return;
        Key key=new Key(cx,cz,sy);
        Compiled old=mesh.remove(key);
        if(old!=null) {old.close();evicted++;}
        unsupported.remove(key);
        if(building!=null &&
            building.slice.cx()==cx &&
            building.slice.cz()==cz &&
            building.slice.sectionY()==sy) {
            building.close();
            building=null;
        }
    }
    public boolean has(SeamNativeGeometryClient.Slice slice) {
        if(!enabled())return false;
        Compiled item=mesh.get(new Key(slice.cx(),slice.cz(),slice.sectionY()));
        return item!=null && item.origin()==slice;
    }

    /**
     * Runs on the render thread; never blocks the server or the chunk packet
     * listener. This routine performs at most 180 model tesselations and
     * roughly 4 ms CPU work in a frame, then resumes the SAME builder.
     */
    public void bakeVisible(ClientLevel remote,
                            List<SeamNativeGeometryClient.Slice> visible,
                            int sourceBoundary,int targetBoundary) {
        if(!enabled()||remote==null||visible.isEmpty())return;
        // The caller has sorted visible slices by true camera distance.
        // Only protect/cache the nearest capacity sections. FIFO eviction
        // previously thrashed: with 150 visible and a cap of 128, compiling
        // the farthest section evicted the central closest section, forcing
        // it to be uploaded AGAIN on the next frame.
        protectedVisible.clear();
        int count=Math.min(MAX_MESHES,visible.size());
        for(int i=0;i<count;i++) {
            var slice=visible.get(i);
            protectedVisible.add(new Key(slice.cx(),slice.cz(),slice.sectionY()));
        }
        if(building!=null) {
            var active=building.slice;
            if(!protectedVisible.contains(new Key(active.cx(),active.cz(),active.sectionY()))) {
                building.close();
                building=null;
            }
        }
        if(building==null) {
            for(int i=0;i<count;i++) {
                var slice=visible.get(i);
                Key key=new Key(slice.cx(),slice.cz(),slice.sectionY());
                Compiled item=mesh.get(key);
                if(item!=null && item.origin()==slice)continue;
                if(unsupported.get(key)==slice)continue;
                building=new Building(slice);
                break;
            }
        }
        if(building==null)return;
        Building job=building;
        var cells=job.slice.cells();
        var dispatcher=Minecraft.getInstance().getBlockRenderer();
        var nativePos=new BlockPos.MutableBlockPos();
        int sx=job.slice.cx()*16,sy=job.slice.sectionY()*16,sz=job.slice.cz()*16;
        long started=System.nanoTime();
        int processed=0;
        try {
            while(job.index<cells.size() && processed<MAX_BAKED_CELLS_PER_FRAME) {
                var c=cells.get(job.index++);
                processed++;
                int nativeY=c.y()-sourceBoundary+targetBoundary;
                nativePos.set(c.x(),nativeY,c.z());
                if(c.drawModel()) {
                    var model=dispatcher.getBlockModel(c.state());
                    job.random.setSeed(c.state().getSeed(nativePos));
                    var available=model.getRenderTypes(c.state(),job.random,ModelData.EMPTY);
                    for(var type:available) {
                        int index=layerIndex(type);
                        if(index<0) {
                            job.unsupported=true;
                            break;
                        }
                        job.random.setSeed(c.state().getSeed(nativePos));
                        job.pose.pushPose();
                        try {
                            job.pose.translate(c.x()-sx,nativeY-sy,c.z()-sz);
                            dispatcher.renderBatched(c.state(),nativePos,remote,
                                job.pose,job.builders[index],true,job.random,
                                ModelData.EMPTY,type);
                        } finally {
                            job.pose.popPose();
                        }
                    }
                }
                // The default fallback skipped ALL lava/water because their
                // BlockState is INVISIBLE. Real fluid geometry is position-
                // dependent: use Minecraft's native liquid tessellator with
                // exactly the source world and native neighbor blocks.
                var fluid=c.state().getFluidState();
                if(c.drawFluid()) {
                    RenderType type=ItemBlockRenderTypes.getRenderLayer(fluid);
                    int index=layerIndex(type);
                    if(index<0) {
                        job.unsupported=true;
                    } else {
                        var transformed=new SeamOffsetVertexConsumer(
                            job.builders[index],-sx,-sy,-sz);
                        dispatcher.renderLiquid(nativePos,remote,transformed,
                            c.state(),fluid);
                    }
                }
                if(job.unsupported)break;
                if(System.nanoTime()-started>=MAX_BAKE_CPU_NANOS)break;
            }
            if(job.index>=cells.size()||job.unsupported) {
                finish(job);
                building=null;
            }
        } catch(RuntimeException problem) {
            // Model and fluid renderers supplied by other Mods may require a
            // different render context. Never let ONE unsupported remote
            // BakedModel kill Minecraft; keep its section in the bounded
            // legacy fallback renderer until that chunk is changed.
            Key key=new Key(job.slice.cx(),job.slice.cz(),job.slice.sectionY());
            unsupported.put(key,job.slice);
            failedSlices++;
            LOG.warn("WORLDSEAM_GPU_SECTION_FALLBACK: {} {} {}: {}",
                key.cx(),key.cz(),key.sy(),problem.toString());
            job.close();
            building=null;
        } catch(Error fatal) {
            // OOME / linkage and other fatal VM errors are not ordinary
            // custom-model rendering incompatibilities. Do not conceal.
            job.close();
            building=null;
            throw fatal;
        } finally {
            bakeSamples.add(Math.max(0,System.nanoTime()-started));
        }
    }

    private static int layerIndex(RenderType type) {
        for(int i=0;i<LAYERS.length;i++)if(LAYERS[i]==type)return i;
        return -1;
    }
    private void finish(Building job) {
        Key key=new Key(job.slice.cx(),job.slice.cz(),job.slice.sectionY());
        if(job.unsupported) {
            unsupported.put(key,job.slice);
            job.close();
            return;
        }
        var buffers=new LinkedHashMap<RenderType,VertexBuffer>();
        try {
            for(int i=0;i<LAYERS.length;i++) {
                MeshData data=job.builders[i].build();
                if(data==null)continue;
                try(data) {
                    var vbo=new VertexBuffer(VertexBuffer.Usage.STATIC);
                    try {
                        vbo.bind();
                        vbo.upload(data);
                        buffers.put(LAYERS[i],vbo);
                    } catch(RuntimeException | Error e) {
                        vbo.close();
                        throw e;
                    } finally {
                        VertexBuffer.unbind();
                    }
                }
            }
            int endCount=0,fluidCount=0;
            for(var c:job.slice.cells()) {
                if(c.state().is(Blocks.END_STONE))endCount++;
                if(c.drawFluid())fluidCount++;
            }
            mesh.put(key,new Compiled(job.slice,Map.copyOf(buffers),
                job.slice.cells().size(),endCount,fluidCount));
            uploaded++;
            while(mesh.size()>MAX_MESHES) {
                // Prefer to discard a section outside the current nearest
                // renderable set, not one the next frame must immediately
                // recompile. This avoids unbounded VRAM growth and reload
                // oscillations when the camera sits still at a seam.
                Key victim=null;
                for(var cached:mesh.keySet()) {
                    if(!protectedVisible.contains(cached)) {
                        victim=cached;
                        break;
                    }
                }
                if(victim==null)victim=mesh.keySet().iterator().next();
                mesh.remove(victim).close();
                evicted++;
            }
        } catch(RuntimeException | Error problem) {
            for(var vbo:buffers.values())vbo.close();
            throw problem;
        } finally {
            job.close();
        }
    }

    public void beginFrame() {lastGpuDrawn=0;lastGpuCells=0;lastGpuFluidCells=0;}

    /**
     * Render only opaque layers first, across ALL visible sections.
     * Translucent faces must be submitted later, after every solid block
     * and fallback proxy mesh, otherwise liquids can appear in front of
     * opaque terrain in an unrelated chunk section.
     */
    public int draw(SeamNativeGeometryClient.Slice slice,PoseStack pose,Vec3 camera,
                    int sourceBoundary,int targetBoundary) {
        if(!has(slice))return 0;
        Compiled entry=mesh.get(new Key(slice.cx(),slice.cz(),slice.sectionY()));
        for(int i=0;i<LAYERS.length-1;i++)drawLayer(slice,entry,LAYERS[i],
            pose,camera,sourceBoundary,targetBoundary);
        lastGpuDrawn++;
        lastGpuCells+=entry.cells();
        lastGpuFluidCells+=entry.fluidCells();
        return entry.endStone();
    }

    /** Call in BACK-TO-FRONT section order AFTER all opaque and CPU draws.
     * Translucent quads within a section are not yet dynamically sorted;
     * future native section renderers must provide view-dependent sorting. */
    public void drawTranslucent(SeamNativeGeometryClient.Slice slice,
                                PoseStack pose,Vec3 camera,
                                int sourceBoundary,int targetBoundary) {
        if(!has(slice))return;
        Compiled entry=mesh.get(new Key(slice.cx(),slice.cz(),slice.sectionY()));
        drawLayer(slice,entry,RenderType.translucent(),
            pose,camera,sourceBoundary,targetBoundary);
    }

    private static void drawLayer(SeamNativeGeometryClient.Slice slice,
                                  Compiled entry,RenderType type,
                                  PoseStack pose,Vec3 camera,
                                  int sourceBoundary,int targetBoundary) {
        VertexBuffer vbo=entry.vbos().get(type);
        if(vbo==null)return;
        int originX=slice.cx()*16,originZ=slice.cz()*16;
        int mappedSectionY=SeamSectionLayout.mappedSectionMinY(
            slice.sectionY()*16,sourceBoundary,targetBoundary);
        Matrix4f matrix=new Matrix4f(pose.last().pose()).translate(
            (float)(originX-camera.x),(float)(mappedSectionY-camera.y),
            (float)(originZ-camera.z));
        type.setupRenderState();
        try {
            var shader=RenderSystem.getShader();
            if(shader==null)throw new IllegalStateException(
                "No Minecraft shader for WorldSeam chunk layer "+type);
            vbo.bind();
            try {
                vbo.drawWithShader(matrix,RenderSystem.getProjectionMatrix(),shader);
            } finally {
                VertexBuffer.unbind();
            }
        } finally {
            type.clearRenderState();
        }
    }

    public int count() {return mesh.size();}
    public long uploaded() {return uploaded;}
    public long evicted() {return evicted;}
    public long failedSlices(){return failedSlices;}
    public long lastGpuDrawn() {return lastGpuDrawn;}
    public long lastGpuCells() {return lastGpuCells;}
    public long lastGpuFluidCells() {return lastGpuFluidCells;}
    public double p95BakeMs() {return bakeSamples.p95Ms();}

    public void clear() {
        for(var item:mesh.values())item.close();
        mesh.clear();
        unsupported.clear();
        protectedVisible.clear();
        if(building!=null)building.close();
        building=null;
        bakeSamples.clear();
        beginFrame();
    }
}
