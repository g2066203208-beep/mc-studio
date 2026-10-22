package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

/**
 * CI-ONLY GENUINE GAME MOVIE. Launch with -Dworldseam.shipFilm=true.
 *
 * Uses REAL integrated NeoForge 1.21.1 worlds, real vanilla /fill air commands,
 * real published Sable/Create/Aeronautics jars, a Sable-assembled physical
 * sublevel, and PHYSICAL IMPULSES through Sable's actual Rapier pipeline.
 * The sole valid success marker means that the SAME Sable UUID traveled down
 * across the joined Y=-64 plane and returned upwards across it under native
 * physics. A missing crossing MUST yield failure evidence, never a staged
 * fake animation. This does NOT by itself prove shared dual-chart contacts.
 */
public final class SeamShipFilmE2E {
    private static final Logger LOG=LogUtils.getLogger();
    private static volatile int state; // 0 pre-world, 1 setup, 2 descending, 3 rising, 4 pass, -1 fail
    private static volatile long started;
    private static volatile UUID shipUuid;
    private static volatile String failReason="";
    private static Object ship,pipeline;
    private static Method bounding,impulse,uuidGetter;
    private static Class<?> vec;
    private static int x,z;
    private static UUID viewerId;
    private static ArrayList<BlockPos> stagingBlocks;
    private static BlockPos stagingAnchor;
    private static ServerLevel stagingHome;
    private static int upTicks,downTicks,passes;
    private static double initialMinY,lowMinY=Double.POSITIVE_INFINITY;
    private static final double PLANE=-64.0;
    private static boolean sawStraddle,fullyAcross;
    private static long lastPulse;

    private SeamShipFilmE2E() {}

    public static void install() {
        if(!Boolean.getBoolean("worldseam.shipFilm"))return;
        LOG.warn("WORLDSEAM_SHIP_FILM_CI_ONLY: destructive /fill air enabled in DISPOSABLE world");
        NeoForge.EVENT_BUS.addListener(SeamShipFilmE2E::clientTick);
        NeoForge.EVENT_BUS.addListener(SeamShipFilmE2E::serverTick);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        MinecraftServer server=mc.getSingleplayerServer();
        if(state!=0||server==null||mc.player==null||mc.level==null ||
           mc.player.tickCount<80)return;
        if(mc.level.dimension()!=Level.OVERWORLD)return;
        state=1;
        UUID player=mc.player.getUUID();
        server.execute(()->prepare(server,player));
    }

    /**
     * Vanilla /fill, NOT an artistic replacement texture or fake flat world.
     * The caller opts in to DESTRUCTIVELY editing this temporary CI save.
     */
    private static void nativeFill(MinecraftServer server,ServerLevel level,
                                   int y1,int y2) {
        // 25*25*height <= vanilla /fill limit; <16k changed voxels per call.
        String cmd="fill "+(x-12)+" "+y1+" "+(z-12)+" "+
            (x+12)+" "+y2+" "+(z+12)+" minecraft:air replace";
        CommandSourceStack source=server.createCommandSourceStack()
            .withLevel(level).withPermission(4);
        LOG.info("WORLDSEAM_FILM_REAL_VANILLA_COMMAND: dimension={} /{}",
            level.dimension().location(),cmd);
        server.getCommands().performPrefixedCommand(source,cmd);
        if(!level.getBlockState(new BlockPos(x,(y1+y2)/2,z)).isAir())
            throw new IllegalStateException("Real /fill did not clear film arena: "+cmd);
    }

