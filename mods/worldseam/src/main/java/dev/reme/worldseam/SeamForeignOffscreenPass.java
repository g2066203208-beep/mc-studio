package dev.reme.worldseam;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
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
    private static long nativeSkyPasses;
    private static long fullLevelPasses;
    private static long translucentPasses;
    private static long actorPasses;
    private static boolean actorsSubmitted;
    private static boolean opaqueReady;
    private static boolean frameComplete;
    private static long restoreFailures;
    private static boolean active;

    private SeamForeignOffscreenPass() {}

    public static long opaquePasses() { return opaquePasses; }
    public static long nativeSkyPasses() { return nativeSkyPasses; }
    public static long fullLevelPasses() { return fullLevelPasses; }
    static RenderTarget currentTarget() {
        if (!active || target == null)
            throw new IllegalStateException("Foreign framebuffer is not bound");
        return target;
    }

    /** Draw the real target LevelRenderer, not the proxy section VBOs. */
    public static boolean drawFullLevel(Minecraft mc, Runnable foreignLevelDraw) {
        return drawFullLevel(mc, foreignLevelDraw, null);
    }

    /**
     * For dimensions with no native sky, clear to the genuine target biome
     * fog produced by FogRenderer, matching vanilla's no-sky background.
     * The depth remains 1.0: this is NOT geometry or a successful chunk
     * coverage signal. A sky-bearing dimension must use a transparent clear
     * and let its own LevelRenderer draw the real sky instead.
     */
    public static boolean drawFullLevel(Minecraft mc, Runnable foreignLevelDraw,
                                        float[] nativeNoSkyFog) {
        opaqueReady = false;
        frameComplete = false;
        actorsSubmitted = false;
        if (!drawScoped(mc, foreignLevelDraw, true, nativeNoSkyFog)) return false;
        fullLevelPasses++;
        frameComplete = true;
        return true;
    }
    public static long translucentPasses() { return translucentPasses; }
    public static long actorPasses() { return actorPasses; }
    public static boolean opaqueReady() { return opaqueReady; }
    public static boolean frameComplete() { return frameComplete; }
    public static int colorTextureId() { return target == null ? -1 : target.getColorTextureId(); }
    public static int depthTextureId() { return target == null ? -1 : target.getDepthTextureId(); }

    /** Each vanilla opaque stage starts a new frame. Never reuse a previous frame's depth. */
    public static void beginFrame() {
        RenderSystem.assertOnRenderThread();
        opaqueReady = false;
        frameComplete = false;
        actorsSubmitted = false;
    }
    public static long restoreFailures() { return restoreFailures; }
    public static boolean allocated() { return target != null; }

    /**
     * Call on the Minecraft render thread only. Save the *actually bound*
     * framebuffer, not just Minecraft's main target: Fabulous/shader passes
     * may be drawing to a different framebuffer at this stage.
     */
    public static void drawOpaque(Minecraft mc, Runnable nativeSkyDraw,
                                  Runnable foreignTerrainDraw) {
        opaqueReady = false;
        // Draw the genuine target LevelRenderer sky FIRST, into the SAME
        // independently depth-backed FBO, then real terrain over that sky.
        // Never synthesize a background color or blit incomplete pixels.
        if (!drawScoped(mc, () -> {
            nativeSkyDraw.run();
            foreignTerrainDraw.run();
        }, true)) return;
        nativeSkyPasses++;
        opaquePasses++;
        opaqueReady = true;
        if (opaquePasses == 1 || opaquePasses % 120 == 0)
            LOG.info("WORLDSEAM_NATIVE_OFFSCREEN_OPAQUE: passes={}, nativeSkyInvocations={}, colorTexture={}, depthTexture={}, size={}x{}, allocations={}, resizes={}, compositeReady=false",
                opaquePasses, nativeSkyPasses, target.getColorTextureId(),
                target.getDepthTextureId(), target.width, target.height,
                allocations, resizes);
    }

    /**
     * Render real target ClientLevel block entities and ordinary entities
     * into the SAME native color/depth attachments after opaque terrain and
     * before translucent terrain. The caller must scope the genuine foreign
     * LevelRenderer/RenderBuffers and flush its native actor buffers before
     * returning. No fake actor, copied level or full-screen blit is involved.
     *
     * This remains a bounded experimental pass, NOT a full secondary
     * LevelRenderer, Sable sublevel renderer or seamless compositor.
     */
    public static boolean drawActors(Minecraft mc, Runnable foreignActors) {
        RenderSystem.assertOnRenderThread();
        if (!opaqueReady || actorsSubmitted || target == null) return false;
        // Set before invoking third-party actor renderers so a reentrant
        // callback cannot submit actors twice to the same depth target.
        actorsSubmitted = true;
        if (!drawScoped(mc, foreignActors, false)) return false;
        actorPasses++;
        if (actorPasses == 1 || actorPasses % 120 == 0)
            LOG.info("WORLDSEAM_NATIVE_OFFSCREEN_ACTORS: passes={}, opaquePasses={}, colorTexture={}, depthTexture={}, sameTarget=true, compositeReady=false",
                actorPasses, opaquePasses, target.getColorTextureId(),
                target.getDepthTextureId());
        return true;
    }

    /**
     * Render actual foreign translucent VBOs into the SAME target as the
     * current frame's opaque geometry, retaining its native depth attachment.
     * This is not a source/target depth compositor: the target remains hidden.
     * Consuming the flag prevents accidentally blending across two frames.
     */
    public static boolean drawTranslucent(Minecraft mc, Runnable foreignDraw) {
        RenderSystem.assertOnRenderThread();
        if (!opaqueReady) return false;
        opaqueReady = false;
        if (target == null || !drawScoped(mc, foreignDraw, false)) return false;
        translucentPasses++;
        frameComplete = true;
        if (translucentPasses == 1 || translucentPasses % 120 == 0)
            LOG.info("WORLDSEAM_NATIVE_OFFSCREEN_TRANSLUCENT: passes={}, opaquePasses={}, colorTexture={}, depthTexture={}, sameTarget=true, depthPreserved=true, compositeReady=false",
                translucentPasses, opaquePasses, target.getColorTextureId(),
                target.getDepthTextureId());
        return true;
    }

    /**
     * Scoped FBO draw. Opaque clears color/depth once; translucent preserves
     * BOTH attachments. Never leave a foreign FBO or viewport bound when
     * vanilla, shader packs or another dimension resumes.
     */
    private static boolean drawScoped(Minecraft mc, Runnable foreignDraw,
                                      boolean clear) {
        return drawScoped(mc, foreignDraw, clear, null);
    }

    private static boolean drawScoped(Minecraft mc, Runnable foreignDraw,
                                      boolean clear, float[] nativeNoSkyFog) {
        RenderSystem.assertOnRenderThread();
        if (nativeNoSkyFog != null &&
            (nativeNoSkyFog.length < 3 ||
             !Float.isFinite(nativeNoSkyFog[0]) ||
             !Float.isFinite(nativeNoSkyFog[1]) ||
             !Float.isFinite(nativeNoSkyFog[2])))
            throw new IllegalArgumentException("Invalid native target biome fog RGB");
        if (active) throw new IllegalStateException("Nested WorldSeam offscreen pass");
        RenderTarget source = mc.getMainRenderTarget();
        if (source == null || source.width <= 0 || source.height <= 0) return false;
        // A resize between opaque and translucent invalidates the pairing;
        // never blend onto a resized/reallocated target with unrelated depth.
        if (!clear && (target == null || target.width != source.width ||
                       target.height != source.height)) return false;
        int oldFramebuffer = GlStateManager.getBoundFramebuffer();
        // A native post-process or shader mod may have distinct READ/DRAW
        // framebuffer bindings. GL_FRAMEBUFFER restoration alone destroys
        // that distinction and can corrupt the following source-world pass.
        int oldReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        // A nested vanilla renderer can inherit a scissor rectangle from the
        // source dimension or a post-process pass. glClear obeys SCISSOR_TEST:
        // leaving it enabled produces stale color/depth OUTSIDE that rectangle
        // even when the target is a freshly allocated real ClientLevel FBO.
        boolean oldScissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntBuffer oldScissorBox = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, oldScissorBox);
        // Minecraft's current render stage may disable depth or color writes.
        // glClear obeys these masks; a stale mask produces a foreign FBO with
        // uncleared depth/alpha and a visibly broken seam on later frames.
        boolean oldDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        // The nested vanilla LevelRenderer is a full independent scene, not
        // just an FBO draw. It can change depth/blend/cull state while the
        // source AFTER_LEVEL callback is still on the stack. Without restoring
        // these actual GL states, subsequent source-world and mod render
        // stages inherit foreign-world depth/transparent ordering.
        boolean oldDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean oldBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        // A nested LevelRenderer (or a modded translucent/BE stage) may
        // change the blend factors/equations even when it leaves BLEND
        // disabled. Restoring only glEnable(GL_BLEND) is insufficient:
        // the source world's later translucent stages can inherit foreign
        // blend math and produce visibly missing/incorrect pixels.
        int oldBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int oldBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int oldBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int oldBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int oldBlendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int oldBlendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        boolean oldCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        ByteBuffer oldColorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, oldColorMask);
        FloatBuffer oldClearColor = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, oldClearColor);
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int x = viewport.get(0), y = viewport.get(1);
        int width = viewport.get(2), height = viewport.get(3);
        active = true;
        try {
            if (target == null) {
                target = new TextureTarget(source.width, source.height, true, true);
                allocations++;
            } else if (clear && (target.width != source.width ||
                                target.height != source.height)) {
                target.resize(source.width, source.height, true);
                resizes++;
            }
            if (target.getColorTextureId() <= 0 || target.getDepthTextureId() <= 0)
                throw new IllegalStateException("Secondary native color/depth attachments missing");
            // The full target LevelRenderer owns its own viewport and geometry.
            // Do not inherit a source-world scissor clip while clearing or
            // rendering this independent native second-world target.
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (clear) {
                // Never carry a prior frame's native depth or alpha into a
                // second ClientLevel, regardless of the source render stage.
                GL11.glDepthMask(true);
                GL11.glColorMask(true, true, true, true);
                if (nativeNoSkyFog == null) {
                    target.setClearColor(0f, 0f, 0f, 0f);
                } else {
                    // This is the target ClientLevel's actual vanilla
                    // FogRenderer RGB. Do not paint fake chunks or change
                    // depth: missing geometry must remain measurable.
                    target.setClearColor(nativeNoSkyFog[0],
                        nativeNoSkyFog[1], nativeNoSkyFog[2], 1f);
                }
                target.clear(true);
            }
            target.bindWrite(true);
            foreignDraw.run();
            return true;
        } finally {
            try {
                // Restore real driver state rather than assuming that the
                // foreign LevelRenderer returned with the source's settings.
                // In particular, a disabled depth test after the second
                // world's sky/translucency must not leak into the next frame.
                if (oldDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
                else GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(oldDepthFunc);
                // Restore the actual source blend function/equation, not
                // merely the enabled bit. This applies to all full-native
                // sky, BE, entity, terrain and translucent callbacks.
                GL14.glBlendFuncSeparate(oldBlendSrcRgb, oldBlendDstRgb,
                    oldBlendSrcAlpha, oldBlendDstAlpha);
                GL20.glBlendEquationSeparate(oldBlendEquationRgb, oldBlendEquationAlpha);
                if (oldBlend) GL11.glEnable(GL11.GL_BLEND);
                else GL11.glDisable(GL11.GL_BLEND);
                if (oldCull) GL11.glEnable(GL11.GL_CULL_FACE);
                else GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDepthMask(oldDepthMask);
                GL11.glColorMask(oldColorMask.get(0) != 0,
                    oldColorMask.get(1) != 0, oldColorMask.get(2) != 0,
                    oldColorMask.get(3) != 0);
                GL11.glClearColor(oldClearColor.get(0), oldClearColor.get(1),
                    oldClearColor.get(2), oldClearColor.get(3));
                GL11.glScissor(oldScissorBox.get(0), oldScissorBox.get(1),
                    oldScissorBox.get(2), oldScissorBox.get(3));
                if (oldScissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
                else GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFramebuffer);
                if (oldReadFramebuffer != oldFramebuffer)
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldReadFramebuffer);
                if (oldDrawFramebuffer != oldFramebuffer)
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDrawFramebuffer);
                RenderSystem.viewport(x, y, width, height);
                if (GlStateManager.getBoundFramebuffer() != oldFramebuffer ||
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) != oldReadFramebuffer ||
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != oldDrawFramebuffer ||
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) != oldScissorEnabled ||
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST) != oldDepthTest ||
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC) != oldDepthFunc ||
                    GL11.glIsEnabled(GL11.GL_BLEND) != oldBlend ||
                    GL11.glIsEnabled(GL11.GL_CULL_FACE) != oldCull)
                    throw new IllegalStateException("Source READ/DRAW framebuffer or scissor state not restored");
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
        opaqueReady = false;
        frameComplete = false;
        actorsSubmitted = false;
        if (active) throw new IllegalStateException("Release during WorldSeam offscreen draw");
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
