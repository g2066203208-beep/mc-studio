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
    private static volatile int state; // 0 pre-world, 5 queued setup, 1 ARENA_READY/handshake, 2 descending, 3 rising, 4 pass, -1 fail
    private static volatile long started;
    private static volatile UUID shipUuid;
    private static volatile String failReason="";
    private static Object ship,pipeline;
    private static Method bounding,impulse,uuidGetter;
    private static Class<?> vec;
    private static int x,z;
    private static UUID viewerId;
    private static ArrayList<BlockPos> stagingBlocks;
    private static ArrayList<BlockPos> realRunwayBlocks;
    private static BlockPos stagingAnchor;
    private static ServerLevel stagingHome;
    private static int upTicks,downTicks,passes;
    private static double initialMinY,lowMinY=Double.POSITIVE_INFINITY;
    private static double actualShipMassKg;
    private static final double PLANE=-64.0;
    private static boolean sawStraddle,fullyAcross;
    private static long lastPulse;
    private static volatile boolean shipClientRenderReady;
    private static volatile int clientSublevelSeenFrames;
    private static volatile int clientSublevelRenderableFrames;
    private static long realFlightStartTick=-1;
    private static boolean startedRealFlight;

    private SeamShipFilmE2E() {}

    public static void install() {
        if(!Boolean.getBoolean("worldseam.shipFilm"))return;
        LOG.warn("WORLDSEAM_SHIP_FILM_CI_ONLY: destructive /fill air enabled in DISPOSABLE world");
        NeoForge.EVENT_BUS.addListener(SeamShipFilmE2E::clientTick);
        NeoForge.EVENT_BUS.addListener(SeamShipFilmE2E::serverTick);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        // A SERVER-side AABB crossing is not a visible Minecraft video.
        // Verify the SAME physical Sable UUID exists in the actual ClientLevel
        // and has a live Sable render-data object before any impulses begin.
        if(shipUuid!=null && (state==2 || state==3)) {
            try {
                Class<?> containerApi=Class.forName(
                    "dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                Object holder=containerApi.getMethod("getContainer",
                    net.minecraft.client.multiplayer.ClientLevel.class)
                    .invoke(null,mc.level);
                if(holder!=null) {
                    Object tracked=containerApi.getMethod("getSubLevel",UUID.class)
                        .invoke(holder,shipUuid);
                    if(tracked!=null) {
                        clientSublevelSeenFrames++;
                        Object rend=tracked.getClass().getMethod("getRenderData")
                            .invoke(tracked);
                        if(rend!=null) {
                            clientSublevelRenderableFrames++;
                            shipClientRenderReady=true;
                        }
                    }
                }
            } catch(Exception | LinkageError ex) {
                if(clientSublevelSeenFrames==0 && mc.player!=null &&
                   mc.player.tickCount%40==0)
                    LOG.warn("WORLDSEAM_FILM_CLIENT_SABLE_DIAGNOSTIC: {}",
                        ex.toString());
            }
        }
        MinecraftServer server=mc.getSingleplayerServer();
        if(state!=0||server==null||mc.player==null||mc.level==null ||
           mc.player.tickCount<80)return;
        if(mc.level.dimension()!=Level.OVERWORLD)return;
        // The client callback and server thread run independently. Mark
        // setup PENDING, not ARENA_READY: otherwise the server-tick
        // listener sees state=1 before prepare() has even executed and
        // attempts to assemble a non-existent Sable test aircraft.
        state=5;
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
        if(state!=5)return; // a prior failure may have invalidated setup
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
            player.connection.teleport(x+8,-52,z+8,135,29);
            // Keep the ACTUAL Sable hull physically supported while the
            // real client receives matching UUID/render data. In the prior
            // film the ship fell through Y=-64 BEFORE the first recorded
            // frame (downTick=0). This is a real 3x3 stone runway beneath
            // the aircraft, NOT frozen pose, no gravity override or fake
            // projected rendering. Remove it only after the first genuine
            // ready screenshot/flight gate.
            realRunwayBlocks = new ArrayList<>();
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                BlockPos support=new BlockPos(x+dx,-59,z+dz);
                if(!home.setBlock(support,Blocks.OBSIDIAN.defaultBlockState(),3))
                    throw new IllegalStateException("Could not stage real runway "+support);
                realRunwayBlocks.add(support);
            }
            LOG.info("WORLDSEAM_FILM_REAL_RUNWAY_READY: vanilla supportBlocks={} y=-59, real physics will stay grounded until client has tracked same ship UUID",realRunwayBlocks.size());
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

            // IMPORTANT: actually build Create:Aeronautics machinery instead
            // of a plain blue/yellow raft that our CI forcibly pushes.
            // Create Creative Motor is a real KineticBlockEntity (initial
            // 16 RPM); the genuine Aeronautics Smart Propeller above it
            // consumes Create rotation via its vertical kinetic shaft and
            // submits native Sable propulsion per physics substep.
            // Fail explicitly if the exact RELEASED mod blocks are absent.
            var motor=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("create:creative_motor"));
            var rotor=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("aeronautics:smart_propeller"));
            if(motor==Blocks.AIR || rotor==Blocks.AIR)
                throw new IllegalStateException(
                    "Actual Create creative motor or Aeronautics smart propeller missing in released jars");
            var motorState=motor.defaultBlockState();
            if(!motorState.hasProperty(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING))
                throw new IllegalStateException(
                    "Release Create Creative Motor FACING property incompatible");
            motorState=motorState.setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                net.minecraft.core.Direction.UP);
            BlockPos motorPos=anchor.offset(0,1,-2);
            BlockPos rotorPos=motorPos.above();
            var rotorState=rotor.defaultBlockState();
            // Verified against OFFICIAL SmartPropellerBlock source:
            // HORIZONTAL_AXIS only accepts X or Z, controlling hinge tilt.
            // Its IRotate.getRotationAxis(...) is independently Y, and
            // hasShaftTowards is DOWN for CEILING=false. Setting the
            // blockstate AXIS=Y throws IllegalArgumentException at runtime!
            // Let the actual released block report its drive axis instead.
            if(!rotorState.hasProperty(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS))
                throw new IllegalStateException(
                    "Released Aeronautics smart propeller has no HORIZONTAL_AXIS");
            var hingeAxis=rotorState.getValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS);
            if(hingeAxis!=net.minecraft.core.Direction.Axis.X &&
               hingeAxis!=net.minecraft.core.Direction.Axis.Z)
                throw new IllegalStateException("Propeller hinge must be horizontal");
            if(!home.setBlock(motorPos,motorState,3) ||
               !home.setBlock(rotorPos,rotorState,3))
                throw new IllegalStateException("Could not stage real Create/Aero drivetrain");
            // Runtime reflection here preserves WorldSeam's genuinely
            // independent build (Create/Aero are optional external jars).
            Object driveAxis=rotor.getClass().getMethod(
                "getRotationAxis",net.minecraft.world.level.block.state.BlockState.class)
                .invoke(rotor,home.getBlockState(rotorPos));
            boolean shaftFacesMotor=(boolean)rotor.getClass().getMethod(
                "hasShaftTowards",net.minecraft.world.level.LevelReader.class,
                BlockPos.class,net.minecraft.world.level.block.state.BlockState.class,
                net.minecraft.core.Direction.class).invoke(
                    rotor,home,rotorPos,rotorState,net.minecraft.core.Direction.DOWN);
            if(driveAxis!=net.minecraft.core.Direction.Axis.Y || !shaftFacesMotor)
                throw new IllegalStateException(
                    "Actual Aeronautics kinetic axle does not face UP motor: "+
                    driveAxis+" downFace="+shaftFacesMotor);
            blocks.add(rotorPos); // motor already replaces a counted hull voxel
            LOG.info("WORLDSEAM_FILM_REAL_CREATE_AERO_MACHINERY: motor={} {}, propeller={} {}, horizontalHingeAxis={}, kineticRotationAxis=Y, shaftFacesMotor=true, verify TRUE getSpeed()+scaledThrust AFTER assembly",
                motorPos,motorState,rotorPos,rotorState,hingeAxis);
            stagingBlocks=blocks;
            stagingAnchor=anchor;
            stagingHome=home;
            started=server.getTickCount();
            // Publish ready LAST, after all writes. The server-tick
            // observer is never allowed to assemble an incomplete fixture.
            state=1;
            LOG.info("WORLDSEAM_FILM_ARENA_READY: /fill air complete in BOTH native worlds, real Aero/Create drivetrain placed, native spectator positioned; now waiting for Sable client sublevel tracking handshake");
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
            shipClientRenderReady=false;
            clientSublevelSeenFrames=0;
            clientSublevelRenderableFrames=0;
            realFlightStartTick=-1;
            startedRealFlight=false;
            lastPulse=0;
            sawStraddle=false; fullyAcross=false;
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
            Object massData=body.getMethod("getMassTracker").invoke(ship);
            Class<?> massInterface=Class.forName(
                "dev.ryanhcode.sable.api.physics.mass.MassData");
            actualShipMassKg=((Number)massInterface.getMethod("getMass")
                .invoke(massData)).doubleValue();
            if(!Double.isFinite(actualShipMassKg) ||
                actualShipMassKg<=0 || actualShipMassKg>100_000_000)
                throw new IllegalStateException(
                    "Sable assembled ship with invalid native mass="+actualShipMassKg);
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
            LOG.info("WORLDSEAM_FILM_REAL_RAPIER_MASS: uuid={}, nativeMassKg={}, forceAccelerationScale=mass-times-meters-per-second-squared",
                shipUuid,actualShipMassKg);
            var aero=SeamAeronauticsEvidence.inspect(ship);
            LOG.warn("WORLDSEAM_FILM_AERONAUTICS_DRIVETRAIN: {} actors={}, powered={}, rpmAndThrustVerified={}, diagnostic={}; test-imposed Rapier forces are NOT Aeronautics engine power",
                shipUuid,aero.aeronauticsPropellerActors(),aero.powered(),
                aero.totalAbsoluteThrust(),aero.diagnostic());
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

    /**
     * Sable's API method is historically named applyLinearAndAngularImpulse,
     * but its native Rapier bridge actually calls applyForceAndTorque and
     * expects NEWTONS. The old test sent merely 35 N to a many-ton ship,
     * producing no perceptible movement. Scale FORCE by the genuine
     * Sable-reported block mass: F = m*a (NO pose editing/teleport).
     */
    private static void pulse(double accelerationY) throws Exception {
        var xyz=vec.getConstructor(double.class,double.class,double.class);
        Object linear=xyz.newInstance(0.0,actualShipMassKg*accelerationY,0.0);
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
        long sinceAssembly=server.getTickCount()-started;
        if(!startedRealFlight) {
            if(sinceAssembly%20==0)
                LOG.info("WORLDSEAM_FILM_CLIENT_TRACK_GATE: uuid={} elapsedServerTicks={} clientSeenFrames={} clientRenderableFrames={} renderReady={}",
                    shipUuid,sinceAssembly,clientSublevelSeenFrames,
                    clientSublevelRenderableFrames,shipClientRenderReady);
            if(sinceAssembly>160) {
                fail("Real Minecraft client never received and initialized visible Sable render data for identical ship UUID, cannot call this a flight video",null);
                return;
            }
            // Hold ship before motion until real rendered client sublevel
            // exists, then leave >=40 native ticks for actual render uploads.
            if(!shipClientRenderReady || sinceAssembly<40)return;
            // Earlier recording falsely showed a DOWN marker on the first
            // flight tick after 40 ticks of uncontrolled free-fall.
            // Reject it: an aircraft must first be clearly parked above the
            // seam in the same native world, then take off in the footage.
            try {
                var parked=currentHull();
                if(parked.minY < PLANE+1.0 || parked.maxY <= PLANE)
                    throw new IllegalStateException(
                        "Real aircraft already fell through the seam before takeoff: "+parked);
                if(realRunwayBlocks==null||realRunwayBlocks.size()!=9)
                    throw new IllegalStateException("Missing 3x3 real runway");
                ServerLevel home=stagingHome;
                for(BlockPos support:realRunwayBlocks)
                    if(!home.setBlock(support,Blocks.AIR.defaultBlockState(),3))
                        throw new IllegalStateException(
                            "Cannot clear real support before takeoff: "+support);
                realRunwayBlocks.clear();
                LOG.info("WORLDSEAM_FILM_REAL_TAKEOFF: uuid={} parkedMinY={} parkedMaxY={} removedNativeRunway=true, noTeleport=true",
                    shipUuid,parked.minY,parked.maxY);
            } catch(Exception e) {
                fail("Real parked-ship takeoff fixture failed: "+e,e);
                return;
            }
            startedRealFlight=true;
            realFlightStartTick=server.getTickCount();
            LOG.info("WORLDSEAM_FILM_CLIENT_VISIBLE_SHIP_READY: sameUuid={} clientSeenFrames={} clientRenderableFrames={} realClientRenderData=true",
                shipUuid,clientSublevelSeenFrames,clientSublevelRenderableFrames);
        }
        if(server.getTickCount()-realFlightStartTick>20*100) {
            fail("Native Rapier ship never completed real bidirectional seam crossing",null);
            return;
        }
        try {
            var hull=currentHull();
            if(!Double.isFinite(hull.minY)||!Double.isFinite(hull.maxY))
                throw new IllegalStateException("Nonfinite Sable hull");
            if(!shipClientRenderReady)
                throw new IllegalStateException("Client lost real Sable ship render data mid-flight");
            if(hull.minY<PLANE && hull.maxY>PLANE)sawStraddle=true;
            lowMinY=Math.min(lowMinY,hull.minY);
            int ticks=(int)(server.getTickCount()-realFlightStartTick);
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
                    pulse(-12);
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
                    pulse(50);
                    lastPulse=ticks;
                }
                if(hull.minY>PLANE+1 && fullyAcross && sawStraddle) {
                    var aero=SeamAeronauticsEvidence.inspect(ship);
                    LOG.info("WORLDSEAM_FILM_REAL_SABLE_FORCE_ONLY_ROUNDTRIP: uuid={}, startMinY={}, bottomMinY={}, returnMinY={}, sameBody=true, testExternalForce=true, aeroPowered={}",
                        shipUuid,initialMinY,lowMinY,hull.minY,aero.powered());
                    // Sable-only body forced across does NOT establish the
                    // requested Create:Aeronautics powertrain. Certify only
                    // when an actual running propeller entity from the real
                    // mod was found on THIS live Sable sublevel.
                    if(!aero.powered()) {
                        fail("A static Sable ship moved under test-imposed force, not actual Aeronautics propulsion: "+aero.diagnostic(),null);
                        return;
                    }
                    state=4;
                    passes++;
                    LOG.info("WORLDSEAM_FILM_PASS_REAL_SHIP_RETURN: uuid={} startMinY={} bottomMinY={} returnMinY={} ticks={} downTicks={} upTicks={}, twoWayStraddle=true, aeroPropellerSpinningAtReturn=true, poweredByTestExternalForce=true, aeronauticsOnlyFlightUnverified=true, trueDualChartRapierContactsUnverified=true, nativeClientSameUuidRenderableFrames={}",
                        shipUuid,initialMinY,lowMinY,hull.minY,ticks,downTicks,upTicks,clientSublevelRenderableFrames);
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
