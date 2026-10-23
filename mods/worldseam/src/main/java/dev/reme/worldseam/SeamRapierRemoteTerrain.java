package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * EXPERIMENTAL REAL NATIVE COLLISION BRIDGE for aligned, translation-only
 * vertical seams. Disabled in normal gameplay pending real contact/torque
 * evidence. Never moves/clones a Sable body, invokes no teleport and creates
 * no fake procedural colliders. Samples actual loaded destination blocks;
 * bakes them with released Sable's native RapierVoxelColliderBakery; uploads
 * their voxels to EMPTY coordinates outside the source world's logical Y
 * range in the source world's ONE authoritative Rapier scene.
 *
 * This is not IPSable Atlas and cannot handle rotated portals, multiple
 * overlapping charts, Sable sublevel ownership, passengers or live rendered
 * client sublevels. It is a bounded native-contact prototype for the fixed
 * vertical chart translation only. NEVER claim completed cross-dim flight
 * from this bridge without measured native contact impulse + torque.
 */
public final class SeamRapierRemoteTerrain {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int SECTIONS_BELOW_EDGE = 2;
    private static final int WINDOW_RADIUS = 2;
    private static final int MAX_NATIVE_UPLOADS_PER_TICK = 1;
    private static final int MAX_PENDING = (2*WINDOW_RADIUS+1)*(2*WINDOW_RADIUS+1)*SECTIONS_BELOW_EDGE;
    private record NativeSection(int cx,int cy,int cz) {}
    private static final ArrayDeque<NativeSection> pending = new ArrayDeque<>();
    private static final Set<NativeSection> queued = new HashSet<>();
    private static final Set<NativeSection> uploaded = new HashSet<>();

    private static MinecraftServer owner;
    private static ServerLevel source,target;
    private static int offsetY,edgeSection,centerX,centerZ;
    private static long sourceNativeScene;
    private static Method nativeAdd,nativeRemove,bake,colliderHandle;
    private static Object destinationBakery;
    private static byte exposedVoxelState;
    private static final Map<BlockState,Integer> COLLIDER_CACHE=new IdentityHashMap<>();
    private static boolean active,faulted;
    private static long totalNativeUploads,totalRemoteSolidCells;

    private SeamRapierRemoteTerrain() {}

    /** Optional experiment only: launched against published Sable jar. */
    public static boolean enabled() {
        return Boolean.getBoolean("worldseam.experimentalRemoteRapier");
    }

    /** No call into Sable or Minecraft chunks from a load callback. */
    public static void markBlockChanged(ServerLevel level,BlockPos p) {
        if(!active || level!=target)return;
        int cy=Math.floorDiv(p.getY(),16);
        if(cy>edgeSection || cy<edgeSection-SECTIONS_BELOW_EDGE+1)return;
        int cx=Math.floorDiv(p.getX(),16),cz=Math.floorDiv(p.getZ(),16);
        if(Math.abs(cx-centerX)>WINDOW_RADIUS ||
           Math.abs(cz-centerZ)>WINDOW_RADIUS)return;
        enqueue(new NativeSection(cx,cy,cz));
    }

    private static void enqueue(NativeSection s) {
        if(queued.contains(s))return;
        if(pending.size()>=MAX_PENDING)return; // bounded; leave old native slice intact
        queued.add(s);
        pending.offer(s);
    }

