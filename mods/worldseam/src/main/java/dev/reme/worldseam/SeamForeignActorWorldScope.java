package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import dev.reme.worldseam.mixin.SeamMinecraftAccess;
import dev.reme.worldseam.mixin.SeamParticleEngineAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.slf4j.Logger;

/**
 * Narrow, exception-safe native context for rendering actual foreign actors.
 * It deliberately keeps the source camera/projection because the existing
 * BE/entity pass translates foreign coordinates into source coordinates.
 * NOT a full second LevelRenderer render, sky, transparency or framebuffer.
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
        if(foreign==null || foreignRenderer==null || mc.level==null ||
            foreign==mc.level || sourceCamera==null)return NOOP;
        if(inside)throw new IllegalStateException("Nested WorldSeam foreign render context");
        ClientLevel originalWorld=mc.level;
        LevelRenderer originalRenderer=mc.levelRenderer;
        SeamParticleEngineAccess particles=(SeamParticleEngineAccess)mc.particleEngine;
        ClientLevel originalParticleWorld=particles.worldseam$getLevel();
        inside=true;
        try {
            mc.level=foreign;
            ((SeamMinecraftAccess)mc).worldseam$setLevelRenderer(foreignRenderer);
            mc.getBlockEntityRenderDispatcher().setLevel(foreign);
            // ParticleEngine.setLevel() clears particles. Swap its actual
            // level field instead, then restore it before vanilla resumes.
            particles.worldseam$setLevel(foreign);
            mc.getEntityRenderDispatcher().prepare(foreign,sourceCamera,null);
        } catch(RuntimeException | Error failure) {
            failures++;
            restore(mc,originalWorld,originalRenderer,originalParticleWorld,
                sourceCamera,particles);
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
                        originalParticleWorld,sourceCamera,particles);
                    restored++;
                    if(restored==1 || restored%120==0)
                        LOG.info("WORLDSEAM_FOREIGN_ACTOR_CONTEXT: entered={}, restored={}, failures={}",
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
                                Camera camera,SeamParticleEngineAccess particles) {
        try {
            mc.level=originalWorld;
            ((SeamMinecraftAccess)mc).worldseam$setLevelRenderer(originalRenderer);
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
