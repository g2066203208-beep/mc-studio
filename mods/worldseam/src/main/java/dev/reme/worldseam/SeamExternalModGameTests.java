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
            var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);
            helper.assertTrue(nether!=null,
                "A real Sable ship cannot enter a missing destination dimension");
            com.mojang.logging.LogUtils.getLogger().info(
                "WORLDSEAM_REAL_SABLE_STRADDLE_OBSERVED: uuid={}, totalLiveHulls={}, straddling={}, shipDrivenRemoteRegionTickets={}, physicsBridgeActive={}",
                actualShip,report.liveBodies(),report.straddlingBodies(),
                SeamShipResidency.active(),report.physicsBridgeActive());
            // This DOES NOT pass physics impulse, joints or dual world
            // renderer requirements. This is a narrow real-assembly probe.
        });
    }

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
