package dev.reme.worldseam;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import dev.reme.worldseam.mixin.SeamBufferSourceAccess;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Owns all native CPU buffers for ONE secondary LevelRenderer.
 *
 * 1.21.1 RenderBuffers exposes no close() method. Once its LevelRenderer
 * has been closed (and its worker dispatcher stopped), the owning caller
 * must release the fixed pack, pooled section packs, and entity buffers.
 *
 * One section builder in the pool is enough to prewarm the five-by-five
 * secondary chunk window; allocation is not tied to player render distance.
 * Renderers moved into Minecraft.levelRenderer during direct handoff remain
 * alive; never close their buffers simply because they are no longer "remote".
 */
public final class SeamOwnedLevelRenderBuffers {
    public static final int SECTION_BUILDERS = 1;

    private final RenderBuffers nativeBuffers;
    private boolean released;

    public SeamOwnedLevelRenderBuffers() {
        nativeBuffers = new RenderBuffers(SECTION_BUILDERS);
    }

    public RenderBuffers buffers() { return nativeBuffers; }
    public boolean released() { return released; }

    /** Call only after the associated renderer has been closed or logged out. */
    public void release() {
        if(released) return;
        released=true;
        Set<ByteBufferBuilder> unique=Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            collect(nativeBuffers.bufferSource(),unique);
            collect(nativeBuffers.crumblingBufferSource(),unique);
            // OutlineBufferSource forwards into bufferSource(), so it owns
            // no extra native ByteBufferBuilder.
            for(var buffer:unique)buffer.close();
        } finally {
            nativeBuffers.fixedBufferPack().close();
            var pool=nativeBuffers.sectionBufferPool();
            SectionBufferBuilderPack item;
            while((item=pool.acquire())!=null)item.close();
        }
    }

    private static void collect(MultiBufferSource.BufferSource source,
                                Set<ByteBufferBuilder> unique) {
        // A discarded renderer cannot draw any more geometry. Drop any
        // unfinished batch without trying to render it after logout.
        var access=(SeamBufferSourceAccess)source;
        unique.add(access.worldseam$getSharedBuffer());
        unique.addAll(access.worldseam$getFixedBuffers().values());
    }
}
