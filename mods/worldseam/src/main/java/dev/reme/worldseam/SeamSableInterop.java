package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EXPERIMENTAL optional whole-ship seam crossing for Sable + AeroPortals.
 *
 * No Sable/Portal classes load in the default standalone WorldSeam instance.
 * On opt-in, delegate the entire Sable sub-level, rider bindings, connected
 * structures, kinetic state and crash journal to AeroPortals' existing public
 * dispatchResult path, rather than cloning vanilla Entities or serialized
 * chunks ourselves. This is a single atomic transfer of a rigid assembly,
 * NOT continuous two-sided Atlas physics or a valid Sable render backend.
 */
public final class SeamSableInterop {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Map<UUID,Observed> observed=new HashMap<>();
    private static Api api;
    private static boolean attempted;
    private static String detection="Not checked";
    private static MinecraftServer currentServer;
    private static long successfulTransfers,rejectedTransfers;
    private static final int RETRY_DELAY_TICKS=40;
    private static final long STALE_TICKS=600;
    private static final int MAX_SUBLEVELS_PER_LEVEL=256;

    private static final class Observed {
        String dimension;
        double previousY;
        long lastSeen;
        long retryAfter;
        Observed(String dimension,double previousY,long tick) {
            this.dimension=dimension;
            this.previousY=previousY;
            this.lastSeen=tick;
        }
    }

    private record Api(Method getContainer, Method getAllSubLevels,
                       Method destinationOf, Method dispatchResult,
                       Class<?> serverSubLevel) {}

    private SeamSableInterop() {}

    public static String status() {
        if(!ModList.get().isLoaded("sable"))
            return "unavailable: Sable missing";
        if(!ModList.get().isLoaded("aeroportals"))
            return "unavailable: AeroPortals missing";
        resolve();
        return detection+" | moved="+successfulTransfers+" | refused="+rejectedTransfers;
    }

    private static void resolve() {
        if(attempted)return;
        attempted=true;
        if(!ModList.get().isLoaded("sable") ||
           !ModList.get().isLoaded("aeroportals")) {
            detection="unavailable: Sable and AeroPortals are required";
            return;
        }
        try {
            Class<?> container=Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Class<?> sub=Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
            Class<?> destination=Class.forName("com.breakinblocks.aeroportals.api.PortalDestination");
            Class<?> teleport=Class.forName("com.breakinblocks.aeroportals.portal.PortalTeleport");
            api=new Api(container.getMethod("getContainer",ServerLevel.class),
                container.getMethod("getAllSubLevels"),
                destination.getMethod("of",ServerLevel.class,Vec3.class,boolean.class,String.class),
                teleport.getMethod("dispatchResult",ServerLevel.class,sub,destination),
                sub);
            detection="compatible API discovered (experimental)";
        } catch(ReflectiveOperationException | LinkageError ex) {
            api=null;
            detection="unsupported AeroPortals/Sable API: "+ex.getClass().getSimpleName();
            LOG.error("WorldSeam cannot link physics bridge; never move physical ships via vanilla teleport",ex);
        }
    }

    /** Server-thread only, called from WorldSeam's own ServerTickEvent.Post.
     *  Off by default: do not modify someone's existing Sable world on load. */
    public static void tick(MinecraftServer server,long tick,boolean enabled) {
        if(server!=currentServer) {
            currentServer=server;
            observed.clear();
            api=null;
            attempted=false;
            detection="Not checked";
        }
        if(!enabled) {
            observed.clear();
            return;
        }
        resolve();
        if(api==null)return;
        if(tick%2==0) {
            for(ServerLevel source:server.getAllLevels()) {
                List<?> ships=loadedShips(source);
                if(ships==null)continue;
                int count=0;
                for(Object ship:ships) {
                    if(++count>MAX_SUBLEVELS_PER_LEVEL)break;
                    if(!api.serverSubLevel().isInstance(ship))continue;
                    consider(server,source,ship,tick);
                }
            }
        }
        if(tick%200==0)
            observed.entrySet().removeIf(e->tick-e.getValue().lastSeen>STALE_TICKS);
    }

    private static List<?> loadedShips(ServerLevel source) {
        try {
            Object container=api.getContainer().invoke(null,source);
            if(container==null)return null;
            Object all=api.getAllSubLevels().invoke(container);
            // The authoritative container may be changed by a successful
            // crossing. Iterate a snapshot, never its mutable live list.
            return all instanceof List<?> list ? new ArrayList<>(list) : null;
        } catch(ReflectiveOperationException | RuntimeException ex) {
            LOG.warn("WorldSeam cannot query Sable sub-level container in {}",source.dimension().location(),ex);
            return null;
        }
    }

