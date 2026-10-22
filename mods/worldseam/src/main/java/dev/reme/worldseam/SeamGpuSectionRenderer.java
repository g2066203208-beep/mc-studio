package dev.reme.worldseam;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded, per-native-16^3-section persistent GPU mesh cache for the temporary
 * cross-dimensional preview renderer. Each BLOCK model is tessellated only on
 * chunk change, not in EVERY FRAME. In subsequent frames all uploaded meshes
 * can be drawn without the old 3500-block budget leaving giant visual holes.
 *
 * This is NOT a replacement for native dual LevelRenderer composition, fluid,
 * shader sky, or shader-specific transparency sorting. The separate Model
 * render layers are retained; an uncached section uses the prior safe fallback.
 * All methods execute on the Minecraft render/client thread.
 */
public final class SeamGpuSectionRenderer {
    public record Cell(int x,int y,int z,BlockState state,int light) {}
    public record Key(long column,int sectionY) {}
    public record Section(Key key,int originX,int originY,int originZ,
                          AABB bounds,List<Cell> cells) {}
    private record Layer(RenderType renderType,VertexBuffer buffer,long vertexBytes) {}
    private record DrawTask(Section section,Layer layer) {}
    private record Cached(Section section,List<Layer> layers) {
        long bytes() {
            long count=0;
            for(var layer:layers)count+=layer.vertexBytes();
            return count;
        }
        void close() { for(var layer:layers)layer.buffer().close(); }
    }

    public static final int MAX_SECTION_UPLOADS_PER_FRAME=1;
    public static final int MAX_CACHED_SECTIONS=300;
    // Bound the total persistent vertex bytes of the proxy cache independently
    // from model complexity, resource packs and third-party block models.
    // This is a CPU-side estimate of vertex and explicit index storage;
    // native GPU allocation may additionally include driver overhead.
    public static final long MAX_CACHED_VERTEX_BYTES=128L*1024*1024;
    private final Map<Key,Cached> loaded=new LinkedHashMap<>();
    private final Map<Key,Section> dirty=new LinkedHashMap<>();
    // Cached VBOs are not proof that a shader actually drew them this frame.
    private final Set<Key> fullyDrawnLastFrame=new HashSet<>();
    private long compilations,uploads,drawCalls,evictions;
    private long cachedVertexBytes;
    private int lastFrameCachedSections,lastFrameUncachedSections;
    private int lastFrameModelsVisible;
    private int lastFrameEndStoneModels;

    public void changed(Section section) {
        dirty.put(section.key(),section);
        // Keep the previous GPU mesh until the replacement is FULLY built
        // and uploaded to avoid visible flicker on live BlockState changes.
    }

    public void removed(Key key) {
        dirty.remove(key);
        Cached old=loaded.remove(key);
        if(old!=null){
            cachedVertexBytes-=old.bytes();
            old.close();evictions++;
        }
    }

