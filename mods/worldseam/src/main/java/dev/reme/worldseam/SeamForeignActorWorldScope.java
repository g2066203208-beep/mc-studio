package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import dev.reme.worldseam.mixin.SeamGameRendererAccess;
import dev.reme.worldseam.mixin.SeamMinecraftAccess;
import dev.reme.worldseam.mixin.SeamParticleEngineAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.slf4j.Logger;

/**
 * Exception-safe native context for rendering actual foreign actors.
 *
 * A secondary LevelRenderer has its own native RenderBuffers. Switching only
 * Minecraft.level and levelRenderer leaves Minecraft/GameRenderer drawing into
 * the SOURCE world's unfinished entity buffers. Keep all four identities
 * consistent until the target draw has completed, then restore them even if
 * a modded BE/entity renderer throws.
 *
 * This retains the source camera because existing foreign actors are mapped
 * into source coordinates. It is NOT yet a full sky/fog/depth/offscreen
 * LevelRenderer pass or Sable sublevel composite.
 */
public final class SeamForeignActorWorldScope {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Scope NOOP=()->{};
    private static boolean inside;
    private static long entered,restored,failures;

    private SeamForeignActorWorldScope() {}

    public interface Scope extends AutoCloseable {
        @Override void close();
    }

    public static boolean active(){return inside;}
    public static long entered(){return entered;}
    public static long restored(){return restored;}
    public static long failures(){return failures;}

    public static Scope bind(ClientLevel foreign, Camera sourceCamera) {
        Minecraft mc=Minecraft.getInstance();
        LevelRenderer foreignRenderer=SeamClientWorldPool.rendererFor(foreign);
        RenderBuffers foreignBuffers=SeamClientWorldPool.renderBuffersFor(foreign);
        if(foreign==null || foreignRenderer==null || foreignBuffers==null ||
            mc.level==null || foreign==mc.level || sourceCamera==null)return NOOP;
        if(inside)throw new IllegalStateException("Nested WorldSeam foreign render context");
        ClientLevel originalWorld=mc.level;
        LevelRenderer originalRenderer=mc.levelRenderer;
        RenderBuffers originalMinecraftBuffers=mc.renderBuffers();
        SeamGameRendererAccess game=(SeamGameRendererAccess)mc.gameRenderer;
        RenderBuffers originalGameBuffers=game.worldseam$getRenderBuffers();
        SeamMinecraftAccess client=(SeamMinecraftAccess)mc;
        SeamParticleEngineAccess particles=(SeamParticleEngineAccess)mc.particleEngine;
        ClientLevel originalParticleWorld=particles.worldseam$getLevel();
        inside=true;
        try {
            mc.level=foreign;
            client.worldseam$setLevelRenderer(foreignRenderer);
            client.worldseam$setRenderBuffers(foreignBuffers);
            game.worldseam$setRenderBuffers(foreignBuffers);
            mc.getBlockEntityRenderDispatcher().setLevel(foreign);
            // ParticleEngine.setLevel() clears particles; swap only its
            // dimension reference and restore it before vanilla resumes.
            particles.worldseam$setLevel(foreign);
            mc.getEntityRenderDispatcher().prepare(foreign,sourceCamera,null);
            // Verify the real Minecraft and GameRenderer now use the
            // secondary LevelRenderer's dedicated native buffer owner.
            if(mc.renderBuffers()!=foreignBuffers ||
               game.worldseam$getRenderBuffers()!=foreignBuffers)
                throw new IllegalStateException("Foreign native buffer scope not installed");
        } catch(RuntimeException | Error failure) {
            failures++;
            restore(mc,originalWorld,originalRenderer,originalParticleWorld,
                originalMinecraftBuffers,originalGameBuffers,
                sourceCamera,particles,client,game);
            throw failure;
        }
        entered++;
        return new Scope() {
            private boolean closed;
            @Override public void close() {
                if(closed)return;
                closed=true;
                try {
                    restore(mc,originalWorld,originalRenderer,
                        originalParticleWorld,originalMinecraftBuffers,
                        originalGameBuffers,sourceCamera,particles,client,game);
                    if(mc.renderBuffers()!=originalMinecraftBuffers ||
                       game.worldseam$getRenderBuffers()!=originalGameBuffers)
                        throw new IllegalStateException("Foreign native render buffers leaked");
                    restored++;
                    if(restored==1 || restored%120==0)
                        LOG.info("WORLDSEAM_FOREIGN_ACTOR_CONTEXT: entered={}, restored={}, failures={}, isolatedBuffers=true",
                            entered,restored,failures);
                } catch(RuntimeException | Error failure) {
                    failures++;
                    throw failure;
                }
            }
        };
    }

    private static void restore(Minecraft mc,ClientLevel originalWorld,
                                LevelRenderer originalRenderer,
                                ClientLevel originalParticleWorld,
                                RenderBuffers originalMinecraftBuffers,
                                RenderBuffers originalGameBuffers,
                                Camera camera,SeamParticleEngineAccess particles,
                                SeamMinecraftAccess client,
                                SeamGameRendererAccess game) {
        try {
            mc.level=originalWorld;
            client.worldseam$setLevelRenderer(originalRenderer);
        } finally {
            try {
                game.worldseam$setRenderBuffers(originalGameBuffers);
            } finally {
                try {
                    client.worldseam$setRenderBuffers(originalMinecraftBuffers);
                } finally {
                    try {
                        mc.getBlockEntityRenderDispatcher().setLevel(originalWorld);
                    } finally {
                        try {
                            particles.worldseam$setLevel(originalParticleWorld);
                        } finally {
                            try {
                                mc.getEntityRenderDispatcher().prepare(
                                    originalWorld,camera,mc.crosshairPickEntity);
                            } finally {
                                inside=false;
                            }
                        }
                    }
                }
            }
        }
    }
}