    public static void tick(ServerTickEvent.Post event) {
        if(!enabled())return;
        MinecraftServer server=event.getServer();
        if(owner!=null && owner!=server) {
            // The old MinecraftServer already disposed its native Rapier
            // scenes; NEVER call nativeRemove using the stale handle.
            resetOnlyJavaState();
            faulted=false;
        }
        owner=server;
        if(faulted)return;
        if(!SeamSettings.get(server).enabled()) {
            if(active)close();
            return;
        }
        ServerLevel upper=server.getLevel(Level.OVERWORLD);
        ServerLevel lower=server.getLevel(Level.NETHER);
        if(upper==null || lower==null)return;
        // Only the non-overlapping, translation-only OW-bottom/NETHER-top
        // chart has been wired for this experiment. Reject all others.
        int highEdge=SeamStackRuntime.bottom(upper);
        int lowEdge=SeamStackRuntime.top(lower);
        int shift;
        try {
            shift=Math.subtractExact(highEdge,lowEdge);
            SeamRapierChartMath.sectionShift(highEdge,lowEdge);
        } catch(IllegalArgumentException | ArithmeticException ex) {
            LOG.error("WORLDSEAM_RAPIER_EXPERIMENT_REJECT: unsupported seam map sourceY={} targetY={}",highEdge,lowEdge,ex);
            faulted=true;return;
        }
        var viewer=server.getPlayerList().getPlayers().stream()
            .filter(p->p.serverLevel()==upper && Math.abs(p.getY()-highEdge)<48)
            .findFirst().orElse(null);
        if(viewer==null) {
            if(active)close();
            return;
        }
        int cx=Math.floorDiv(viewer.blockPosition().getX(),16);
        int cz=Math.floorDiv(viewer.blockPosition().getZ(),16);
        if(active && (source!=upper||target!=lower||offsetY!=shift))close();
        if(!active) {
            try {
                initialize(upper,lower,shift,cx,cz);
            } catch(ReflectiveOperationException | LinkageError problem) {
                faulted=true;
                LOG.error("WORLDSEAM_RAPIER_EXPERIMENT_FAIL_NATIVE_INIT: cannot attach released Sable native scene",problem);
                return;
            }
        }
        if(cx!=centerX || cz!=centerZ) {
            centerX=cx;centerZ=cz;
            // Old foreign chunks must not remain collidable after moving.
            for(var i=uploaded.iterator();i.hasNext();) {
                var s=i.next();
                if(Math.abs(s.cx()-cx)>WINDOW_RADIUS ||
                   Math.abs(s.cz()-cz)>WINDOW_RADIUS) {
                    try {remove(s);} catch(ReflectiveOperationException ex) {
                        LOG.error("WORLDSEAM_RAPIER_EXPERIMENT_REMOVE_FAIL",ex);
                        faulted=true;return;
                    }
                    i.remove();
                }
            }
            enqueueWindow();
        }
        // The dimension's normal asynchronous tickets must already have
        // loaded these real chunks; NEVER call getChunk for missing data.
        for(var i=uploaded.iterator();i.hasNext();) {
            var s=i.next();
            if(target.getChunkSource().getChunkNow(s.cx(),s.cz())==null) {
                try {remove(s);} catch(ReflectiveOperationException ex) {
                    LOG.error("WORLDSEAM_RAPIER_EXPERIMENT_UNLOAD_FAIL",ex);
                    faulted=true;return;
                }
                i.remove();
            }
        }
        int polls=pending.size();
        int uploads=0;
        for(int i=0;i<polls && uploads<MAX_NATIVE_UPLOADS_PER_TICK;i++) {
            var s=pending.poll();
            if(s==null)break;
            LevelChunk chunk=target.getChunkSource().getChunkNow(s.cx(),s.cz());
            if(chunk==null){ pending.offer(s);continue; }
            queued.remove(s);
            try {
                upload(chunk,s);
            } catch(ReflectiveOperationException | RuntimeException ex) {
                faulted=true;
                LOG.error("WORLDSEAM_RAPIER_EXPERIMENT_UPLOAD_FAIL: refuses to claim dual-world contacts",ex);
                return;
            }
            uploaded.add(s);
            uploads++;
        }
    }

    private static void initialize(ServerLevel upper,ServerLevel lower,int shift,
                                   int cx,int cz)
        throws ReflectiveOperationException {
        Class<?> nativeBridge=Class.forName("dev.ryanhcode.sable.physics.impl.rapier.Rapier3D");
        Method scene=nativeBridge.getMethod("getSceneHandle",ServerLevel.class);
        sourceNativeScene=((Number)scene.invoke(null,upper)).longValue();
        nativeAdd=nativeBridge.getDeclaredMethod("addChunk",long.class,
            int.class,int.class,int.class,int[].class,boolean.class,int.class);
        nativeRemove=nativeBridge.getDeclaredMethod("removeChunk",long.class,
            int.class,int.class,int.class,boolean.class);
        nativeAdd.setAccessible(true);
        nativeRemove.setAccessible(true);
        Class<?> bakery=Class.forName(
            "dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery");
        Constructor<?> constructor=bakery.getConstructor(
            net.minecraft.world.level.BlockGetter.class);
        destinationBakery=constructor.newInstance(lower);
        bake=bakery.getMethod("getPhysicsDataForBlock",BlockState.class);
        Class<?> data=Class.forName(
            "dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData");
        colliderHandle=data.getMethod("handle");
        Class<?> neighborhood=Class.forName(
            "dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState");
        @SuppressWarnings({"unchecked","rawtypes"})
        Object corner=Enum.valueOf((Class)neighborhood,"CORNER");
        exposedVoxelState=((Number)neighborhood.getMethod("byteRepresentation")
            .invoke(corner)).byteValue();
        source=upper;target=lower;offsetY=shift;
        edgeSection=SeamRapierChartMath.topSection(SeamStackRuntime.top(lower));
        centerX=cx;centerZ=cz;
        active=true;
        enqueueWindow();
        LOG.warn("WORLDSEAM_RAPIER_EXPERIMENT_ACTIVE: sceneHandle={} source={} target={} nativeShiftY={} firstRemoteSection={} oneBody=true, contact/torque NOT YET VERIFIED",
            sourceNativeScene,upper.dimension().location(),
            lower.dimension().location(),shift,edgeSection);
    }

