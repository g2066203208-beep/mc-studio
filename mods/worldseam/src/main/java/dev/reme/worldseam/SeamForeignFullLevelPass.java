package dev.reme.worldseam;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.reme.worldseam.mixin.SeamGameRendererAccess;
import dev.reme.worldseam.mixin.SeamMinecraftAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

/**
 * Opt-in native SECOND LevelRenderer pipeline. Unlike the temporary VBO proxy,
 * this invokes the target ClientLevel's actual LevelRenderer.renderLevel,
 * including its native section discovery, weather, entities and BE stages.
 *
 * It runs after the source LevelRenderer has finished and uses an independent
 * depth-backed FBO. Minecraft's main target and GameRenderer camera are
 * temporarily redirected because vanilla nested stages may bind them again.
 * This is NOT final seamlessness: fog/global mod state, Sable sublevels and
 * fused Rapier contacts require further integration and real GUI validation.
 */
public final class SeamForeignFullLevelPass {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean active;
    private static boolean disabled;
    private static long completed;
    private static long failures;
    private static long clippedFrames;
    private static final boolean ENABLE_CLIP = Boolean.getBoolean("worldseam.experimentalNativeSeamClip");
    private static Matrix4f lastProjection;

    private SeamForeignFullLevelPass() {}

    public static boolean active() { return active; }
    public static long completed() { return completed; }
    public static long failures() { return failures; }
    public static Matrix4f projectionForComposite(Matrix4f fallback) {
        return lastProjection == null ? fallback : new Matrix4f(lastProjection);
    }

    private static final class MappedCamera extends Camera {
        void relocate(Camera source, int sourceBoundary, int targetBoundary) {
            Vec3 p = source.getPosition();
            setPosition(new Vec3(p.x, p.y + (double) targetBoundary - sourceBoundary, p.z));
            setRotation(source.getYRot(), source.getXRot(), source.getRoll());
        }
    }

