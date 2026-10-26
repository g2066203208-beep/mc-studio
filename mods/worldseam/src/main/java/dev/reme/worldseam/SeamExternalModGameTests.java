package dev.reme.worldseam;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Runtime API contract: only the special CI job loads the THREE RELEASED
 * third-party mods. Standalone WorldSeam testing stays independent.
 *
 * This is an optional-dependency load/linkage test; it does NOT certify
 * Sable shared-Rapier impulse solving or a real assembled airship straddle.
 */
@GameTestHolder(WorldSeamMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeamExternalModGameTests {
    private SeamExternalModGameTests() {}

    @GameTest(template="empty",batch="worldseam.real_ship_assembly",timeoutTicks=200)
    public static void detectActuallyAssembledSableHullCrossingBoundary(GameTestHelper helper) {
        if(!Boolean.getBoolean("worldseam.requireExternalMods")) {
            helper.succeed();
            return;
        }
        if(!ModList.get().isLoaded("sable")) {
            helper.fail("Real Sable jar not installed for physical hull test");
            return;
        }

        var level=helper.getLevel();
        var server=level.getServer();
        // Disposable GameTest-only fixture: real 2-layer stone raft in the
        // OVERWORLD floor, not synthetic AABB data, fake ships or mock APIs.
        var anchor=helper.absolutePos(new net.minecraft.core.BlockPos(2,1,2));
        var floor=new net.minecraft.core.BlockPos(anchor.getX(),-64,anchor.getZ());
        var blocks=new java.util.ArrayList<net.minecraft.core.BlockPos>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            for(int y=-64;y<=-63;y++) {
                var pos=new net.minecraft.core.BlockPos(
                    floor.getX()+x,y,floor.getZ()+z);
                level.setBlock(pos,
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                blocks.add(pos);
            }

        final java.util.UUID actualShip;
        try {
            // Reflect only stable PUBLIC Sable APIs; production WorldSeam
            // remains independently buildable without a mandatory Sable jar.
            Class<?> bboxType=Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3ic");
            Class<?> bboxClass=Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3i");
            Object bounds=bboxClass.getConstructor(int.class,int.class,int.class,
                int.class,int.class,int.class).newInstance(
                floor.getX()-1,-64,floor.getZ()-1,
                floor.getX()+1,-63,floor.getZ()+1);
            Class<?> helperClass=Class.forName(
                "dev.ryanhcode.sable.api.SubLevelAssemblyHelper");
            Object sub=helperClass.getMethod("assembleBlocks",
                net.minecraft.server.level.ServerLevel.class,
                net.minecraft.core.BlockPos.class,Iterable.class,bboxType)
                .invoke(null,level,floor,blocks,bounds);
            if(sub==null) {
                helper.fail("Sable official assembleBlocks returned no physical sublevel");
                return;
            }
            actualShip=(java.util.UUID)sub.getClass().getMethod("getUniqueId").invoke(sub);
            com.mojang.logging.LogUtils.getLogger().info(
                "WORLDSEAM_REAL_SABLE_SHIP_ASSEMBLED: uniqueUuid={}, nativeBlocks={}, actualLevel={}",
                actualShip,blocks.size(),level.dimension().location());
        } catch(ReflectiveOperationException | LinkageError ex) {
            helper.fail("Actual Sable 2.0.6 public ship assembly failed: "+ex);
            return;
        }

        helper.succeedWhen(() -> {
            // The REAL sublevel must move under Sable physics and straddle
            // the native OW floor, which maps to the Nether logical roof.
            SeamSableObserver.tick(server,10,true);
            var report=SeamSableObserver.snapshot();
            helper.assertTrue(report.compatible(),
                "Sable reflection was not compatible with the installed runtime");
            helper.assertTrue(report.straddlingBodies()>0,
                "Sable created the ship, but no actual native hull straddled the joined world plane yet");
            helper.assertTrue(SeamShipResidency.active()>0,
                "A genuine Sable ship touched the boundary but no adjacent-native terrain region ticket exists");
            // The screenshot-free dedicated-server test must verify MORE
            // than a synthetic AABB. Start with the image of the genuinely
            // assembled Sable ServerSubLevel captured by the server observer.
            var actualImage=SeamSableObserver.sampleRemoteImage();
            helper.assertTrue(actualImage!=null &&
                SeamSableObserver.sampleRemoteDimension().equals("minecraft:the_nether"),
                "No actual Sable hull image in Nether was captured");
            var nativeNether=server.getLevel(net.minecraft.world.level.Level.NETHER);
            helper.assertTrue(nativeNether!=null,"The Nether native world is missing");
            int wx=(int)Math.floor((actualImage.minX+actualImage.maxX)*0.5);
            int wz=(int)Math.floor((actualImage.minZ+actualImage.maxZ)*0.5);
            int wy=(int)Math.floor(actualImage.minY+
                Math.min(0.1,(actualImage.maxY-actualImage.minY)*0.5));
            var nativeWitness=new net.minecraft.core.BlockPos(wx,wy,wz);
            // One deterministic test-only load is allowed when creating a
            // fixture; the PRODUCTION contact probe must remain NO-LOAD.
            nativeNether.getChunk(Math.floorDiv(wx,16),Math.floorDiv(wz,16));
            var former=nativeNether.getBlockState(nativeWitness);
            try {
                nativeNether.setBlock(nativeWitness,
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                var genuineProbe=SeamShipRemoteContactProbe.scan(
                    nativeNether,actualImage);
                helper.assertTrue(genuineProbe.hasCandidate(),
                    "Actual Sable hull image failed to intersect REAL Nether stone collision");
                helper.assertTrue(genuineProbe.complete(),
                    "Actual Sable hull other-dimension collision probe was incomplete");
                com.mojang.logging.LogUtils.getLogger().info(
                    "WORLDSEAM_REAL_SABLE_REMOTE_VOXEL_CONTACT: authenticShip={}, realNetherBlock={}, hullContactCandidateCount={}, physicsBridgeActive=false",
                    actualShip,nativeWitness,genuineProbe.solidCandidates());
            } finally {
                nativeNether.setBlock(nativeWitness,former,3);
            }
            var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);
            helper.assertTrue(nether!=null,
                "A real Sable ship cannot enter a missing destination dimension");
            // This real released-Sable GameTest runs without a spectator:
            // verify that the newly published native terrain anchor comes
            // from the actual OW-owned assembled hull, not player position.
            var realHull=SeamSableObserver.nativeRapierSourceHull(level,nether,10);
            var nativeAnchor=SeamRapierAnchor.fromHull(realHull);
            helper.assertTrue(nativeAnchor!=null,
                "Real unattended Sable ship did not publish its native Rapier terrain anchor");
            helper.assertTrue(SeamSableObserver.nativeRapierSourceHull(level,nether,31)==null,
                "Expired Sable terrain anchor was reused after 20 ticks");
            com.mojang.logging.LogUtils.getLogger().info(
                "WORLDSEAM_REAL_SABLE_UNATTENDED_TERRAIN_ANCHOR: uuid={} chunkX={} chunkZ={} actualHull={} (observer only; JNI same-body force/torque NOT verified)",
                actualShip,nativeAnchor.chunkX(),nativeAnchor.chunkZ(),realHull);
            com.mojang.logging.LogUtils.getLogger().info(
                "WORLDSEAM_REAL_SABLE_STRADDLE_OBSERVED: uuid={}, totalLiveHulls={}, straddling={}, shipDrivenRemoteRegionTickets={}, physicsBridgeActive={}",
                actualShip,report.liveBodies(),report.straddlingBodies(),
                SeamShipResidency.active(),report.physicsBridgeActive());
            // This DOES NOT pass physics impulse, joints or dual world
            // renderer requirements. This is a narrow real-assembly probe.
        });
    }


    /**
     * Actual physics MOTION gate, not a static straddle detection:
     * Create an honest Sable ServerSubLevel via its released public API,
     * apply a mass-scaled Newton FORCE through its real Rapier pipeline,
     * then require the SAME UUID's world-space AABB to move downward.
     * No fake entity movement, direct pose editing, or teleporting.
     */
    @GameTest(template="empty",batch="worldseam.real_ship_dynamics",timeoutTicks=160)
    public static void realSableMustActuallyMoveUnderNativeRapierForce(GameTestHelper helper) {
        if(!Boolean.getBoolean("worldseam.requireExternalMods")) {
            helper.succeed();
            return;
        }
        var level=helper.getLevel();
        var origin=helper.absolutePos(new net.minecraft.core.BlockPos(4,1,4));
        int x=origin.getX(),z=origin.getZ();
        // Disposable isolated GameTest fixture: clear a small, real native
        // vertical fall shaft, not a motion animation or transformed blocks.
        var pos=new net.minecraft.core.BlockPos.MutableBlockPos();
        for(int y=-64;y<=-49;y++)
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++) {
                pos.set(x+dx,y,z+dz);
                level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            }
        var nativeBlocks=new java.util.ArrayList<net.minecraft.core.BlockPos>();
        for(int dy=0;dy<=1;dy++)
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                var b=new net.minecraft.core.BlockPos(x+dx,-55+dy,z+dz);
                level.setBlock(b,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                nativeBlocks.add(b);
            }
        final Object sub;
        final java.lang.reflect.Method hullMethod,forceMethod;
        final java.util.UUID uuid;
        final Class<?> jomlVec;
        final double massKg,initialY;
        final long startTime=level.getGameTime();
        try {
            Class<?> shape=Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3ic");
            Object cube=Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3i")
                .getConstructor(int.class,int.class,int.class,int.class,int.class,int.class)
                .newInstance(x-1,-55,z-1,x+1,-54,z+1);
            sub=Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper")
                .getMethod("assembleBlocks",net.minecraft.server.level.ServerLevel.class,
                    net.minecraft.core.BlockPos.class,Iterable.class,shape)
                .invoke(null,level,new net.minecraft.core.BlockPos(x,-55,z),
                    nativeBlocks,cube);
            if(sub==null)throw new IllegalStateException("Sable did not allocate a real ship");
            var subType=Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            subType.getMethod("updateBoundingBox").invoke(sub);
            hullMethod=subType.getMethod("boundingBox");
            uuid=(java.util.UUID)subType.getMethod("getUniqueId").invoke(sub);
            var massBody=Class.forName("dev.ryanhcode.sable.api.physics.PhysicsPipelineBody");
            var massData=massBody.getMethod("getMassTracker").invoke(sub);
            massKg=((Number)Class.forName("dev.ryanhcode.sable.api.physics.mass.MassData")
                .getMethod("getMass").invoke(massData)).doubleValue();
            if(!Double.isFinite(massKg)||massKg<=0)
                throw new IllegalStateException("Sable returned nonphysical mass "+massKg);
            Object physics=Class.forName(
                "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem")
                .getMethod("require",net.minecraft.world.level.Level.class)
                .invoke(null,level);
            Object pipeline=physics.getClass().getMethod("getPipeline").invoke(physics);
            Class<?> vector=Class.forName("org.joml.Vector3dc");
            jomlVec=Class.forName("org.joml.Vector3d");
            forceMethod=pipeline.getClass().getMethod("applyLinearAndAngularImpulse",
                massBody,vector,vector,boolean.class);
            // Keep the actual live Sable pipeline in a final closure for
            // repeated force while engine substeps are ticking.
            nativeSableForcePipeline= pipeline;
            var aabb=hullMethod.invoke(sub);
            var bbClass=Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3dc");
            initialY=((Number)bbClass.getMethod("minY").invoke(aabb)).doubleValue();
            if(initialY > -53 || initialY < -57)
                throw new IllegalStateException("Sable initial real bounds invalid: "+initialY);
            com.mojang.logging.LogUtils.getLogger().info(
                "WORLDSEAM_SABLE_NATIVE_MOTION_START: uuid={}, massKg={}, minY={}, fixture=real18BlockRaft",
                uuid,massKg,initialY);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {
            helper.fail("Native Sable dynamics fixture could not initialize: "+ex);
            return;
        }
        helper.succeedWhen(() -> {
            try {
                long elapsed=level.getGameTime()-startTime;
                var bounds=hullMethod.invoke(sub);
                double currentY=((Number)Class.forName(
                    "dev.ryanhcode.sable.companion.math.BoundingBox3dc")
                    .getMethod("minY").invoke(bounds)).doubleValue();
                if(!Double.isFinite(currentY))
                    throw new IllegalStateException("Sable physics produced non-finite position");
                if(currentY < initialY-0.8) {
                    com.mojang.logging.LogUtils.getLogger().info(
                        "WORLDSEAM_REAL_SABLE_NATIVE_FORCE_MOVEMENT_PASS: sameUuid={}, initialMinY={}, observedMinY={}, deltaY={}, elapsedNativeTicks={}, nativeMassKg={}, noTeleports=true",
                        uuid,initialY,currentY,currentY-initialY,elapsed,massKg);
                    return;
                }
                if(elapsed<60) {
                    // Sable uses true FORCE in Newtons. Acceleration = F/m,
                    // not the obsolete fixed "35 N" that barely moved a ship.
                    Object force=jomlVec.getConstructor(double.class,double.class,double.class)
                        .newInstance(0.0,-massKg*75.0,0.0);
                    Object zero=jomlVec.getConstructor(double.class,double.class,double.class)
                        .newInstance(0.0,0.0,0.0);
                    forceMethod.invoke(nativeSableForcePipeline,sub,force,zero,true);
                }
                helper.assertTrue(false,"Sable native Rapier has not moved its real ship yet: initial="+
                    initialY+" current="+currentY+" ticks="+elapsed);
            } catch(ReflectiveOperationException | RuntimeException ex) {
                helper.fail("Native Sable position/force callback failed: "+ex);
            }
        });
    }

    /** GameTest single server-thread session; only the real optional mod
        physics suite populates this. Never consulted by production code. */
    private static Object nativeSableForcePipeline;

    @GameTest(template="empty",batch="worldseam.real_external_mods",timeoutTicks=160)
    public static void loadRealSableCreateAndAeronautics(GameTestHelper helper) {
        if(!Boolean.getBoolean("worldseam.requireExternalMods")) {
            helper.succeed();
            return;
        }
        ModList mods=ModList.get();
        if(!mods.isLoaded("sable") || !mods.isLoaded("create")) {
            helper.fail("REAL MOD INTEGRATION FAIL: missing installed Sable or Create 6");
            return;
        }
        boolean aeronauticsPresent=mods.getMods().stream().anyMatch(info->
            info.getModId().toLowerCase(java.util.Locale.ROOT).contains("aero") ||
            info.getModId().toLowerCase(java.util.Locale.ROOT).contains("simulated") ||
            info.getDisplayName().toLowerCase(java.util.Locale.ROOT).contains("aeronautics"));
        if(!aeronauticsPresent) {
            helper.fail("REAL MOD INTEGRATION FAIL: Aeronautics bundled jar not loaded");
            return;
        }
        var server=helper.getLevel().getServer();
        SeamSableObserver.tick(server,10,true);
        var report=SeamSableObserver.snapshot();
        if(!report.installed() || !report.compatible()) {
            helper.fail("REAL MOD INTEGRATION FAIL: Sable API mismatch: "+
                SeamSableObserver.problem());
            return;
        }
        com.mojang.logging.LogUtils.getLogger().info(
            "WORLDSEAM_REAL_EXTERNAL_MODS_PASS: verified released Sable/Create/Aeronautics loaded; Sable sublevel reflection compatible, knownActiveShips={}, straddledShips={}, physicsBridgeActive={}",
            report.liveBodies(),report.straddlingBodies(),report.physicsBridgeActive());
        // The absence of assembled ships must never be reported as "ship
        // physics compatibility passed". It proves ONLY actual API loading.
        helper.succeed();
    }
}
