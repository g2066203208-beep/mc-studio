package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

/**
 * Draws actual target ClientLevel block entities with Minecraft's own block
 * entity renderers. This adds a missing live visual layer to WorldSeam's
 * temporary remote-chunk mesh path; it is NOT full native dual-LevelRenderer
 * composition (sky, entities, Sable sublevels and transparent clip remain).
 *
 * No fake tile meshes, no cross-dimension BlockEntity copies, and no
 * synchronous chunks. All temporary BufferSource and dispatcher changes are
 * returned to the owning active world in a finally block.
 */
public final class SeamRemoteBlockEntities {
    private static final Logger LOG = LogUtils.getLogger();
    private static final SeamRenderBuffers OWNED = new SeamRenderBuffers();
    private static final int WINDOW_RADIUS_CHUNKS = 2;
    private static final int MAX_RENDERED_PER_FRAME = 96;
    private static final double MAX_DISTANCE_SQ = 96.0 * 96.0;
    private static int lastRendered;
    private static int lastEligible;
    private static int lastFailures;
    private static boolean warned;

    private SeamRemoteBlockEntities() {}

    /**
     * Render from the loaded *real* target chunks through a source-camera
     * coordinate transform. The owning BlockEntity stays in its native level.
     */
    public static void draw(RenderLevelStageEvent event, ClientLevel remote,
                            int sourceBoundary, int targetBoundary,
                            SeamGraph.EdgeSide targetEntry) {
        lastRendered = lastEligible = lastFailures = 0;
        Minecraft mc = Minecraft.getInstance();
        if (event.getPoseStack() == null || mc.level == null ||
            mc.player == null || remote == null || targetEntry == null) return;
        var cache = remote.getChunkSource();
        var dispatcher = mc.getBlockEntityRenderDispatcher();
        Vec3 camera = event.getCamera().getPosition();
        int cx = Math.floorDiv(mc.player.blockPosition().getX(),16);
        int cz = Math.floorDiv(mc.player.blockPosition().getZ(),16);
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean switched = false;
        boolean usedBuffers = false;
        try {
            outer:
            for (int dz = -WINDOW_RADIUS_CHUNKS; dz <= WINDOW_RADIUS_CHUNKS; dz++)
                for (int dx = -WINDOW_RADIUS_CHUNKS; dx <= WINDOW_RADIUS_CHUNKS; dx++) {
                    if(!cache.hasChunk(cx+dx,cz+dz)) continue;
                    var chunk = cache.getChunk(cx+dx,cz+dz,false);
                    if(chunk == null) continue;
                    for (BlockEntity tile : chunk.getBlockEntities().values()) {
                        if(tile == null || tile.isRemoved() || tile.getLevel() != remote) continue;
                        BlockPos p = tile.getBlockPos();
                        // Logical seam bounds take precedence over vanilla
                        // Nether Y=256 chunk storage: never show ghost roof.
                        if (targetEntry == SeamGraph.EdgeSide.TOP
                            ? p.getY() >= targetBoundary : p.getY() < targetBoundary)
                            continue;
                        int mappedY = SeamSectionLayout.mappedBlockY(
                            p.getY(),sourceBoundary,targetBoundary);
                        double xx = p.getX()-camera.x;
                        double yy = mappedY-camera.y;
                        double zz = p.getZ()-camera.z;
                        if(xx*xx+yy*yy+zz*zz > MAX_DISTANCE_SQ)continue;
                        if(!event.getFrustum().isVisible(new AABB(
                                p.getX(),mappedY,p.getZ(),
                                p.getX()+1,mappedY+1,p.getZ()+1)))continue;
                        if(dispatcher.getRenderer(tile) == null) continue;
                        lastEligible++;
                        if(!switched) {
                            dispatcher.setLevel(remote);
                            switched=true;
                        }
                        event.getPoseStack().pushPose();
                        try {
                            event.getPoseStack().translate(xx,yy,zz);
                            usedBuffers=true;
                            dispatcher.render(tile,partial,event.getPoseStack(),
                                OWNED.source());
                            lastRendered++;
                        } catch (RuntimeException failure) {
                            // A foreign BER may rely on an active-world
                            // singleton. Never leave the live renderer
                            // attached to the remote level on failure.
                            lastFailures++;
                            if(!warned) {
                                warned=true;
                                LOG.error("WorldSeam remote native block-entity renderer failed; "
                                    +"this BE is not validated for joined rendering",failure);
                            }
                        } finally {
                            event.getPoseStack().popPose();
                        }
                        if(lastRendered+lastFailures>=MAX_RENDERED_PER_FRAME)
                            break outer;
                    }
                }
        } finally {
            try {
                if(usedBuffers)OWNED.flush();
            } finally {
                if(switched)dispatcher.setLevel(mc.level);
            }
        }
    }

    public static int lastRendered(){return lastRendered;}
    public static int lastEligible(){return lastEligible;}
    public static int lastFailures(){return lastFailures;}

    public static void reset() {
        OWNED.release();
        lastRendered=lastEligible=lastFailures=0;
        warned=false;
    }
}