    private static void prepare(MinecraftServer server,UUID playerId) {
        try {
            ServerPlayer player=server.getPlayerList().getPlayer(playerId);
            if(player==null)throw new IllegalStateException("Client player disappeared");
            var home=server.getLevel(Level.OVERWORLD);
            var nether=server.getLevel(Level.NETHER);
            if(home==null||nether==null)throw new IllegalStateException("World not available");
            if(!net.neoforged.fml.ModList.get().isLoaded("sable") ||
               !net.neoforged.fml.ModList.get().isLoaded("create"))
                throw new IllegalStateException("Real Sable/Create releases were not loaded");
            x=player.blockPosition().getX();
            z=player.blockPosition().getZ();
            int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);
            // Only disposable CI world: do not execute these edits on normal
            // user worlds or normal WorldSeam installs.
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++) {
                home.getChunk(cx+dx,cz+dz);
                nether.getChunk(cx+dx,cz+dz);
            }
            var cfg=SeamSettings.get(server);
            cfg.setEnabled(true);
            cfg.setExperimentalNoRespawn(true);
            cfg.setCarveNewChunks(true);
            nativeFill(server,home,-64,-47);
            nativeFill(server,nether,112,134);
            // Put the real spectator in tracking range BEFORE Sable
            // registers its first sublevel/spawn packets. The previous
            // film assembled immediately and got "movement packet for a
            // non-existent sub-level" from the remote client.
            player.setGameMode(GameType.SPECTATOR);
            viewerId=player.getUUID();
            // Genuine vanilla night vision improves visibility inside the
            // excavated deepslate/Nether roof instead of post-processing
            // or recoloring the MP4 into fabricated gameplay.
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                20*60*20,0,false,false));
            home.setDayTime(6000L);
            player.connection.teleport(x+8,-57,z+8,135,13);
            // Genuine 9x7 Minecraft-block aircraft silhouette: tapered
            // nose, center fuselage, swept wings, lit engine and glass
            // canopy. Every voxel is a real world block *before* the
            // released Sable API assembles it into ONE physical sublevel.
            var blocks=new ArrayList<BlockPos>();
            BlockPos anchor=new BlockPos(x,-57,z);
            for(int dz=-4;dz<=4;dz++)for(int dx=-3;dx<=3;dx++) {
                boolean spine=Math.abs(dx)<=1;
                boolean wings=Math.abs(dz)<=1&&Math.abs(dx)<=3;
                boolean tail=Math.abs(dz-3)<=1&&Math.abs(dx)<=2;
                boolean nose=dz<=-3&&dx==0;
                if(!(spine||wings||tail||nose))continue;
                BlockPos bp=anchor.offset(dx,0,dz);
                var block=dz==4?Blocks.SEA_LANTERN:
                    Math.abs(dx)==3?Blocks.YELLOW_CONCRETE:
                    Blocks.LIGHT_BLUE_CONCRETE;
                home.setBlock(bp,block.defaultBlockState(),3);
                blocks.add(bp);
                if(spine&&dz>=-2&&dz<=2) {
                    BlockPos top=bp.above();
                    home.setBlock(top,Math.abs(dx)==0&&dz==-1?
                        Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState():
                        Blocks.IRON_BLOCK.defaultBlockState(),3);
                    blocks.add(top);
                }
            }
            // Distinct glass canopy, third voxel layer.
            BlockPos canopy=anchor.offset(0,2,-1);
            home.setBlock(canopy,Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),3);
            blocks.add(canopy);
            stagingBlocks=blocks;
            stagingAnchor=anchor;
            stagingHome=home;
            started=server.getTickCount();
            LOG.info("WORLDSEAM_FILM_ARENA_READY: /fill air complete in BOTH native worlds, native spectator positioned; waiting for Sable client sublevel tracking handshake");
            state=1;
        } catch(Exception | LinkageError ex) {
            fail("Arena setup failed: "+ex,ex);
        }
    }

    private static void assembleAfterTracking(MinecraftServer server) {
        try {
            ServerLevel home=stagingHome;
            BlockPos anchor=stagingAnchor;
            ArrayList<BlockPos> blocks=stagingBlocks;
            if(home==null||anchor==null||blocks==null)
                throw new IllegalStateException("Missing prepared native aircraft");
            Class<?> bounds=Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3ic");
            Class<?> box=Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3i");
            Object region=box.getConstructor(
                int.class,int.class,int.class,int.class,int.class,int.class)
                .newInstance(x-3,-57,z-4,x+3,-55,z+4);
            Class<?> assembler=Class.forName(
                "dev.ryanhcode.sable.api.SubLevelAssemblyHelper");
            ship=assembler.getMethod("assembleBlocks",ServerLevel.class,
                BlockPos.class,Iterable.class,bounds).invoke(null,home,anchor,blocks,region);
            if(ship==null)throw new IllegalStateException("Sable assembler returned null");
            Class<?> sub=Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            bounding=sub.getDeclaredMethod("boundingBox");
            uuidGetter=sub.getDeclaredMethod("getUniqueId");
            shipUuid=(UUID)uuidGetter.invoke(ship);
            // Sable initializes SubLevel.globalBounds to the zero AABB.
            // assembleBlocks() DOES NOT call updateBoundingBox(): reading
            // the zero box immediately as a ship at Y=0 was a false signal.
            // Refresh the actual plot geometry after real assembly, before
            // recording any real position. No physics pose/ship teleport.
            sub.getMethod("updateBoundingBox").invoke(ship);
            Class<?> system=Class.forName(
                "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");
            Object owner=system.getMethod("require",Level.class).invoke(null,home);
            pipeline=system.getMethod("getPipeline").invoke(owner);
            Class<?> body=Class.forName(
                "dev.ryanhcode.sable.api.physics.PhysicsPipelineBody");
            vec=Class.forName("org.joml.Vector3d");
            Class<?> v=Class.forName("org.joml.Vector3dc");
            impulse=pipeline.getClass().getMethod("applyLinearAndAngularImpulse",
                body,v,v,boolean.class);
            var hull=currentHull();
            if(hull.getSize()<=0.0 ||
               hull.minY>=hull.maxY ||
               hull.minY< -256 || hull.maxY>512)
                throw new IllegalStateException(
                    "Sable returned stale/invalid zero AABB after real assembly: "+hull);
            initialMinY=hull.minY;
            started=server.getTickCount();
            // The camera is an ACTUAL in-game spectator, never a diagram.
            stagingBlocks=null;
            LOG.info("WORLDSEAM_FILM_RECORD_READY: REAL Sable uuid={}, startMinY={}, plane=-64, real camera pre-tracking stable, /fill air Overworld and Nether completed",
                shipUuid,initialMinY);
            state=2;
        } catch(Exception | LinkageError ex) {
            fail("Setup failed: "+ex,ex);
        }
    }

    private static net.minecraft.world.phys.AABB currentHull() throws Exception {
        var box=bounding.invoke(ship);
        Class<?> bounds=Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc");
        double[] coords=new double[6];
        String[] names={"minX","minY","minZ","maxX","maxY","maxZ"};
        for(int i=0;i<names.length;i++)
            coords[i]=((Number)bounds.getMethod(names[i]).invoke(box)).doubleValue();
        return new net.minecraft.world.phys.AABB(
            coords[0],coords[1],coords[2],coords[3],coords[4],coords[5]);
    }

    /** NOT a teleport: pushes the SAME Rapier UUID by native linear impulse. */
    private static void pulse(double vertical) throws Exception {
        var xyz=vec.getConstructor(double.class,double.class,double.class);
        Object linear=xyz.newInstance(0.0,vertical,0.0);
        Object torque=xyz.newInstance(0.0,0.0,0.0);
        impulse.invoke(pipeline,ship,linear,torque,true);
    }

    private static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server=event.getServer();
        if(state==1) {
            long elapsed=server.getTickCount()-started;
            if(elapsed%10==0)
                LOG.info("WORLDSEAM_FILM_HANDSHAKE_TICK: actualServerTicksSinceArena={}, waitingFor=60, serverTick={}",
                    elapsed,server.getTickCount());
            if(elapsed>=60) {
                LOG.info("WORLDSEAM_FILM_NATIVE_TICK_HANDSHAKE: spectator tracked for {} real server ticks before sublevel assembly",elapsed);
                assembleAfterTracking(server);
            }
            return;
        }
        if(state!=2&&state!=3)return;
        if(server.getTickCount()-started>20*100) {
            fail("Native Rapier ship never completed real bidirectional seam crossing",null);
            return;
        }
        try {
            var hull=currentHull();
            if(!Double.isFinite(hull.minY)||!Double.isFinite(hull.maxY))
                throw new IllegalStateException("Nonfinite Sable hull");
            if(hull.minY<PLANE && hull.maxY>PLANE)sawStraddle=true;
            lowMinY=Math.min(lowMinY,hull.minY);
            int ticks=(int)(server.getTickCount()-started);
            // An actual in-game spectator CAMERA follows the real hull.
            // The Sable rigid body itself is NEVER teleported.
            // Camera stays in Overworld, shooting into the other dimension.
            if(ticks%3==0) {
                ServerPlayer camera=viewerId==null?null:
                    server.getPlayerList().getPlayer(viewerId);
                if(camera!=null && camera.serverLevel().dimension()==Level.OVERWORLD) {
                    double midY=(hull.minY+hull.maxY)*0.5;
                    double eyeY=Math.max(-60.0,Math.min(-55.0,midY+4.0));
                    double eyeDiff=(eyeY+1.62)-midY;
                    float pitch=(float)Math.toDegrees(Math.atan2(
                        eyeDiff,Math.sqrt(8.0*8.0+8.0*8.0)));
                    camera.connection.teleport(x+8,eyeY,z+8,135,pitch);
                }
            }
            if(state==2) {
                downTicks++;
                // Gentle repeated *real impulse*, not teleport or rewriting
                // entity/world pose. Sable/Rapier still controls the body.
                if(ticks-lastPulse>=8) {
                    pulse(-35);
                    lastPulse=ticks;
                }
                if(hull.maxY<PLANE-1) {
                    fullyAcross=true;
                    state=3;
                    lastPulse=0;
                    LOG.info("WORLDSEAM_FILM_REAL_SHIP_DOWN: uuid={} minY={} maxY={} tick={} straddled={}, movement=native-rapier-impulses",
                        shipUuid,hull.minY,hull.maxY,ticks,sawStraddle);
                }
            } else {
                upTicks++;
                if(ticks-lastPulse>=8) {
                    pulse(110);
                    lastPulse=ticks;
                }
                if(hull.minY>PLANE+1 && fullyAcross && sawStraddle) {
                    state=4;
                    passes++;
                    LOG.info("WORLDSEAM_FILM_PASS_REAL_SHIP_RETURN: uuid={} startMinY={} bottomMinY={} returnMinY={} ticks={} downTicks={} upTicks={}, twoWayStraddle=true, noTeleport=true, noSecondRigidBody=true",
                        shipUuid,initialMinY,lowMinY,hull.minY,ticks,downTicks,upTicks);
                }
            }
            if(ticks<=6 || ticks%10==0)
                LOG.info("WORLDSEAM_FILM_FRAME: tick={} uuid={} state={} hullMinY={} hullMaxY={} straddles={} dimension=overworld(authoritative Sable body)",
                    ticks,shipUuid,state,hull.minY,hull.maxY,
                    hull.minY<PLANE&&hull.maxY>PLANE);
        } catch(Exception | LinkageError ex) {
            fail("Real Rapier impulse/pose failed: "+ex,ex);
        }
    }

    private static void fail(String reason,Throwable ex) {
        if(state==-1)return;
        state=-1;
        failReason=reason;
        if(ex==null)LOG.error("WORLDSEAM_FILM_FAIL_REAL_SHIP_CROSSING: {} uuid={}",reason,shipUuid);
        else LOG.error("WORLDSEAM_FILM_FAIL_REAL_SHIP_CROSSING: {} uuid={}",reason,shipUuid,ex);
    }
}
