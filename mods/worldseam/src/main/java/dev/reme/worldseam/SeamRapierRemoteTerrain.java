package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
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
import java.util.Comparator;
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
    private static final int SECTIONS_BELOW_EDGE = 4;
    private static final int WINDOW_RADIUS = 2;
    private static final int MAX_NATIVE_UPLOADS_PER_TICK = 2;
    // Unlike the visual prefetch, this ticket follows the AUTHORITATIVE
    // Sable hull even when no player is nearby. Without it, getChunkNow()
    // never becomes resident and the foreign Rapier scene has no terrain
    // contacts below the seam. Ordinary region tickets are asynchronous:
    // this path never calls getChunk() or forces synchronous generation.
    private static final int PHYSICS_TICKET_DISTANCE = WINDOW_RADIUS + 1;
    private static final TicketType<ChunkPos> PHYSICS_TICKET = TicketType.create(
        "worldseam_rapier_foreign_chart", Comparator.comparingLong(ChunkPos::toLong)
    );
    private static ServerLevel ticketLevel;
    private static ChunkPos ticketCenter;
    private static long ticketMoves;
    private static final int MAX_PENDING = (2*WINDOW_RADIUS+1)*(2*WINDOW_RADIUS+1)*SECTIONS_BELOW_EDGE;
    private record NativeSection(int cx,int cy,int cz) {}
    // A tested bounded FIFO retains both unloaded-chunk retries and edits
    // arriving while all native upload slots are occupied.
    private static final SeamBoundedRetryQueue<NativeSection> UPLOAD_QUEUE =
        new SeamBoundedRetryQueue<>(MAX_PENDING);
    private static final Set<NativeSection> uploaded = new HashSet<>();

    private static MinecraftServer owner;
    private static ServerLevel source,target;
    private static int offsetY,edgeSection,windowBottomSection,windowTopSection,centerX,centerZ;
    private static long sourceNativeScene;
    private static Method nativeSceneLookup,nativeAdd,nativeRemove,bake,colliderHandle;
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

    /** Maintain exactly one bounded foreign-world ticket for the real hull. */
    private static void updateForeignTicket(ServerLevel destination, int cx, int cz) {
        ChunkPos next = new ChunkPos(cx, cz);
        if (ticketLevel == destination && next.equals(ticketCenter)) return;
        releaseForeignTicket();
        destination.getChunkSource().addRegionTicket(
            PHYSICS_TICKET, next, PHYSICS_TICKET_DISTANCE, next);
        ticketLevel = destination;
        ticketCenter = next;
        ticketMoves++;
        if (ticketMoves <= 4 || ticketMoves % 40 == 0)
            LOG.info("WORLDSEAM_RAPIER_FOREIGN_TICKET: dimension={} center={},{} radius={} moves={} asyncOnly=true",
                destination.dimension().location(), cx, cz, WINDOW_RADIUS, ticketMoves);
    }

    private static void releaseForeignTicket() {
        if (ticketLevel != null && ticketCenter != null)
            ticketLevel.getChunkSource().removeRegionTicket(
                PHYSICS_TICKET, ticketCenter, PHYSICS_TICKET_DISTANCE, ticketCenter);
        ticketLevel = null;
        ticketCenter = null;
    }

    /**
     * Native scene pointers are leases, not permanent identities. Sable may
     * destroy/recreate a Rapier scene while this MinecraftServer is alive.
     * Never dereference an old scene handle through addChunk/removeChunk:
     * a dangling JNI pointer can crash the JVM rather than throw Java.
     *
     * This check is necessary, not sufficient, for a fused Atlas solver:
     * Sable's published API does not expose a monotonically increasing
     * scene generation, so an allocator reusing the same pointer cannot
     * be detected here. Keep this prototype opt-in.
     */
    private static boolean nativeSceneStillLive() {
        if (!active || source == null || sourceNativeScene == 0 ||
            nativeSceneLookup == null) return false;
        try {
            Object value = nativeSceneLookup.invoke(null, source);
            return value instanceof Number &&
                SeamRapierSceneLease.isSameLiveScene(sourceNativeScene,
                    ((Number) value).longValue());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            LOG.warn("WORLDSEAM_RAPIER_SCENE_LOOKUP_FAILED: skipping unsafe native access", ex);
            return false;
        }
    }

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
        if(cy>windowTopSection || cy<windowBottomSection)return;
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
        if(cy>windowTopSection || cy<windowBottomSection ||
           Math.abs((long)cx-centerX)>WINDOW_RADIUS ||
           Math.abs((long)cz-centerZ)>WINDOW_RADIUS)return;
        enqueue(new NativeSection(cx,cy,cz));
    }

    private static void enqueue(NativeSection s) {
        UPLOAD_QUEUE.enqueue(s);
    }

    public static void tick(ServerTickEvent.Post event) {
        if(!enabled()) {
            if(active || ticketLevel!=null)close();
            return;
        }
        MinecraftServer server=event.getServer();
        if(owner!=null && owner!=server) {
            // The old MinecraftServer already disposed its native Rapier
            // scenes; NEVER call nativeRemove using the stale handle.
            resetOnlyJavaState();
            faulted=false;
        }
        owner=server;
        // Detect Sable's in-server native scene replacement BEFORE any
        // stale removeChunk/addChunk operation or section-window cleanup.
        // The dead native scene owns its old terrain; only Java references
        // and our asynchronous region ticket may be released safely.
        if(active && !nativeSceneStillLive()) {
            LOG.error("WORLDSEAM_RAPIER_SCENE_REPLACED: oldScene={} nativeAccessSkipped=true; experiment will reinitialize only on a live scene",
                sourceNativeScene);
            releaseForeignTicket();
            resetOnlyJavaState();
            return;
        }
        if(faulted) {
            // Experimental JNI mismatch may have left chart proxies.
            // A failed native mutation is NOT a safe physics mode.
            if(active || ticketLevel!=null)close();
            return;
        }
        if(!SeamSettings.get(server).enabled()) {
            if(active || ticketLevel!=null)close();
            return;
        }
        ServerLevel upper=server.getLevel(Level.OVERWORLD);
        ServerLevel lower=server.getLevel(Level.NETHER);
        if(upper==null || lower==null) {
            if(active || ticketLevel!=null)close();
            return;
        }
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
            faulted=true;
            if(active || ticketLevel!=null)close();
            return;
        }
        // The physics window must follow the real authoritative Sable hull,
        // not a spectator. A ship without nearby players still requires the
        // SAME native Rapier scene to retain both dimensions\u0027 terrain.
        // This is a bounded sliding chart window, NOT fused Atlas physics.
        long now=upper.getGameTime();
        var shipHull=SeamSableObserver.nativeRapierSourceHull(upper,lower,now);
        var shipAnchor=SeamRapierAnchor.fromHull(shipHull);
        var viewer=server.getPlayerList().getPlayers().stream()
            .filter(p->p.serverLevel()==upper && Math.abs(p.getY()-highEdge)<48)
            .findFirst().orElse(null);
        if(shipAnchor==null && viewer==null) {
            if(active || ticketLevel!=null)close();
            return;
        }
        int cx=shipAnchor!=null?shipAnchor.chunkX():
            Math.floorDiv(viewer.blockPosition().getX(),16);
        int cz=shipAnchor!=null?shipAnchor.chunkZ():
            Math.floorDiv(viewer.blockPosition().getZ(),16);
        if(active && (source!=upper||target!=lower||offsetY!=shift))close();
        // Pin the real destination terrain independently of the player.
        // A moving ship must not lose its native collision terrain merely
        // because its pilot/spectator has moved to another dimension.
        // Acquire after any old-scene cleanup, so a stale ticket is never
        // retained across dimension/scene changes.
        try {
            updateForeignTicket(lower,cx,cz);
        } catch(RuntimeException problem) {
            faulted=true;
            LOG.error("WORLDSEAM_RAPIER_EXPERIMENT_TICKET_FAIL",problem);
            close();
            return;
        }
        if(!active) {
            try {
                initialize(upper,lower,shift,cx,cz,
                    shipHull==null?Double.NaN:shipHull.minY);
            } catch(ReflectiveOperationException | LinkageError problem) {
                faulted=true;
                LOG.error("WORLDSEAM_RAPIER_EXPERIMENT_FAIL_NATIVE_INIT: cannot attach released Sable native scene",problem);
                close();
                return;
            }
        }
        // Follow the real Sable hull DOWN the foreign chart; the previous
        // fixed Nether Y96..127 slice left empty native space below Y=-96.
        var vertical=SeamRapierChartWindow.select(
            shipHull==null?Double.NaN:shipHull.minY,shift,
            lower.getMinSection(),edgeSection,SECTIONS_BELOW_EDGE);
        if(cx!=centerX || cz!=centerZ ||
           vertical.bottom()!=windowBottomSection ||
           vertical.top()!=windowTopSection) {
            centerX=cx;centerZ=cz;
            windowBottomSection=vertical.bottom();
            windowTopSection=vertical.top();
            // Drop queued old-window work BEFORE rescheduling. Otherwise
            // delayed chunks could reintroduce ghost colliders out of view.
            UPLOAD_QUEUE.clear();
            // Old foreign chunks must not remain collidable after moving.
            for(var i=uploaded.iterator();i.hasNext();) {
                var s=i.next();
                if(Math.abs(s.cx()-cx)>WINDOW_RADIUS ||
                   Math.abs(s.cz()-cz)>WINDOW_RADIUS ||
                   s.cy()<windowBottomSection || s.cy()>windowTopSection) {
                    try {remove(s);} catch(ReflectiveOperationException ex) {
                        LOG.error("WORLDSEAM_RAPIER_EXPERIMENT_REMOVE_FAIL",ex);
                        faulted=true;return;
                    }
                    i.remove();
                }
            }
            enqueueWindow(shipHull==null?Double.NaN:shipHull.minY);
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
        int polls=UPLOAD_QUEUE.size();
        int uploads=0;
        for(int i=0;i<polls && uploads<MAX_NATIVE_UPLOADS_PER_TICK;i++) {
            var s=UPLOAD_QUEUE.poll();
            if(s==null)break;
            LevelChunk chunk=target.getChunkSource().getChunkNow(s.cx(),s.cz());
            if(chunk==null){ UPLOAD_QUEUE.defer(s);continue; }
            UPLOAD_QUEUE.complete(s);
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
                                   int cx,int cz,double hullMinY)
        throws ReflectiveOperationException {
        Class<?> nativeBridge=Class.forName("dev.ryanhcode.sable.physics.impl.rapier.Rapier3D");
        Method scene=nativeBridge.getMethod("getSceneHandle",ServerLevel.class);
        long sceneHandle=((Number)scene.invoke(null,upper)).longValue();
        if(sceneHandle==0)
            throw new ReflectiveOperationException("Released Sable has no live Overworld Rapier scene");
        nativeSceneLookup=scene;
        sourceNativeScene=sceneHandle;
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
        var initial=SeamRapierChartWindow.select(Double.NaN,shift,
            lower.getMinSection(),edgeSection,SECTIONS_BELOW_EDGE);
        windowBottomSection=initial.bottom();
        windowTopSection=initial.top();
        centerX=cx;centerZ=cz;
        active=true;
        enqueueWindow(hullMinY);
        LOG.warn("WORLDSEAM_RAPIER_EXPERIMENT_ACTIVE: sceneHandle={} source={} target={} nativeShiftY={} firstRemoteSection={} oneBody=true, contact/torque NOT YET VERIFIED",
            sourceNativeScene,upper.dimension().location(),
            lower.dimension().location(),shift,edgeSection);
    }

    private static void enqueueWindow(double hullMinY) {
        // Priority is based on the actual authoritative hull, not a viewer.
        // With two native uploads per tick, the old corner-first order
        // delayed the central collision column by dozens of ticks. Schedule
        // the center and the hull's vertical section before distant terrain.
        // The real target chunk must STILL be resident before JNI upload.
        var ordered = SeamRapierChartWindow.prioritize(centerX,centerZ,
            windowBottomSection,windowTopSection,hullMinY,offsetY);
        for(var s:ordered)
            enqueue(new NativeSection(s.cx(),s.cy(),s.cz()));
        if(!ordered.isEmpty())
            LOG.info("WORLDSEAM_RAPIER_UPLOAD_PRIORITY: hullMinY={} firstRealTargetSection={} queued={} authoritativeScene={} fusedAtlas=false",
                hullMinY,ordered.get(0),ordered.size(),sourceNativeScene);
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
        long low=((long)windowBottomSection)*16L;
        long high=((long)windowTopSection+1L)*16L;
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
        UPLOAD_QUEUE.clear();uploaded.clear();COLLIDER_CACHE.clear();
        // On server replacement the old server has already disposed its
        // ticket manager; discard Java references WITHOUT touching it.
        ticketLevel=null;ticketCenter=null;
        source=null;target=null;destinationBakery=null;
        windowBottomSection=windowTopSection=0;
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
        try {
            if(active && nativeRemove!=null && nativeSceneStillLive()) {
                for(var s:uploaded)try {remove(s);}
                    catch(Exception ex){LOG.error("WORLDSEAM_RAPIER_EXPERIMENT_CLEANUP_FAIL",ex);}
            }
        } finally {
            // No leaked tickets after disabling WorldSeam, losing the hull,
            // failing native initialization or replacing the active chart.
            try { releaseForeignTicket(); }
            finally { resetOnlyJavaState(); }
        }
    }
    /** Actual JNI Rapier velocity for the same authoritative body ID.
     * No "contact" flag is inferred from this raw native measurement. */
    public record Motion(double vx,double vy,double vz,
                         double wx,double wy,double wz) {}

    public static Motion motion(int nativeBodyID) throws ReflectiveOperationException {
        if(!active || nativeLinearVelocity==null || nativeAngularVelocity==null)
            throw new IllegalStateException("Native projected scene not active");
        // The native motion reader must obey the SAME scene lease as
        // addChunk/removeChunk. A Sable level reload can dispose this pointer
        // between the server tick and a telemetry/flight observer callback.
        // Calling getLinearVelocity on a freed scene is a JVM-level UAF, not
        // a recoverable Java exception. A pointer-generation API is still
        // required upstream to rule out address reuse.
        if(!nativeSceneStillLive())
            throw new IllegalStateException("Native Rapier scene was disposed or replaced");
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
    public static boolean foreignTicketActive(){return ticketLevel!=null;}
    public static long foreignTicketMoves(){return ticketMoves;}
    public static long uploadedSections(){return totalNativeUploads;}
    public static long remoteColliderCells(){return totalRemoteSolidCells;}
}