    private static Object invoke(Object owner,String method)
        throws ReflectiveOperationException {
        return owner.getClass().getMethod(method).invoke(owner);
    }
    private static double component(Object owner,String method)
        throws ReflectiveOperationException {
        return ((Number)invoke(owner,method)).doubleValue();
    }
    private static SeamShipCrossing.Bounds bounds(Object ship)
        throws ReflectiveOperationException {
        Object box=invoke(ship,"boundingBox");
        return new SeamShipCrossing.Bounds(
            component(box,"minX"),component(box,"minY"),component(box,"minZ"),
            component(box,"maxX"),component(box,"maxY"),component(box,"maxZ"));
    }

    private static void consider(MinecraftServer server,ServerLevel source,
                                 Object ship,long tick) {
        try {
            if(Boolean.TRUE.equals(invoke(ship,"isRemoved")))return;
            UUID id=(UUID)invoke(ship,"getUniqueId");
            Object pose=invoke(ship,"logicalPose");
            Object point=invoke(pose,"position");
            double x=component(point,"x"),y=component(point,"y"),z=component(point,"z");
            String src=source.dimension().location().toString();
            Observed sample=observed.get(id);
            if(sample==null || !src.equals(sample.dimension)) {
                observed.put(id,new Observed(src,y,tick));
                return;
            }
            sample.lastSeen=tick;
            if(tick<sample.retryAfter)return;
            for(SeamGraph.Link link:SeamStackRuntime.from(server,src)) {
                double sourceBoundary=SeamGraph.boundaryY(link.exit(),
                    SeamStackRuntime.bottom(source),SeamStackRuntime.top(source));
                if(Math.abs(y-sourceBoundary)>32 && Math.abs(sample.previousY-sourceBoundary)>32)
                    continue;
                ServerLevel target=server.getLevel(SeamServer.dimensionKey(link.target()));
                if(target==null)continue;
                double targetBoundary=SeamGraph.boundaryY(link.entry(),
                    SeamStackRuntime.bottom(target),SeamStackRuntime.top(target));
                double vy=y-sample.previousY;
                SeamShipCrossing.Crossing crossing=SeamShipCrossing.crossing(link.exit(),
                    sample.previousY,y,vy,sourceBoundary,targetBoundary);
                if(crossing==null)continue;
                var dest=new Vec3(x,crossing.destinationY(),z);
                var area=bounds(ship).shiftedY(targetBoundary-sourceBoundary);
                BlockPos min=BlockPos.containing(area.minX(),area.minY(),area.minZ());
                BlockPos max=BlockPos.containing(
                    Math.nextDown(area.maxX()),Math.nextDown(area.maxY()),
                    Math.nextDown(area.maxZ()));
                if(!target.getWorldBorder().isWithinBounds(min) ||
                   !target.getWorldBorder().isWithinBounds(max) ||
                   !SeamShipCrossing.loadedFootprint(area,
                       target.getMinBuildHeight(),target.getMaxBuildHeight(),
                       target::hasChunk)) {
                    sample.retryAfter=tick+RETRY_DELAY_TICKS;
                    rejectedTransfers++;
                    return;
                }
                // Actual relocation is entirely owned by AeroPortals: it
                // checks connected groups, carriers, riders and rollback.
                // We don't use Entity.changeDimension nor write Sable plots.
                Object request=api.destinationOf().invoke(null,target,dest,
                    true,"worldseam_vertical");
                boolean moved=Boolean.TRUE.equals(api.dispatchResult().invoke(
                    null,source,ship,request));
                if(moved) {
                    successfulTransfers++;
                    observed.remove(id);
                    LOG.info("WORLDSEAM_SABLE_ATOMIC_TRANSFER: ship={} {} -> {} y={} (not progressive dual-side physics)",
                        id,src,link.target(),dest.y());
                } else {
                    rejectedTransfers++;
                    sample.retryAfter=tick+RETRY_DELAY_TICKS;
                    LOG.warn("WORLDSEAM_SABLE_TRANSFER_DECLINED: ship={} {} -> {} ; retaining source physics",
                        id,src,link.target());
                }
                return;
            }
            sample.previousY=y;
        } catch(InvocationTargetException ex) {
            // Let AeroPortals' crash-safe journal/rollback own failures. A
            // failed plugin call is never retried every game tick.
            LOG.error("WORLDSEAM_SABLE_TRANSFER_FAILED: source ship remains owner until AeroPortals recovery",ex.getCause());
            rejectedTransfers++;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {
            LOG.error("WORLDSEAM_SABLE_API_ERROR: incompatible physics bridge; disabling for this session",ex);
            detection="disabled after Sable API runtime error";
            api=null;
            rejectedTransfers++;
        }
    }
}
