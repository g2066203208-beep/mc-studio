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
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;
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
    // Native section edits/unloads cannot be silently discarded while the
    // bounded upload queue is full. Deferred work remains within the same
    // fixed 5x5x2 window and is drained without synchronous chunk loads.
    private static final Set<NativeSection> overflow = new HashSet<>();
    private static final Set<NativeSection> uploaded = new HashSet<>();

    private static MinecraftServer owner;
    private static ServerLevel source,target;
    private static int offsetY,edgeSection,centerX,centerZ;
    private static long sourceNativeScene;
    private static Method nativeAdd,nativeRemove,bake,colliderHandle;
    private static Method nativeLinearVelocity,nativeAngularVelocity;
    private static Object destinationBakery;
    private static byte exposedVoxelState;
    private static byte[] neighborhoodBytes;
    private static Method hasCollisionCallback;
    private static final Map<BlockState,Integer> COLLIDER_CACHE=new IdentityHashMap<>();
    private static boolean active,faulted;
    private static long totalNativeUploads,totalRemoteSolidCells;
    // Only the disposable CI world may request an OBSERVABLE real-Nether
    // witness block. This wraps the actual native block collider with a
    // pass-through Sable JNI callback, never a duplicate body or fake force.
    private static BlockPos witnessPos;
    private static ServerLevel witnessLevel;
    private static final AtomicLong nativeWitnessContacts=new AtomicLong();
    private static volatile double latestNativeImpactSpeed;
    private static int witnessColliderPacked;
    private static Object witnessCallback; // strong reference while JNI callback is registered

    private SeamRapierRemoteTerrain() {}

    /** CI-only real Nether block; the world must have actually placed it. */
    public static void registerNativeWitness(ServerLevel level,BlockPos pos) {
        if(!enabled())return;
        if(level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.OBSIDIAN)) {
            witnessLevel=level;
            witnessPos=pos.immutable();
            nativeWitnessContacts.set(0);
            latestNativeImpactSpeed=0;
            witnessColliderPacked=0;
            // Re-upload the exact native section if the witness is registered
            // after a section was already baked. Never alter other obsidian.
            if(active && level==target)
                enqueue(new NativeSection(Math.floorDiv(pos.getX(),16),
                    Math.floorDiv(pos.getY(),16),Math.floorDiv(pos.getZ(),16)));
        } else {
            throw new IllegalArgumentException(
                "Foreign Rapier witness must be a REAL Nether obsidian block");
        }
    }

    public static long witnessedNativeContacts(){return nativeWitnessContacts.get();}
    public static double lastNativeImpactSpeed(){return latestNativeImpactSpeed;}

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
        enqueueIfResident(cx,cy,cz);
        // Native neighborhood classification depends on both sides of a
        // section face. Re-bake a loaded adjacent section when the edit
        // touches that face, rather than leaving stale native contacts.
        if(Math.floorMod(p.getX(),16)==0)enqueueIfResident(cx-1,cy,cz);
        if(Math.floorMod(p.getX(),16)==15)enqueueIfResident(cx+1,cy,cz);
        if(Math.floorMod(p.getY(),16)==0)enqueueIfResident(cx,cy-1,cz);
        if(Math.floorMod(p.getY(),16)==15)enqueueIfResident(cx,cy+1,cz);
        if(Math.floorMod(p.getZ(),16)==0)enqueueIfResident(cx,cy,cz-1);
        if(Math.floorMod(p.getZ(),16)==15)enqueueIfResident(cx,cy,cz+1);
    }

    private static void enqueueIfResident(int cx,int cy,int cz) {
        if(cy>edgeSection || cy<edgeSection-SECTIONS_BELOW_EDGE+1 ||
           Math.abs((long)cx-centerX)>WINDOW_RADIUS ||
           Math.abs((long)cz-centerZ)>WINDOW_RADIUS)return;
        enqueue(new NativeSection(cx,cy,cz));
    }

    private static void enqueue(NativeSection s) {
        if(queued.contains(s))return;
        if(pending.size()>=MAX_PENDING) {
            overflow.add(s);
            return;
        }
        overflow.remove(s);
        queued.add(s);
        pending.offer(s);
    }

    private static void drainOverflow() {
        if(overflow.isEmpty() || pending.size()>=MAX_PENDING)return;
        for(var it=overflow.iterator();it.hasNext() && pending.size()<MAX_PENDING;) {
            NativeSection section=it.next();
            it.remove();
            if(queued.add(section))pending.offer(section);
        }
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
        if(faulted) {
            // Experimental JNI mismatch may have left chart proxies.
            // A failed native mutation is NOT a safe physics mode.
            if(active)close();
            return;
        }
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
        // The physics window must follow the real authoritative Sable hull,
        // not a spectator. A ship without nearby players still requires the
        // SAME native Rapier scene to retain both dimensions\u0027 terrain.
        // This is a bounded two-section prototype, NOT fused Atlas physics.
        long now=upper.getGameTime();
        var shipHull=SeamSableObserver.nativeRapierSourceHull(upper,lower,now);
        var shipAnchor=SeamRapierAnchor.fromHull(shipHull);
        var viewer=server.getPlayerList().getPlayers().stream()
            .filter(p->p.serverLevel()==upper && Math.abs(p.getY()-highEdge)<48)
            .findFirst().orElse(null);
        if(shipAnchor==null && viewer==null) {
            if(active)close();
            return;
        }
        int cx=shipAnchor!=null?shipAnchor.chunkX():
            Math.floorDiv(viewer.blockPosition().getX(),16);
        int cz=shipAnchor!=null?shipAnchor.chunkZ():
            Math.floorDiv(viewer.blockPosition().getZ(),16);
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
            // Drop queued old-window work BEFORE rescheduling. Otherwise
            // delayed chunks could reintroduce ghost colliders out of view.
            pending.clear();
            queued.clear();
            overflow.clear();
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
                // The native section has been removed, but the ship may
                // remain at this seam while the authoritative target chunk
                // streams out and back in. Without requeueing, this section
                // stays absent from Rapier forever until the ship moves to
                // another chunk and enqueueWindow() runs again.
                // Retry asynchronously: upload() still requires getChunkNow,
                // so this never forces a chunk load or invents a collider.
                enqueue(s);
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
        drainOverflow();
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
        nativeLinearVelocity=nativeBridge.getDeclaredMethod("getLinearVelocity",
            long.class,int.class,double[].class);
        nativeAngularVelocity=nativeBridge.getDeclaredMethod("getAngularVelocity",
            long.class,int.class,double[].class);
        nativeLinearVelocity.setAccessible(true);
        nativeAngularVelocity.setAccessible(true);
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
        neighborhoodBytes=new byte[SeamRapierVoxelNeighbors.Kind.values().length];
        Method representation=neighborhood.getMethod("byteRepresentation");
        for(var kind:SeamRapierVoxelNeighbors.Kind.values()) {
            @SuppressWarnings({"unchecked","rawtypes"})
            Object nativeKind=Enum.valueOf((Class)neighborhood,kind.name());
            neighborhoodBytes[kind.ordinal()]=((Number)representation
                .invoke(nativeKind)).byteValue();
        }
        exposedVoxelState=neighborhoodBytes[SeamRapierVoxelNeighbors.Kind.CORNER.ordinal()];
        hasCollisionCallback=Class.forName(
            "dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback")
            .getMethod("hasCallback",BlockState.class);
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
            int x=(s.cx()<<4)+bx,y=(s.cy()<<4)+by,z=(s.cz()<<4)+bz;
            int index=bx+(bz<<4)+(by<<8);
            // Only the ONE real Nether witness voxel may receive the JNI
            // observer. A BlockState-wide cache made every obsidian block
            // look like the witness, invalidating native-contact evidence.
            if(witnessLevel==target && witnessPos!=null &&
                state.is(net.minecraft.world.level.block.Blocks.OBSIDIAN) &&
                SeamRapierVoxelNeighbors.matchesWitness(x,y,z,
                    witnessPos.getX(),witnessPos.getY(),witnessPos.getZ())) {
                if(witnessColliderPacked==0)
                    witnessColliderPacked=createObservedObsidianCollider();
                packed[index]=witnessColliderPacked;
                filled++;
                continue;
            }
            // Cache collider handles, NOT their neighborhood bytes: those
            // depend on six real neighboring voxels and can change on edits.
            Integer colliderBase=COLLIDER_CACHE.get(state);
            if(colliderBase==null) {
                Object collider=bake.invoke(destinationBakery,state);
                int id=collider==null?-1:((Number)colliderHandle.invoke(collider)).intValue();
                if(id>=65535)throw new IllegalStateException("Native collider handle overflow");
                colliderBase=id<0?0:(id+1)<<16;
                COLLIDER_CACHE.put(state,colliderBase);
            }
            if(colliderBase==0)continue;
            var kind=classifyNativeVoxel(state,x,y,z);
            packed[index]=colliderBase|(neighborhoodBytes[kind.ordinal()]&0xFF);
            filled++;
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

    // Only loaded, projected native terrain is allowed to contribute
    // neighborhood support; never force-load a target chunk or use Nether
    // roof voxels above its configured logical top.
    private static boolean fullNativeNeighbor(int ox,int oy,int oz,
                                              int x,int y,int z) {
        // The target world can have this neighbor loaded while its native
        // Rapier section has not yet been projected (or has been evicted).
        // Never hide a source collider face based on non-native support.
        // Conservatively expose cross-section faces even if both sections
        // happen to be resident, so eviction/upload ordering cannot create
        // a transient invisible collision hole.
        if(!SeamRapierVoxelNeighbors.sameNativeSection(
                ox,oy,oz,x,y,z))return false;
        long low=((long)edgeSection-SECTIONS_BELOW_EDGE+1L)*16L;
        long high=((long)edgeSection+1L)*16L;
        if(y<low || y>=high || y<target.getMinBuildHeight() ||
           y>=target.getMaxBuildHeight())return false;
        LevelChunk chunk=target.getChunkSource().getChunkNow(
            Math.floorDiv(x,16),Math.floorDiv(z,16));
        if(chunk==null)return false;
        BlockPos p=new BlockPos(x,y,z);
        BlockState state=chunk.getBlockState(p);
        return !state.isAir() && state.isCollisionShapeFullBlock(target,p);
    }

    private static SeamRapierVoxelNeighbors.Kind classifyNativeVoxel(
        BlockState state,int x,int y,int z) throws ReflectiveOperationException {
        BlockPos p=new BlockPos(x,y,z);
        boolean full=state.isCollisionShapeFullBlock(target,p);
        // Sable treats liquid and callback-bearing blocks conservatively.
        // Kelp variants are likewise fluid-like despite not always liquid().
        boolean special=state.liquid() ||
            state.getBlock() instanceof net.minecraft.world.level.block.KelpBlock ||
            state.getBlock() instanceof net.minecraft.world.level.block.KelpPlantBlock ||
            Boolean.TRUE.equals(hasCollisionCallback.invoke(null,state));
        if(!full || special)return SeamRapierVoxelNeighbors.Kind.CORNER;
        return SeamRapierVoxelNeighbors.classify(true,true,false,
            fullNativeNeighbor(x,y,z,x-1,y,z),fullNativeNeighbor(x,y,z,x+1,y,z),
            fullNativeNeighbor(x,y,z,x,y-1,z),fullNativeNeighbor(x,y,z,x,y+1,z),
            fullNativeNeighbor(x,y,z,x,y,z-1),fullNativeNeighbor(x,y,z,x,y,z+1));
    }

    /**
     * Exact vanilla obsidian full-block shape + published Sable friction,
     * restitution and volume. Only the callback is added for CI evidence;
     * it always returns zero tangent motion, removes no collision and applies
     * NO force itself. JNI invokes it only if its native collision pair
     * actually reaches this foreign terrain witness.
     */
    private static int createObservedObsidianCollider()
        throws ReflectiveOperationException {
        if(witnessLevel==null || witnessPos==null)
            throw new IllegalStateException("No real foreign-world witness");
        BlockState state=witnessLevel.getBlockState(witnessPos);
        if(!state.isCollisionShapeFullBlock(witnessLevel,witnessPos))
            throw new IllegalStateException(
                "Witness no longer has real full-block collision shape");
        Class<?> interfaceType=Class.forName(
            "dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback");
        Class<?> result=Class.forName(
            "dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback$CollisionResult");
        Object noChange=result.getField("NONE").get(null);
        Object callback=Proxy.newProxyInstance(interfaceType.getClassLoader(),
            new Class<?>[]{interfaceType},(proxy,method,args)->{
                if(method.getName().equals("onCollision")) {
                    int wx=((Number)args[0]).intValue();
                    int wy=((Number)args[1]).intValue();
                    int wz=((Number)args[2]).intValue();
                    int mappedY=Math.addExact(witnessPos.getY(),offsetY);
                    if(wx==witnessPos.getX() && wz==witnessPos.getZ() &&
                       (wy==mappedY || wy==witnessPos.getY())) {
                        long hits=nativeWitnessContacts.incrementAndGet();
                        latestNativeImpactSpeed=((Number)args[9]).doubleValue();
                        if(hits<=8 || hits%50==0)
                            LOG.warn("WORLDSEAM_RAPIER_NATIVE_FOREIGN_CONTACT: actualNetherWitness={} solverCollisionPos={},{},{} nativeContactCount={} impactSpeed={} (ONE authoritative scene, callback never applies fake force)",
                                witnessPos,wx,wy,wz,hits,latestNativeImpactSpeed);
                    }
                    return new double[]{0.0,0.0,0.0,0.0};
                }
                if(method.getName().equals("sable$onCollision"))return noChange;
                if(method.getName().equals("toString"))return "WorldSeamNativeWitness("+witnessPos+")";
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==args[0];
                throw new UnsupportedOperationException(method.getName());
            });
        Class<?> props=Class.forName(
            "dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper");
        double friction=((Number)props.getMethod("getFriction",BlockState.class).invoke(null,state)).doubleValue();
        double volume=((Number)props.getMethod("getVolume",BlockState.class).invoke(null,state)).doubleValue();
        double restitution=((Number)props.getMethod("getRestitution",BlockState.class).invoke(null,state)).doubleValue();
        Class<?> nativeBridge=Class.forName("dev.ryanhcode.sable.physics.impl.rapier.Rapier3D");
        witnessCallback=callback;
        Object collider=nativeBridge.getMethod("createVoxelColliderEntry",
            double.class,double.class,double.class,boolean.class,interfaceType)
            .invoke(null,friction,volume,restitution,false,callback);
        int id=((Number)colliderHandle.invoke(collider)).intValue();
        nativeBridge.getMethod("addVoxelColliderBox",int.class,double[].class)
            .invoke(null,id,new double[]{0.0,0.0,0.0,1.0,1.0,1.0});
        LOG.info("WORLDSEAM_RAPIER_NATIVE_WITNESS_ARMED: realBlock={} mappedY={} nativeColliderID={} noClonedBody=true",
            witnessPos,Math.addExact(witnessPos.getY(),offsetY),id);
        return ((id+1)<<16)|(exposedVoxelState&0xFF);
    }

    private static void remove(NativeSection s) throws ReflectiveOperationException {
        if(nativeRemove!=null)
            nativeRemove.invoke(null,sourceNativeScene,
                s.cx(),SeamRapierChartMath.mapSection(s.cy(),
                    SeamStackRuntime.bottom(source),
                    SeamStackRuntime.top(target)),s.cz(),true);
    }

    private static void resetOnlyJavaState() {
        pending.clear();queued.clear();overflow.clear();uploaded.clear();COLLIDER_CACHE.clear();
        source=null;target=null;destinationBakery=null;
        active=false;
        totalNativeUploads=totalRemoteSolidCells=0;
        witnessColliderPacked=0;
        neighborhoodBytes=null;
        hasCollisionCallback=null;
        witnessCallback=null;
        witnessPos=null;
        witnessLevel=null;
        nativeWitnessContacts.set(0);
        latestNativeImpactSpeed=0;
    }

    public static void close() {
        if(active && nativeRemove!=null) {
            for(var s:uploaded)try {remove(s);}
                catch(Exception ex){LOG.error("WORLDSEAM_RAPIER_EXPERIMENT_CLEANUP_FAIL",ex);}
        }
        resetOnlyJavaState();
    }
    /** Actual JNI Rapier velocity for the same authoritative body ID.
     * No "contact" flag is inferred from this raw native measurement. */
    public record Motion(double vx,double vy,double vz,
                         double wx,double wy,double wz) {}

    public static Motion motion(int nativeBodyID) throws ReflectiveOperationException {
        if(!active || nativeLinearVelocity==null || nativeAngularVelocity==null)
            throw new IllegalStateException("Native projected scene not active");
        double[] linear=new double[3],angular=new double[3];
        nativeLinearVelocity.invoke(null,sourceNativeScene,nativeBodyID,linear);
        nativeAngularVelocity.invoke(null,sourceNativeScene,nativeBodyID,angular);
        for(double v:linear)if(!Double.isFinite(v))
            throw new IllegalStateException("Native Rapier returned nonfinite velocity");
        for(double v:angular)if(!Double.isFinite(v))
            throw new IllegalStateException("Native Rapier returned nonfinite angular velocity");
        return new Motion(linear[0],linear[1],linear[2],
                          angular[0],angular[1],angular[2]);
    }

    public static boolean active(){return active;}
    public static long uploadedSections(){return totalNativeUploads;}
    public static long remoteColliderCells(){return totalRemoteSolidCells;}
}
