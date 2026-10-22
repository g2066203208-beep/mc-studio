package dev.reme.worldseam;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * WorldSeam's OWN 1.21.1 vertex buffer storage.
 *
 * Vanilla entity, block-entity, shader, and Create/Aeronautics render passes
 * retain ownership of Minecraft.renderBuffers().bufferSource(). This class
 * NEVER invokes endBatch on another renderer's buffers.
 *
 * Initialization is lazy (first visible seam); all memory is released after
 * world disconnection, disabling or changing the secondary dimension.
 * Render-thread-only: no GL calls or BufferBuilder mutation on worker threads.
 */
public final class SeamRenderBuffers {
    // ByteBufferBuilder will grow if geometry needs more space. These are
    // initial capacities, not falsely advertised hard GPU/memory limits.
    public static final int SHARED_BYTES=256*1024;
    public static final int SOLID_BYTES=512*1024;
    public static final int CUTOUT_BYTES=256*1024;
    public static final int CUTOUT_MIPPED_BYTES=256*1024;
    public static final int TRANSLUCENT_BYTES=512*1024;
    public static final int INITIAL_BYTES=SHARED_BYTES+SOLID_BYTES+
        CUTOUT_BYTES+CUTOUT_MIPPED_BYTES+TRANSLUCENT_BYTES;

    private MultiBufferSource.BufferSource ownedSource;
    private final List<ByteBufferBuilder> ownedBuilders=new ArrayList<>(5);
    private long creations, flushes, disposals;

    public MultiBufferSource.BufferSource source() {
        if(ownedSource!=null)return ownedSource;
        try {
            var shared=allocate(SHARED_BYTES);
            // Separate the most common render types; renderSingleBlock on
            // resource packs may switch types many times within a frame.
            var fixed=new LinkedHashMap<RenderType,ByteBufferBuilder>();
            fixed.put(RenderType.solid(),allocate(SOLID_BYTES));
            fixed.put(RenderType.cutout(),allocate(CUTOUT_BYTES));
            fixed.put(RenderType.cutoutMipped(),allocate(CUTOUT_MIPPED_BYTES));
            fixed.put(RenderType.translucent(),allocate(TRANSLUCENT_BYTES));
            ownedSource=MultiBufferSource.immediateWithBuffers(fixed,shared);
            creations++;
            return ownedSource;
        } catch (RuntimeException | Error failure) {
            // Failed initialisation must not strand native off-heap buffers.
            for(var buffer:ownedBuilders)buffer.close();
            ownedBuilders.clear();
            throw failure;
        }
    }

    private ByteBufferBuilder allocate(int bytes) {
        var result=new ByteBufferBuilder(bytes);
        ownedBuilders.add(result);
        return result;
    }

    public void flush() {
        if(ownedSource==null)return;
        ownedSource.endBatch();
        flushes++;
    }

    public void release() {
        if(ownedSource==null)return;
        // Frames must flush their own buffers in a finally block, before
        // lifecycle teardown. Never issue a stray GPU draw during logout.
        ownedSource=null;
        for(var builder:ownedBuilders)builder.close();
        ownedBuilders.clear();
        disposals++;
    }

    public boolean active() {return ownedSource!=null;}
    public long creations() {return creations;}
    public long flushes() {return flushes;}
    public long disposals() {return disposals;}
}
