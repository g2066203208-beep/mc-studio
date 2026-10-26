package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import dev.reme.worldseam.mixin.SeamGameRendererAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import org.slf4j.Logger;

/**
 * Owns a genuine LightTexture for the secondary ClientLevel. Vanilla's single
 * active lightmap is NOT valid for Nether/End sky and ambient illumination.
 *
 * This is one necessary piece of native dual-world rendering, NOT a second
 * LevelRenderer composite: sky/fog, transparency, depth, Sable sublevels and
 * renderer state still need their own scoped implementation.
 */
public final class SeamRemoteLightmaps {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Scope NOOP=()->{};
    private static ClientLevel owner;
    private static LightTexture foreign;
    private static long creations,binds,failures,releases;
    private static boolean warned;
    // Vanilla advances lightmap flicker at 20 TPS, not every render frame.
    // The secondary ClientLevel can render many times between its real ticks.
    private static final SeamLightmapTickCadence TICK_CADENCE = new SeamLightmapTickCadence();

    private SeamRemoteLightmaps() {}

    public interface Scope extends AutoCloseable {
        @Override void close();
    }

    /**
     * Prepare the real remote dimension's lightmap while temporarily using
     * its ClientLevel for the vanilla lighting calculation. The source
     * ClientLevel is restored BEFORE the caller tessellates joined geometry,
     * since that tessellation still needs both original levels. Only the
     * lightmap sampler is bound for the foreign draw, with strict restoration.
     */
    public static Scope bind(ClientLevel remote) {
        Minecraft mc=Minecraft.getInstance();
        if(remote==null || mc.level==null || remote==mc.level)
            return NOOP;
        if(owner!=remote)release();
        if(foreign==null) {
            foreign=new LightTexture(mc.gameRenderer,mc);
            owner=remote;
            creations++;
            LOG.info("WORLDSEAM_REMOTE_LIGHTMAP_CREATED: dimension={}",
                remote.dimension().location());
        }

        LightTexture active=mc.gameRenderer.lightTexture();
        ClientLevel source=mc.level;
        try {
            mc.level=remote;
            // Preserve vanilla 20 TPS lightmap flicker cadence even when
            // the target LevelRenderer draws at a different FPS. Still let
            // updateLightTexture process any pending brightness changes.
            if (TICK_CADENCE.shouldTick(remote, remote.getGameTime()))
                foreign.tick();
            foreign.updateLightTexture(0.0F);
        } catch(RuntimeException error) {
            report(error);
            return NOOP;
        } finally {
            mc.level=source;
        }

        SeamGameRendererAccess access=(SeamGameRendererAccess)mc.gameRenderer;
        try {
            access.worldseam$setLightTexture(foreign);
            foreign.turnOnLightLayer();
        } catch(RuntimeException error) {
            access.worldseam$setLightTexture(active);
            active.turnOnLightLayer();
            report(error);
            return NOOP;
        }
        binds++;
        LightTexture bound=foreign;
        return ()->{
            try {
                bound.turnOffLightLayer();
            } finally {
                access.worldseam$setLightTexture(active);
                active.turnOnLightLayer();
            }
        };
    }

    private static void report(RuntimeException error) {
        failures++;
        if(!warned) {
            warned=true;
            LOG.error("WorldSeam remote lightmap failed; using source lightmap. "
                +"No seamless lighting acceptance can be claimed",error);
        }
    }

    /** Called on handoff and secondary ClientLevel disposal, on client thread. */
    public static void release() {
        if(foreign!=null) {
            foreign.close();
            releases++;
        }
        foreign=null;
        owner=null;
        TICK_CADENCE.reset();
    }

    public static long creations(){return creations;}
    public static long boundFrames(){return binds;}
    public static long failures(){return failures;}
    public static long releases(){return releases;}
}