    private static void enqueueWindow() {
        for(int cy=edgeSection;cy>edgeSection-SECTIONS_BELOW_EDGE;cy--)
            for(int dz=-WINDOW_RADIUS;dz<=WINDOW_RADIUS;dz++)
                for(int dx=-WINDOW_RADIUS;dx<=WINDOW_RADIUS;dx++)
                    enqueue(new NativeSection(centerX+dx,cy,centerZ+dz));
    }

    private static void upload(LevelChunk chunk,NativeSection s)
        throws ReflectiveOperationException {
        int sectionIndex=s.cy()-target.getMinSection();
        if(sectionIndex<0 || sectionIndex>=chunk.getSections().length)
            throw new IllegalStateException("Foreign chart section outside native target");
        LevelChunkSection section=chunk.getSection(sectionIndex);
        int[] packed=new int[4096];
        long filled=0;
        for(int by=0;by<16;by++)for(int bz=0;bz<16;bz++)for(int bx=0;bx<16;bx++) {
            BlockState state=section.getBlockState(bx,by,bz);
            if(state.isAir())continue;
            Integer value=COLLIDER_CACHE.get(state);
            if(value==null) {
                Object collider=bake.invoke(destinationBakery,state);
                int id=collider==null?-1:((Number)colliderHandle.invoke(collider)).intValue();
                // Sable's native format: (colliderID+1) << 16 | VoxelNeighborhoodState.
                // 0 is air. CORNER is conservative and preserves custom box shape,
                // but must be replaced by exact neighbor classification before
                // declaring full physics acceptance.
                if(id>=65535)throw new IllegalStateException("Native collider handle overflow");
                value=id<0?0:((id+1)<<16)|(exposedVoxelState&0xFF);
                COLLIDER_CACHE.put(state,value);
            }
            packed[bx+(bz<<4)+(by<<8)]=value;
            if(value!=0)filled++;
        }
        int mappedCy=SeamRapierChartMath.mapSection(s.cy(),
            SeamStackRuntime.bottom(source),SeamStackRuntime.top(target));
        // Only project terrain into currently unoccupied slice of the
        // source native Rapier scene; never overwrite source-world voxels.
        if(!SeamRapierChartMath.entirelyBelowSource(mappedCy,
                SeamStackRuntime.bottom(source)))
            throw new IllegalStateException("Remote native terrain overlaps source dimension");
        nativeAdd.invoke(null,sourceNativeScene,s.cx(),mappedCy,s.cz(),
            packed,true,-1);
        totalNativeUploads++;
        totalRemoteSolidCells+=filled;
        if(totalNativeUploads<=8 || totalNativeUploads%16==0)
            LOG.info("WORLDSEAM_RAPIER_EXPERIMENT_NATIVE_TERRAIN: targetSection={} mappedSection={} sourceScene={} realColliderCells={} totalUploaded={} activeTerrainSections={} (ONE native solver, ZERO ship clones; contact impulses NOT VERIFIED)",
                s.cy(),mappedCy,sourceNativeScene,filled,totalNativeUploads,
                uploaded.size()+1);
    }

    private static void remove(NativeSection s) throws ReflectiveOperationException {
        if(nativeRemove!=null)
            nativeRemove.invoke(null,sourceNativeScene,
                s.cx(),SeamRapierChartMath.mapSection(s.cy(),
                    SeamStackRuntime.bottom(source),
                    SeamStackRuntime.top(target)),s.cz(),true);
    }

    private static void resetOnlyJavaState() {
        pending.clear();queued.clear();uploaded.clear();COLLIDER_CACHE.clear();
        source=null;target=null;destinationBakery=null;
        active=false;
        totalNativeUploads=totalRemoteSolidCells=0;
    }

    public static void close() {
        if(active && nativeRemove!=null) {
            for(var s:uploaded)try {remove(s);}
                catch(Exception ex){LOG.error("WORLDSEAM_RAPIER_EXPERIMENT_CLEANUP_FAIL",ex);}
        }
        resetOnlyJavaState();
    }
    public static boolean active(){return active;}
    public static long uploadedSections(){return totalNativeUploads;}
    public static long remoteColliderCells(){return totalRemoteSolidCells;}
}
