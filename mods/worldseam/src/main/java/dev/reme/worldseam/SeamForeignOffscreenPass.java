package dev.reme.worldseam;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.nio.IntBuffer;

/**
 * Real, independently depth-backed GPU render target for the secondary
 * dimension. The experimental opaque pass draws ACTUAL foreign native-chunk
 * VBOs here, never a screenshot, color fill, or synthetic sky.
 *
 * This is deliberately NOT composited yet: a correct two-world compositor
 * must clip by the physical seam plane and compare both per-pixel depths.
 * Blitting this target over the source framebuffer would be visually false.
 * The normal existing source-frame path remains intact during development.
 */
public final class SeamForeignOffscreenPass {
    private static final Logger LOG = LogUtils.getLogger();
    private static TextureTarget target;
    private static long allocations;
    private static long resizes;
    private static long opaquePasses;
    private static long restoreFailures;
    private static boolean active;

    private SeamForeignOffscreenPass() {}

    public static long opaquePasses() { return opaquePasses; }
    public static long restoreFailures() { return restoreFailures; }
    public static boolean allocated() { return target != null; }

    /**
     * Call on the Minecraft render thread only. Save the *actually bound*
     * framebuffer, not just Minecraft's main target: Fabulous/shader passes
     * may be drawing to a different framebuffer at this stage.
     */
    public static void drawOpaque(Minecraft mc, Runnable foreignDraw) {
        RenderSystem.assertOnRenderThread();
        if (active) throw new IllegalStateException("Nested WorldSeam offscreen pass");
        RenderTarget source = mc.getMainRenderTarget();
        if (source == null || source.width <= 0 || source.height <= 0) return;
        int oldFramebuffer = GlStateManager.getBoundFramebuffer();
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int x = viewport.get(0), y = viewport.get(1);
        int width = viewport.get(2), height = viewport.get(3);
        active = true;
        try {
            if (target == null) {
                target = new TextureTarget(source.width, source.height, true, true);
                allocations++;
            } else if (target.width != source.width || target.height != source.height) {
                target.resize(source.width, source.height, true);
                resizes++;
            }
            if (target.getColorTextureId() <= 0 || target.getDepthTextureId() <= 0)
                throw new IllegalStateException("Secondary native color/depth attachments missing");
            target.setClearColor(0f, 0f, 0f, 0f);
            target.clear(true);
            target.bindWrite(true);
            foreignDraw.run();
            opaquePasses++;
            if (opaquePasses == 1 || opaquePasses % 120 == 0)
                LOG.info("WORLDSEAM_NATIVE_OFFSCREEN_OPAQUE: passes={}, colorTexture={}, depthTexture={}, size={}x{}, allocations={}, resizes={}, compositeReady=false",
                    opaquePasses, target.getColorTextureId(), target.getDepthTextureId(),
                    target.width, target.height, allocations, resizes);
        } finally {
            try {
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFramebuffer);
                RenderSystem.viewport(x, y, width, height);
                if (GlStateManager.getBoundFramebuffer() != oldFramebuffer)
                    throw new IllegalStateException("Source framebuffer not restored");
            } catch (RuntimeException | Error failure) {
                restoreFailures++;
                throw failure;
            } finally {
                active = false;
            }
        }
    }

    /** Release only the framebuffer we own; never destroy a vanilla target. */
    public static void release() {
        RenderSystem.assertOnRenderThread();
        if (active) throw new IllegalStateException("Release during WorldSeam offscreen draw");
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
