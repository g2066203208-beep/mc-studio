package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

/**
 * Draw REAL entities already tracked by the foreign ClientLevel using the
 * native 1.21.1 EntityRenderDispatcher, not synthetic models or duplicates.
 * Scoped prepare/restore and isolated buffers are mandatory: neither the
 * active world's dispatcher nor its pending vertex buffers may be stolen.
 *
 * Limited transition pass only. It does not supply missing remote entity
 * packets, Sable sublevel tracking, seam-plane geometry clipping, sky or
 * full dual-LevelRenderer composition. No success claim from render counts.
 */
public final class SeamRemoteEntities {
    private static final Logger LOG=LogUtils.getLogger();
    private static final SeamRenderBuffers OWNED=new SeamRenderBuffers();
    private static final int MAX_ENTITIES=96;
    private static final double MAX_DISTANCE_SQ=96.0*96.0;
    private static int lastEligible,lastRendered,lastErrors;
    private static long framesWithRealEntities,totalErrors;
    private static boolean warned;

    private SeamRemoteEntities() {}

    public static void draw(RenderLevelStageEvent event,ClientLevel remote,
                            int sourceBoundary,int targetBoundary,
                            SeamGraph.EdgeSide targetEntry) {
        lastEligible=lastRendered=lastErrors=0;
        Minecraft mc=Minecraft.getInstance();
        if(event.getPoseStack()==null||mc.level==null||mc.player==null||
           remote==null||remote==mc.level||targetEntry==null)return;
        var dispatcher=mc.getEntityRenderDispatcher();
        var camera=event.getCamera();
        Vec3 cameraPos=camera.getPosition();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long shift=(long)sourceBoundary-targetBoundary;
        boolean switched=false,usedBuffers=false;
        try {
            for(Entity entity:remote.entitiesForRendering()) {
                if(entity==null||entity.isRemoved()||entity.level()!=remote||
                   entity.getUUID().equals(mc.player.getUUID()))continue;
                AABB nativeBox=entity.getBoundingBox();
                if(!SeamRemoteEntityVisibility.intersectsNativeSlice(
                        nativeBox.minY,nativeBox.maxY,targetBoundary,
                        targetEntry==SeamGraph.EdgeSide.TOP))continue;
                AABB projected=nativeBox.move(0.0,(double)shift,0.0);
                if(!event.getFrustum().isVisible(projected))continue;
                double x=entity.xOld+(entity.getX()-entity.xOld)*partial;
                double y=entity.yOld+(entity.getY()-entity.yOld)*partial+(double)shift;
                double z=entity.zOld+(entity.getZ()-entity.zOld)*partial;
                double dx=x-cameraPos.x,dy=y-cameraPos.y,dz=z-cameraPos.z;
                if(dx*dx+dy*dy+dz*dz>MAX_DISTANCE_SQ)continue;
                lastEligible++;
                if(!switched) {
                    dispatcher.prepare(remote,camera,null);
                    switched=true;
                }
                try {
                    usedBuffers=true;
                    int light=dispatcher.getPackedLightCoords(entity,partial);
                    float yaw=entity.yRotO+(entity.getYRot()-entity.yRotO)*partial;
                    dispatcher.render(entity,dx,dy,dz,yaw,partial,
                        event.getPoseStack(),OWNED.source(),light);
                    lastRendered++;
                } catch(RuntimeException failure) {
                    lastErrors++;
                    totalErrors++;
                    if(!warned) {
                        warned=true;
                        LOG.error("WorldSeam native remote entity renderer failed; "
                            +"this does NOT establish seamless dual-world entities",failure);
                    }
                }
                if(lastEligible>=MAX_ENTITIES)break;
            }
        } finally {
            if(lastRendered>0)framesWithRealEntities++;
            try {
                if(usedBuffers)OWNED.flush();
            } finally {
                if(switched)dispatcher.prepare(mc.level,camera,mc.crosshairPickEntity);
            }
        }
    }

    public static int lastEligible(){return lastEligible;}
    public static int lastRendered(){return lastRendered;}
    public static long framesWithRealEntities(){return framesWithRealEntities;}
    public static long totalErrors(){return totalErrors;}
    public static void reset() {
        OWNED.release();
        lastEligible=lastRendered=lastErrors=0;
        framesWithRealEntities=totalErrors=0;
        warned=false;
    }
}
