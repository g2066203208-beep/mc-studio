package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import java.util.UUID;

/**
 * Actual integrated CLIENT + integrated SERVER long-running regression.
 * Opt-in only: -Dworldseam.e2e=true -Dworldseam.stress=true.
 *
 * Create a dedicated DISPOSABLE world using the NEW default dimension order
 * Overworld > Nether > End. Excavate BOTH genuine vertical seams, then
 * traverse Overworld > Nether > End > Nether > Overworld
 * SIX complete rounds (24 real server + client handoffs). No direct
 * teleport across dimensions is issued by the driver; it only positions
 * the player 3 blocks INSIDE the current world before testing normal seam
 * motion. Every pass must pass a ready handshake, arrive through the
 * real SeamServer game loop, stream far native chunks and spawn a real
 * client-visible ArmorStand in the destination level.
 */
public final class SeamLongRunE2E {
    private static final Logger LOG=LogUtils.getLogger();
    private static final int ROUNDS=6;
    private static final int PASSES=ROUNDS*4;
    private static final int WAIT_READY_TICKS=300;
    private static final int WAIT_CROSS_TICKS=130;
    private static final int ENTITY_WAIT_TICKS=110;
    private static final String END="minecraft:the_end";
    private static final String NETHER="minecraft:the_nether";
    private static final String OVERWORLD="minecraft:overworld";
    private static final String[] FROM={OVERWORLD,NETHER,END,NETHER};
    private static final String[] TO={NETHER,END,NETHER,OVERWORLD};
    private static final boolean[] UP={false,false,true,true};

    private static volatile boolean enabled;
    private static volatile boolean triggerSent;
    private static volatile boolean markerSpawned;
    private static volatile UUID expectedMarkerUuid;
    private static volatile double expectedMarkerX,expectedMarkerY,expectedMarkerZ;
    private static volatile boolean serverConfirmed;
    private static volatile boolean serverReady;
    private static volatile long stageArmedAfterServerTick;
    private static int stage;
    private static int pass;
    private static long stageTick;
    private static UUID player;
    private static int x,z;
    private static int lastClientHandoffs;
    private static long elapsedStart;
    private static long maxClientHandoffNanos;
    private static long maxUsedHeap;
    private static int maxNativeChunks;
    private static int maxBufferOwners;
    private static int maxGpuSections;
    private static long lastClientTickNs;
    private static long slowTickGaps250ms;
    private static long slowTickGaps1000ms;
    private static long worstClientTickGapMs;
    private static ArmorStand marker;

    private SeamLongRunE2E() {}

    public static void install() {
        if(Boolean.getBoolean("worldseam.e2e") &&
            Boolean.getBoolean("worldseam.stress"))
            NeoForge.EVENT_BUS.addListener(SeamLongRunE2E::tick);
    }