    public void trimTo(Set<Key> alive) {
        dirty.keySet().removeIf(key->!alive.contains(key));
        var iterator=loaded.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(!alive.contains(entry.getKey())) {
                cachedVertexBytes-=entry.getValue().bytes();
                entry.getValue().close();
                iterator.remove();
                evictions++;
            }
        }
    }

    /** CPU-side baked quad data uploads are bounded to ONE native section per
     *  frame, while ALL previously uploaded sections remain visible. */
    public void compileVisible(Minecraft mc, ClientLevel remote, List<Section> visible,
                               Vec3 camera, int sourceBoundary, int targetBoundary) {
        var candidates=new ArrayList<Section>();
        for(var section:visible)
            if(dirty.containsKey(section.key()))candidates.add(section);
        candidates.sort(Comparator.comparingDouble(s->distanceSq(s.bounds(),camera)));
        int limit=Math.min(MAX_SECTION_UPLOADS_PER_FRAME,candidates.size());
        for(int n=0;n<limit;n++) {
            var candidate=candidates.get(n);
            if(dirty.get(candidate.key())!=candidate)continue;
            Cached built=compile(mc,remote,candidate,sourceBoundary,targetBoundary);
            if(built==null)continue;
            Cached old=loaded.put(candidate.key(),built);
            dirty.remove(candidate.key());
            if(old!=null){
                cachedVertexBytes-=old.bytes();
                old.close();
            }
            cachedVertexBytes+=built.bytes();
            compilations++;
            uploads+=built.layers().size();
        }
        while(loaded.size()>MAX_CACHED_SECTIONS ||
              cachedVertexBytes>MAX_CACHED_VERTEX_BYTES) {
            var oldest=loaded.entrySet().iterator();
            var value=oldest.next();
            cachedVertexBytes-=value.getValue().bytes();
            value.getValue().close();
            oldest.remove();
            evictions++;
        }
    }

    private static double distanceSq(AABB box,Vec3 c) {
        double dx=(box.minX+box.maxX)*0.5-c.x;
        double dy=(box.minY+box.maxY)*0.5-c.y;
        double dz=(box.minZ+box.maxZ)*0.5-c.z;
        return dx*dx+dy*dy+dz*dz;
    }

    private static Cached compile(Minecraft mc,ClientLevel remote,Section section,
                                  int sourceBoundary,int targetBoundary) {
        var byteBuffers=new LinkedHashMap<RenderType,ByteBufferBuilder>();
        var builders=new LinkedHashMap<RenderType,BufferBuilder>();
        MultiBufferSource capture = renderType -> builders.computeIfAbsent(
            renderType,type->{
                ByteBufferBuilder bytes=new ByteBufferBuilder(Math.max(8192,
                    Math.min(type.bufferSize(),262144)));
                byteBuffers.put(type,bytes);
                return new BufferBuilder(bytes,type.mode(),type.format());
            });
        var layers=new ArrayList<Layer>();
        try {
            // The previous renderSingleBlock path treated every terrain block
            // as an INVENTORY MODEL: it emitted hidden faces, missed native AO,
            // used item tint instead of biome/world tint and ignored the
            // chunk RenderType and model data. Dense terrain could overrun
            // the 128 MiB cache and leave the neighboring world fragmented.
            // Tessellate against the actual remote ClientLevel instead.
            PoseStack pose=new PoseStack();
            RandomSource random=RandomSource.create();
            for(var cell:section.cells()) {
                // The GPU section is positioned in the source's mapped view,
                // but all block occlusion, lighting, biome colors, model data
                // and pseudorandom variants must use the TARGET's native Y.
                BlockPos nativePos=new BlockPos(cell.x(),
                    SeamSectionLayout.nativeBlockY(cell.y(),
                        sourceBoundary,targetBoundary),cell.z());
                var blockModel=mc.getBlockRenderer().getBlockModel(cell.state());
                ModelData modelData=blockModel.getModelData(
                    remote,nativePos,cell.state(),ModelData.EMPTY);
                random.setSeed(cell.state().getSeed(nativePos));
                pose.pushPose();
                try {
                    pose.translate(cell.x()-section.originX(),
                        cell.y()-section.originY(),
                        cell.z()-section.originZ());
                    // Chunk RenderTypes use BLOCK vertex format, unlike
                    // the NEW_ENTITY format of renderSingleBlock items.
                    for(RenderType type:blockModel.getRenderTypes(
                        cell.state(),random,modelData)) {
                        random.setSeed(cell.state().getSeed(nativePos));
                        mc.getBlockRenderer().renderBatched(
                            cell.state(),nativePos,remote,pose,
                            capture.getBuffer(type),true,random,modelData,type);
                    }
                } finally {
                    pose.popPose();
                }
            }
            for(var entry:builders.entrySet()) {
                var data=entry.getValue().build();
                if(data==null)continue;
                try(data) {
                    var vb=new VertexBuffer(VertexBuffer.Usage.STATIC);
                    try {
                        vb.bind();
                        long vertexBytes=data.vertexBuffer().remaining();
                        var indices=data.indexBuffer();
                        if(indices!=null)vertexBytes+=indices.remaining();
                        vb.upload(data);
                        layers.add(new Layer(entry.getKey(),vb,vertexBytes));
                    } catch (RuntimeException | Error failed) {
                        vb.close();
                        throw failed;
                    } finally {
                        VertexBuffer.unbind();
                    }
                }
            }
            return new Cached(section,List.copyOf(layers));
        } catch(RuntimeException | Error failed) {
            for(var layer:layers)layer.buffer().close();
            // Fail safely: another mod's incompatible dynamic baked model
            // must not corrupt the current active-world GL render state.
            throw failed;
        } finally {
            for(var bytes:byteBuffers.values())bytes.close();
        }
    }

    /** Called inside RenderLevelStageEvent with the original source camera. */
    public void draw(List<Section> visible,PoseStack pose,Vec3 camera) {
        lastFrameCachedSections=0;
        lastFrameUncachedSections=0;
        lastFrameModelsVisible=0;
        lastFrameEndStoneModels=0;
        var projection=RenderSystem.getProjectionMatrix();

        // Order by RENDER TYPE before submitting GPU draws. The first
        // prototype switched texture, shader and blend states hundreds of
        // times each frame (one switch per section x layer). Grouping cuts
        // these expensive switches to one per actual RenderType.
        fullyDrawnLastFrame.clear();
        var completedLayerCounts=new HashMap<Key,Integer>();
        var passes=new LinkedHashMap<RenderType,List<DrawTask>>();
        for(var section:visible) {
            var cached=loaded.get(section.key());
            if(cached==null || cached.layers().isEmpty()) continue;
            for(var layer:cached.layers())
                passes.computeIfAbsent(layer.renderType(),k->new ArrayList<>())
                    .add(new DrawTask(section,layer));
        }
        var types=new ArrayList<>(passes.keySet());
        types.sort(Comparator.comparing(type->type.sortOnUpload()));
        for(var type:types) {
            var tasks=passes.get(type);
            if(type.sortOnUpload()) {
                // Blend the distant translucent sections first. Individual
                // quads are still static and need future re-sorting support.
                tasks.sort(Comparator.comparingDouble(
                    (DrawTask task)->distanceSq(task.section().bounds(),camera))
                    .reversed());
            }
            type.setupRenderState();
            try {
                var shader=RenderSystem.getShader();
                if(shader==null)continue;
                for(var task:tasks) {
                    Section section=task.section();
                    Matrix4f matrix=new Matrix4f(pose.last().pose()).translate(
                        (float)(section.originX()-camera.x),
                        (float)(section.originY()-camera.y),
                        (float)(section.originZ()-camera.z));
                    task.layer().buffer().bind();
                    task.layer().buffer().drawWithShader(matrix,projection,shader);
                    drawCalls++;
                    completedLayerCounts.merge(section.key(),1,Integer::sum);
                }
            } finally {
                VertexBuffer.unbind();
                type.clearRenderState();
            }
        }
        // A section passes the GUI coverage gate only after ALL its actual
        // layer VBOs were submitted. A zero-layer mesh or missing shader
        // instead keeps the safe fallback active, not a false green light.
        for(var section:visible) {
            var cached=loaded.get(section.key());
            if(cached==null || cached.layers().isEmpty() ||
                completedLayerCounts.getOrDefault(section.key(),0)<cached.layers().size()) {
                lastFrameUncachedSections++;
                continue;
            }
            fullyDrawnLastFrame.add(section.key());
            lastFrameCachedSections++;
            // When a section changes, its OLD mesh stays until re-uploaded:
            // report the submitted mesh instead of the new dirty cell list.
            var submittedCells=cached.section().cells();
            lastFrameModelsVisible+=submittedCells.size();
            for(var cell:submittedCells)
                if(cell.state().is(net.minecraft.world.level.block.Blocks.END_STONE))
                    lastFrameEndStoneModels++;
        }
    }

    public boolean ready(Key key){return fullyDrawnLastFrame.contains(key);}
    public int cachedCount(){return loaded.size();}
    public int pendingCount(){return dirty.size();}
    public int lastFrameCachedSections(){return lastFrameCachedSections;}
    public int lastFrameUncachedSections(){return lastFrameUncachedSections;}
    public int lastFrameModelsVisible(){return lastFrameModelsVisible;}
    public int lastFrameEndStoneModels(){return lastFrameEndStoneModels;}
    public long uploads(){return uploads;}
    public long drawCalls(){return drawCalls;}
    public long compilations(){return compilations;}
    public long evictions(){return evictions;}
    public long cachedVertexBytes(){return cachedVertexBytes;}

    public void release() {
        for(var cached:loaded.values())cached.close();
        loaded.clear();
        dirty.clear();
        fullyDrawnLastFrame.clear();
        cachedVertexBytes=0;
        lastFrameCachedSections=lastFrameUncachedSections=lastFrameModelsVisible=0;
        lastFrameEndStoneModels=0;
    }
}
