package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

/**
 * Read-only, optional Sable 2.x integration. Looks at REAL registered
 * ServerSubLevel global bounds to identify hulls which physically straddle
 * WorldSeam's configured stack plane.
 *
 * CRITICAL: no transfer, clone, fake rigid body, impulse approximation or
 * modification of the live Sable solver happens here. A proper unified
 * native Rapier chart solver remains an independent required feature.
 */
public final class SeamSableObserver {
    private static final Logger LOG=LogUtils.getLogger();
    private static final int INTERVAL_TICKS=10;
    private static Method containerGetter,allSubLevels,boundingBox,uniqueId,isRemoved;
    private static Method minX,minY,minZ,maxX,maxY,maxZ;
    private static boolean attempted,failed;
    private static String problem="";
    private static boolean previouslyStraddling;
    private static Snapshot latest=new Snapshot(false,false,0,0,0,"",0);

    public record Snapshot(boolean installed,boolean compatible,
                           int liveBodies,int straddlingBodies,
                           long lastTick,String sampleShip,int candidateLinks) {
        public boolean physicsBridgeActive() {return false;}
    }

    private SeamSableObserver() {}

    public static Snapshot snapshot(){return latest;}
    public static String problem(){return problem;}

    public static void tick(MinecraftServer server,long tick,boolean worldSeamEnabled) {
        if(tick%INTERVAL_TICKS!=0)return;
        SeamShipResidency.beginTick(server);
        boolean installed=ModList.get().isLoaded("sable");
        if(!worldSeamEnabled || !installed) {
            SeamShipResidency.clear();
            latest=new Snapshot(installed,false,0,0,tick,"",0);
            return;
        }
        try {
            if(!initialize()) {
                SeamShipResidency.clear();
                latest=new Snapshot(true,false,0,0,tick,"",0);
                return;
            }
            int bodies=0,charted=0;
            var visited=new HashSet<UUID>();
            var straddled=new HashSet<UUID>();
            String sample="";
            for(ServerLevel home:server.getAllLevels()) {
                // Invoke Sable's server-only holder bridge directly. Reflection
                // over SubLevelContainer.getMethods() resolves its CLIENT
                // overload and triggers Forge invalid-dist crash on dedicated
                // servers. The live ServerLevel mixin is already a holder.
                Object container=containerGetter.invoke(home);
                if(container==null)continue;
                Object all=allSubLevels.invoke(container);
                if(!(all instanceof Collection<?> sublevels))continue;
                for(Object sub:sublevels) {
                    if(sub==null || Boolean.TRUE.equals(isRemoved.invoke(sub)))
                        continue;
                    if(!(uniqueId.invoke(sub) instanceof UUID id))
                        continue;
                    if(!visited.add(id))continue;
                    bodies++;
                    Object box=boundingBox.invoke(sub);
                    if(box==null)continue;
                    double x1=num(minX,box),y1=num(minY,box),z1=num(minZ,box);
                    double x2=num(maxX,box),y2=num(maxY,box),z2=num(maxZ,box);
                    if(!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(z1) ||
                       !Double.isFinite(x2) || !Double.isFinite(y2) || !Double.isFinite(z2) ||
                       x1>=x2 || y1>=y2 || z1>=z2)continue;
                    AABB hull=new AABB(x1,y1,z1,x2,y2,z2);
                    for(SeamGraph.Link link:SeamStackRuntime.from(server,
                            home.dimension().location().toString())) {
                        ServerLevel dest=server.getLevel(SeamServer.dimensionKey(link.target()));
                        if(dest==null)continue;
                        double homeY=SeamGraph.boundaryY(link.exit(),
                            SeamStackRuntime.bottom(home),SeamStackRuntime.top(home));
                        // A physics ship can reach the plane without a
                        // passenger nearby. Request the REAL neighbor's
                        // terrain *ahead* of collision time, via an ordinary
                        // bounded region ticket, without synchronous loads.
                        if(SeamShipResidency.nearing(hull,homeY))
                            SeamShipResidency.touch(id,dest,hull,homeY,tick);
                        if(hull.minY>=homeY || hull.maxY<=homeY)continue;
                        double destY=SeamGraph.boundaryY(link.entry(),
                            SeamStackRuntime.bottom(dest),SeamStackRuntime.top(dest));
                        SeamShipChartBounds.Projection projection=
                            SeamShipChartBounds.project(hull,link,homeY,destY);
                        if(projection.straddles()) {
                            if(straddled.add(id) && sample.isEmpty())
                                sample=id.toString()+" "+link.target();
                            charted++;
                        }
                    }
                }
            }
            SeamShipResidency.prune(tick);
            latest=new Snapshot(true,true,bodies,straddled.size(),tick,sample,charted);
            if(!straddled.isEmpty() && !previouslyStraddling)
                LOG.warn("WORLDSEAM_SABLE_STRADDLE_DETECTED: {} real ships touching two dimension charts. NO unified Rapier contact bridge is installed; do NOT claim physics continuity. sample={}",
                    straddled.size(),sample);
            previouslyStraddling=!straddled.isEmpty();
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {
            failed=true;
            problem=ex.getClass().getSimpleName()+": "+ex.getMessage();
            LOG.warn("WORLDSEAM_SABLE_READ_ONLY_PROBE_UNAVAILABLE: Sable API adapter unavailable; no changes to ships were attempted",ex);
            SeamShipResidency.clear();
            latest=new Snapshot(true,false,0,0,tick,"",0);
        }
    }

    private static boolean initialize() {
        if(attempted)return !failed;
        attempted=true;
        try {
            Class<?> holder=Class.forName(
                "dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
            Class<?> serverContainer=Class.forName(
                "dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class<?> sub=Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> bounds=Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3dc");
            // getDeclaredMethod only! getMethod on SubLevelContainer or a
            // subclass scans inherited public static client overloads and
            // tries loading ClientLevel on DEDICATED_SERVER (FML crash).
            containerGetter=holder.getDeclaredMethod("sable$getPlotContainer");
            allSubLevels=serverContainer.getDeclaredMethod("getAllSubLevels");
            boundingBox=sub.getDeclaredMethod("boundingBox");
            uniqueId=sub.getDeclaredMethod("getUniqueId");
            isRemoved=sub.getDeclaredMethod("isRemoved");
            minX=bounds.getMethod("minX");minY=bounds.getMethod("minY");
            minZ=bounds.getMethod("minZ");maxX=bounds.getMethod("maxX");
            maxY=bounds.getMethod("maxY");maxZ=bounds.getMethod("maxZ");
            LOG.info("WORLDSEAM_SABLE_PROBE_READY: resolved real Sable SubLevelContainer and global hull API");
            return true;
        } catch(ReflectiveOperationException | LinkageError ex) {
            failed=true;
            problem=ex.getClass().getSimpleName()+": "+ex.getMessage();
            LOG.warn("WORLDSEAM_SABLE_PROBE_UNSUPPORTED: Sable runtime does not match expected 2.x API. No physics fallback or ship teleport is permitted",ex);
            return false;
        }
    }

    private static double num(Method method,Object o) throws ReflectiveOperationException {
        return ((Number)method.invoke(o)).doubleValue();
    }
}