    private static void applyWorldFog(Minecraft mc, ClientLevel world,
                                      Camera camera, float partial) {
        FogRenderer.setupColor(camera, partial, world,
            mc.options.getEffectiveRenderDistance(),
            mc.gameRenderer.getDarkenWorldAmount(partial));
        float distance = Math.max(mc.gameRenderer.getRenderDistance(), 32.0F);
        boolean foggy = world.effects().isFoggyAt(
            Mth.floor(camera.getPosition().x), Mth.floor(camera.getPosition().y))
            || mc.gui.getBossOverlay().shouldCreateWorldFog();
        FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN,
            distance, foggy, partial);
        FogRenderer.levelFogColor();
    }

    public static boolean render(RenderLevelStageEvent event, ClientLevel foreign,
                                 int sourceBoundary, int targetBoundary,
                                 boolean remoteBelow) {
        Minecraft mc = Minecraft.getInstance();
        if (active || disabled || foreign == null || foreign == mc.level ||
            mc.level == null || event.getCamera() == null ||
            event.getCamera().getEntity() == null) return false;
        LevelRenderer renderer = SeamClientWorldPool.rendererFor(foreign);
        if (renderer == null || SeamClientWorldPool.renderBuffersFor(foreign) == null ||
            !SeamClientWorldPool.nativeForeignTickHealthy() ||
            SeamClientWorldPool.nativeForeignTicks() == 0)
            return false;

        lastProjection = null;
        Camera sourceCamera = event.getCamera();
        // The vanilla GameRenderer normally establishes the world's fog
        // color BEFORE LevelRenderer.renderLevel. A nested second-world
        // LevelRenderer must do that itself and restore the source fog after.
        ClientLevel sourceLevel = mc.level;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MappedCamera mapped = new MappedCamera();
        mapped.setup(foreign, sourceCamera.getEntity(), false, false, partial);
        mapped.relocate(sourceCamera, sourceBoundary, targetBoundary);
        float seamY=(float)(sourceBoundary-sourceCamera.getPosition().y);
        Matrix4f clipped=ENABLE_CLIP ? SeamNativeClipProjection.beyondSeam(
            event.getProjectionMatrix(),event.getModelViewMatrix(),seamY,remoteBelow) : null;
        // The native clip is not an optional visual optimization. Without it,
        // wrong-side terrain writes the foreign depth buffer BEFORE the final
        // compositor and permanently hides the valid far-side geometry. A
        // screen-space discard cannot recover that geometry. Fail closed for
        // this frame instead of silently rendering with vanilla projection.
        if (ENABLE_CLIP && clipped == null) {
            if (completed == 0 || completed % 120 == 0)
                LOG.warn("WORLDSEAM_NATIVE_CLIP_UNAVAILABLE: cameraSeamY={} below={} foreign={} nativeFrameSkipped=true finalAcceptance=false",
                    seamY, remoteBelow, foreign.dimension().location());
            return false;
        }
        Matrix4f nativeProjection=clipped==null?event.getProjectionMatrix():clipped;

        SeamMinecraftAccess client = (SeamMinecraftAccess) mc;
        SeamGameRendererAccess game = (SeamGameRendererAccess) mc.gameRenderer;
        active = true;
        try {
            // GameRenderer normally clears its real scene target to the
            // biome's fog color before LevelRenderer draws. Our nested pass
            // previously cleared transparent black instead. Dimensions with
            // SkyType.NONE (notably vanilla Nether) have NO native sky draw
            // to cover that black background, so legitimate uncovered rays
            // remained visibly black even when native geometry rendered.
            // Use the actual target FogRenderer's computed shader fog RGB,
            // not a hard-coded color, screenshot, or substitute terrain.
            // Keep alpha=1 only for no-sky dimensions; normal/End sky must
            // still be drawn by the target's real LevelRenderer.
            applyWorldFog(mc, foreign, mapped, partial);
            float[] nativeNoSkyFog = foreign.effects().skyType()
                == DimensionSpecialEffects.SkyType.NONE
                ? RenderSystem.getShaderFogColor().clone() : null;
            boolean drawn = SeamForeignOffscreenPass.drawFullLevel(mc, () -> {
                RenderTarget sourceTarget = mc.getMainRenderTarget();
                Camera originalGameCamera = mc.gameRenderer.getMainCamera();
                Matrix4f originalProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
                var originalSorting=RenderSystem.getVertexSorting();
                try (SeamRemoteLightmaps.Scope lightmap =
                         SeamRemoteLightmaps.bind(foreign);
                     SeamForeignActorWorldScope.Scope world =
                         SeamForeignActorWorldScope.bind(foreign, mapped, sourceCamera)) {
                    // Nested vanilla LevelRenderer passes can rebind the
                    // Minecraft main target; keep those writes in the real
                    // foreign FBO, never in the unfinished source frame.
                    client.worldseam$setMainRenderTarget(
                        SeamForeignOffscreenPass.currentTarget());
                    game.worldseam$setMainCamera(mapped);
                    RenderSystem.setProjectionMatrix(nativeProjection,originalSorting);
                    try {
                        // Sky is at effectively infinite depth, not a terrain
                        // fragment. Applying an oblique near-plane to vanilla's
                        // sky dome can cut out wedges even though the foreign
                        // ClientLevel has a valid native sky. Draw its ACTUAL
                        // LevelRenderer sky into this same foreign color/depth
                        // target with vanilla perspective BEFORE raster-clipped
                        // terrain. No screenshots, synthetic colors or geometry.
                        // The full LevelRenderer still owns all later stages.
                        if (clipped != null &&
                            foreign.effects().skyType() != DimensionSpecialEffects.SkyType.NONE) {
                            RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), originalSorting);
                            try {
                                renderer.renderSky(event.getModelViewMatrix(),
                                    event.getProjectionMatrix(), partial, mapped,
                                    false, () -> {});
                            } finally {
                                RenderSystem.setProjectionMatrix(nativeProjection, originalSorting);
                            }
                        }
                        // Target fog is already initialized before the FBO
                        // clear, and remains active throughout native rendering.
                        // GameRenderer normally initializes the culling frustum
                        // before entering LevelRenderer.renderLevel. We invoke
                        // LevelRenderer directly in a nested native pass, so
                        // prepare the TARGET renderer's own frustum explicitly.
                        // Without this, setupRender -> offsetFrustum receives
                        // a null matrix and the real GUI throws NPE.
                        // IMPORTANT: The oblique seam plane is a RASTER
                        // clip, not a safe camera-culling frustum. Vanilla
                        // LevelRenderer.offsetFrustum() repeatedly backs the
                        // frustum away until the camera cube is included.
                        // An oblique near plane can exclude that cube for
                        // EVERY offset, hanging the real render thread inside
                        // Frustum.offsetToFullyIncludeCameraCube() indefinitely.
                        // This was captured in the genuine Sable GUI thread
                        // stack on 2026-10-25. Use the vanilla perspective
                        // for finite section discovery, but keep the clipped
                        // projection for the actual GPU draw/depth composite.
                        // This may over-include sections; it cannot remove
                        // correct visible sections, and the raster seam plane
                        // still clips wrong-side fragments before depth writes.
                        renderer.prepareCullFrustum(mapped.getPosition(),
                            event.getModelViewMatrix(), event.getProjectionMatrix());
                        renderer.renderLevel(event.getPartialTick(), false, mapped,
                            mc.gameRenderer, mc.gameRenderer.lightTexture(),
                            event.getModelViewMatrix(), nativeProjection);
                    } finally {
                        RenderSystem.setProjectionMatrix(originalProjection,originalSorting);
                        game.worldseam$setMainCamera(originalGameCamera);
                        client.worldseam$setMainRenderTarget(sourceTarget);
                    }
                }
            }, nativeNoSkyFog);
            if (drawn) {
                lastProjection=new Matrix4f(nativeProjection);
                if(clipped!=null) {
                    clippedFrames++;
                    if(clippedFrames==1||clippedFrames%120==0)
                        LOG.info("WORLDSEAM_NATIVE_PREDEPTH_CLIP: frames={}, seamY={}, target={}, finalAcceptance=false",
                            clippedFrames,seamY,foreign.dimension().location());
                }
                completed++;
                if (completed == 1 || completed % 120 == 0)
                    LOG.info("WORLDSEAM_NATIVE_SECOND_LEVEL_RENDERED: passes={}, dimension={}, mappedCameraY={}, fullLevelRenderer=true, seamlessAcceptance=false",
                        completed, foreign.dimension().location(), mapped.getPosition().y);
            }
            return drawn;
        } catch (RuntimeException | Error failure) {
            failures++;
            disabled = true;
            LOG.error("WorldSeam experimental full secondary LevelRenderer failed; disabled to protect vanilla frame", failure);
            return false;
        } finally {
            // Also restore source fog when the FBO could not be allocated,
            // a nested native pass threw, or no native pixels were produced.
            // All world/lightmap scopes close before this outer finally.
            try {
                applyWorldFog(mc, sourceLevel, sourceCamera, partial);
            } finally {
                active = false;
            }
        }
    }
}