    public static void begin(MinecraftServer server,UUID id,int shaftX,int shaftZ) {
        if(!Boolean.getBoolean("worldseam.stress") || enabled)return;
        player=id;
        x=shaftX;
        z=shaftZ;
        enabled=true;
        pass=0;
        stage=0;
        stageTick=0;
        elapsedStart=System.nanoTime();
        lastClientHandoffs=SeamClientWorldPool.nativeHandoffCount();
        LOG.warn("WORLDSEAM_STRESS_START: actual integrated NeoForge GUI; {} physical crossings across End/Nether/Overworld in disposable world",PASSES);
        server.execute(()->{
            var settings=SeamSettings.get(server);
            settings.setEnabled(true);
            settings.setExperimentalNoRespawn(true);
            settings.setCarveNewChunks(true);
            settings.setStack(SeamStack.defaults(),server);
            var live=server.getPlayerList().getPlayer(player);
            if(live==null) {
                LOG.error("WORLDSEAM_STRESS_FAIL: local player disappeared before setup");
                enabled=false;
                return;
            }
            live.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var overworld=server.getLevel(Level.OVERWORLD);
            var nether=server.getLevel(Level.NETHER);
            var end=server.getLevel(Level.END);
            if(overworld==null||nether==null||end==null) {
                LOG.error("WORLDSEAM_STRESS_FAIL: missing target levels");
                enabled=false;
                return;
            }
            // In-game world modification, NOT fake rendering: remove real
            // bedrock and stone in a 9x9 corridor at each touching plane.
            // This code is enabled ONLY in a disposable GitHub CI save.
            int excavated=0;
            var at=new BlockPos.MutableBlockPos();
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
                for(int y=-64;y<=-54;y++) {
                    at.set(x+dx,y,z+dz);
                    if(!overworld.getBlockState(at).isAir()
                        && overworld.setBlock(at,Blocks.AIR.defaultBlockState(),3))
                        excavated++;
                }
                for(int y=120;y<=127;y++) {
                    at.set(x+dx,y,z+dz);
                    if(!nether.getBlockState(at).isAir()
                        && nether.setBlock(at,Blocks.AIR.defaultBlockState(),3))
                        excavated++;
                }
                for(int y=0;y<=9;y++) {
                    at.set(x+dx,y,z+dz);
                    if(!nether.getBlockState(at).isAir()
                        && nether.setBlock(at,Blocks.AIR.defaultBlockState(),3))
                        excavated++;
                }
                for(int y=248;y<=255;y++) {
                    at.set(x+dx,y,z+dz);
                    if(!end.getBlockState(at).isAir()
                        && end.setBlock(at,Blocks.AIR.defaultBlockState(),3))
                        excavated++;
                }
            }
            LOG.info("WORLDSEAM_STRESS_BOTH_SHAFTS_OPEN: cleared={} native blocks across Overworld bottom, Nether roof/floor and End top; default stack=Overworld > Nether > End",excavated);
        });
    }

    private static void tick(ClientTickEvent.Post ignored) {
        Minecraft mc=Minecraft.getInstance();
        MinecraftServer server=mc.getSingleplayerServer();
        if(server==null || mc.level==null || mc.player==null)return;
        // Real Minecraft world joined, native player initialized. The
        // dedicated long-run begins WITHOUT waiting for legacy visual E2E.
        if(!enabled) {
            if(stage==0 && mc.player.tickCount>=70 &&
                mc.level.dimension()==Level.OVERWORLD) {
                begin(server,mc.player.getUUID(),
                    mc.player.blockPosition().getX(),
                    mc.player.blockPosition().getZ());
            }
            return;
        }
        long tick=mc.player.tickCount;
        // Keep timing separate from the existing "model CPU submit" metric.
        // Under software OpenGL the Post event wall-clock separation
        // includes scheduling/GC/render stalls, but not precise GPU time.
        long ns=System.nanoTime();
        if(lastClientTickNs!=0) {
            long gap=ns-lastClientTickNs;
            if(gap>250_000_000L)slowTickGaps250ms++;
            if(gap>1_000_000_000L)slowTickGaps1000ms++;
            worstClientTickGapMs=Math.max(worstClientTickGapMs,gap/1_000_000L);
        }
        lastClientTickNs=ns;
        if(stage==0) {
            // Wait for new default per-world saved stack and chunk residency
            // to sync to the client before attempting End->Nether.
            if(stageTick==0)stageTick=tick;
            if(tick-stageTick<50)return;
            prepare(server,tick);
            return;
        }
        if(stage==1) {
            if(tick-stageTick>WAIT_READY_TICKS) {
                fail("READY_TIMEOUT pass="+pass+" from="+from()+" to="+to()+" client="+mc.level.dimension().location());
                return;
            }
            if(tick%10==0 && !triggerSent) {
                server.execute(()->{
                    ServerPlayer p=server.getPlayerList().getPlayer(player);
                    if(p==null || !p.serverLevel().dimension().location().toString().equals(from()))return;
                    long serverTick=server.overworld().getGameTime();
                    // Give SeamReturnGuard a real server-tick interval at
                    // the position THREE blocks inside this world. If we
                    // reverse immediately after the prior crossing, the
                    // anti-bounce hysteresis rightly rejects the attempt.
                    if(serverTick<stageArmedAfterServerTick)return;
                    if(!SeamReadiness.isReady(player,from(),to(),serverTick))return;
                    if(triggerSent)return;
                    triggerSent=true;
                    int bound=boundary();
                    double starting=UP[pass%4]?bound-0.25:bound+0.25;
                    p.connection.teleport(p.getX(),starting,p.getZ(),
                        p.getYRot(),UP[pass%4]?-90:90);
                    // Avoid the prior test bug: flying=false causes normal
                    // gravity and the client movement packets to overwrite
                    // upward momentum before the seam logic can see it.
                    p.getAbilities().flying=true;
                    p.onUpdateAbilities();
                    p.fallDistance=0;
                    p.setDeltaMovement(new Vec3(0,UP[pass%4]?0.90:-0.55,0));
                    LOG.info("WORLDSEAM_STRESS_TRIGGER: pass={} {} -> {} boundary={} velocity={} native_ready=true",
                        pass+1,from(),to(),bound,UP[pass%4]?0.90:-0.55);
                });
            }
            if(triggerSent) {
                stage=2;
                stageTick=tick;
            }
            return;
        }
        if(stage==2) {
            if(tick-stageTick>WAIT_CROSS_TICKS) {
                fail("CROSS_TIMEOUT pass="+pass+" expected="+to()+" got="+mc.level.dimension().location());
                return;
            }
            if(mc.level.dimension().location().toString().equals(to())
                && SeamClientWorldPool.nativeHandoffCount()>lastClientHandoffs) {
                int handoffs=SeamClientWorldPool.nativeHandoffCount();
                lastClientHandoffs=handoffs;
                stage=3;
                stageTick=tick;
                markerSpawned=false;
                serverConfirmed=false;
                server.execute(()->{
                    ServerPlayer p=server.getPlayerList().getPlayer(player);
                    if(p==null || !p.serverLevel().dimension().location().toString().equals(to())) {
                        LOG.error("WORLDSEAM_STRESS_FAIL_SERVER_CLIENT_MISMATCH: pass {}",pass+1);
                        return;
                    }
                    serverConfirmed=true;
                    // The original E2E opened real air shafts through Nether
                    // roof/floor. A player in creative mode with flight OFF
                    // falls dozens of blocks during the entity probe. Hold
                    // the player STILL only AFTER the real seam transfer,
                    // so nearby entity visibility tests are meaningful.
                    p.getAbilities().flying=true;
                    p.onUpdateAbilities();
                    p.setDeltaMovement(Vec3.ZERO);
                    p.fallDistance=0;
                    if(marker!=null)marker.discard();
                    expectedMarkerX=p.getX()+2;
                    // Entering through a target TOP puts the player at
                    // targetBoundary-0.125. The old test put ArmorStand
                    // at playerY+1, which is *outside* that dimension.
                    // WorldSeam correctly crossed that marker straight
                    // BACK over the seam, making client entity tracking
                    // look broken on every third visit. Keep witnesses
                    // 2 blocks INSIDE the real target world.
                    expectedMarkerY=p.getY()+(UP[pass%4]?2.0:-2.0);
                    expectedMarkerZ=p.getZ();
                    marker=new ArmorStand(p.serverLevel(),
                        expectedMarkerX,expectedMarkerY,expectedMarkerZ);
                    marker.setNoGravity(true);
                    if(!p.serverLevel().addFreshEntity(marker)) {
                        LOG.error("WORLDSEAM_STRESS_FAIL_ENTITY_SPAWN: pass={}",pass+1);
                        return;
                    }
                    expectedMarkerUuid=marker.getUUID();
                    markerSpawned=true;
                });
            }
            return;
        }
        if(stage==3) {
            if(tick-stageTick>ENTITY_WAIT_TICKS) {
                fail("ENTITY_TRACKING_TIMEOUT pass="+pass+" dimension="+mc.level.dimension().location());
                return;
            }
            // Test true vanilla entity packets in the ACTIVE client's
            // native world. This catches no-respawn handoff entity loading
            // regressions that old chunk-only E2E never exercised.
            if(!serverConfirmed || !markerSpawned || tick-stageTick<12)return;
            // Require the EXACT server-spawned UUID for THIS crossing.
            // An old stale entity from a previous visit cannot count.
            UUID witness=expectedMarkerUuid;
            boolean clientEntity=witness!=null && mc.level.getEntitiesOfClass(
                ArmorStand.class,new AABB(
                    expectedMarkerX-3,expectedMarkerY-3,expectedMarkerZ-3,
                    expectedMarkerX+4,expectedMarkerY+4,expectedMarkerZ+4)
                ).stream().anyMatch(e->e.getUUID().equals(witness));
            int chunks=mc.level.getChunkSource().getLoadedChunksCount();
            int owners=SeamClientWorldPool.dedicatedRendererActive();
            int gpuMeshes=SeamNativeGeometryClient.gpuUploadedSections();
            maxGpuSections=Math.max(maxGpuSections,gpuMeshes);
            if(Boolean.getBoolean("worldseam.gpuSections") && gpuMeshes>192) {
                fail("GPU_SECTION_VRAM_LIMIT_EXCEEDED: live="+gpuMeshes+
                     " pass="+(pass+1));
                return;
            }
            long memory=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
            maxUsedHeap=Math.max(maxUsedHeap,memory);
            maxNativeChunks=Math.max(maxNativeChunks,chunks);
            maxBufferOwners=Math.max(maxBufferOwners,owners);
            if(!clientEntity)return;
            if(owners>2) {
                fail("BUFFER_LEAK activeOwners="+owners+" after "+(pass+1)+" crossings");
                return;
            }
            if(chunks<9) {
                fail("CHUNK_DROPOUT activeChunks="+chunks+" pass="+(pass+1));
                return;
            }
            LOG.info("WORLDSEAM_STRESS_PROGRESS: pass={}/{}, {} -> {}, activeChunks={}, markerEntityClient=true, witnessY={}, activeRendererOwners={}, heapUsedMiB={}, renderCpuP95Ms={}, rebuildCpuP95Ms={}",
                pass+1,PASSES,from(),to(),chunks,
                String.format(java.util.Locale.ROOT,"%.3f",expectedMarkerY),
                owners,memory/(1024*1024),
                String.format(java.util.Locale.ROOT,"%.3f",SeamNativeGeometryClient.p95ModelCpuMs()),
                String.format(java.util.Locale.ROOT,"%.3f",SeamNativeGeometryClient.p95RebuildCpuMs()));
            pass++;
            if(pass==PASSES) {
                stage=4;
                stageTick=tick;
                long wallMs=(System.nanoTime()-elapsedStart)/1_000_000L;
                LOG.info("WORLDSEAM_STRESS_PASS: crossings={}, clientHandoffs={}, elapsedWallMs={}, peakNativeChunks={}, peakDedicatedRendererOwners={}, peakHeapUsedMiB={}, peakGpuMeshes={}, clientTickGapsOver250ms={}, clientTickGapsOver1000ms={}, worstClientTickGapMs={}",
                    pass,SeamClientWorldPool.nativeHandoffCount(),wallMs,
                    maxNativeChunks,maxBufferOwners,maxUsedHeap/(1024*1024),
                    maxGpuSections,slowTickGaps250ms,slowTickGaps1000ms,
                    worstClientTickGapMs);
                server.execute(()->{
                    if(marker!=null) {marker.discard();marker=null;}
                    SeamSettings.get(server).setEnabled(false);
                });
                return;
            }
            prepare(server,tick);
        } else if(stage==4 && tick-stageTick>50) {
            if(SeamStackClient.enabled() ||
                SeamClientWorldPool.remoteHasDedicatedBuffers() ||
                SeamNativeGeometryClient.ownBuffersActive() ||
                SeamNativeGeometryClient.gpuUploadedSections()!=0) {
                fail("CLEANUP_FAILED after long-run crossing");
                return;
            }
            LOG.info("WORLDSEAM_STRESS_PASS_CLEANUP: enabled=false remoteBuffers=false proxyBuffers=false completed={}",pass);
            enabled=false;
        }
    }

    private static String from(){return FROM[pass%4];}
    private static String to(){return TO[pass%4];}
    private static int boundary(){return UP[pass%4]?
        (from().equals(END)?256:128):
        (from().equals(OVERWORLD)?-64:0);}

    private static void prepare(MinecraftServer server,long tick) {
        stage=1;
        stageTick=tick;
        triggerSent=false;
        markerSpawned=false;
        expectedMarkerUuid=null;
        serverConfirmed=false;
        String desired=from();
        int b=boundary();
        server.execute(()->{
            ServerPlayer p=server.getPlayerList().getPlayer(player);
            if(p==null || !p.serverLevel().dimension().location().toString().equals(desired)) {
                LOG.error("WORLDSEAM_STRESS_FAIL_STAGE: expected source {}, actual {} at pass {}",
                    desired,p==null?"missing":p.serverLevel().dimension().location(),pass+1);
                return;
            }
            if(marker!=null) {marker.discard();marker=null;}
            p.getAbilities().flying=true;
            p.onUpdateAbilities();
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance=0;
            double standby=UP[pass%4]?b-3.0:b+3.0;
            p.connection.teleport(x+0.5,standby,z+0.5,0,UP[pass%4]?-90:90);
            stageArmedAfterServerTick=server.overworld().getGameTime()+12;
            LOG.info("WORLDSEAM_STRESS_STAGE: pass={}/{}, native source={}, destination={}, x={}, z={}, y={}, upper={}",
                pass+1,PASSES,desired,to(),x,z,standby,UP[pass%4]);
        });
    }

    private static void fail(String explanation) {
        LOG.error("WORLDSEAM_STRESS_FAIL: {}",explanation);
        enabled=false;
    }
}
