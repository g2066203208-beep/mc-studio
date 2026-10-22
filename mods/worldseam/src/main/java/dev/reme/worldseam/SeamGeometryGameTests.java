package dev.reme.worldseam;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Minecraft's AABB lives in NeoForge's real game runtime, NOT on the isolated
 * pure-JUnit test classpath. Exercise the two-chart geometry in GameTestServer.
 */
@GameTestHolder(WorldSeamMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeamGeometryGameTests {
    private SeamGeometryGameTests() {}

    private static boolean near(double a,double b) {
        return Math.abs(a-b)<1.e-7;
    }

    @GameTest(template="empty",batch="worldseam.geometry",timeoutTicks=100)
    public static void realAabbSplitOverworldNether(GameTestHelper h) {
        var box=new AABB(-0.3,127.875,-0.3,0.3,129.675,0.3);
        var s=SeamBodySplit.atTargetBoundary(box,SeamGraph.EdgeSide.TOP,128,-64);
        if(s.targetPart()==null||s.sourcePart()==null ||
           !near(s.targetPart().minY,127.875) ||
           !near(s.targetPart().maxY,128) ||
           !near(s.sourcePart().minY,-64) ||
           !near(s.sourcePart().maxY,-62.325)) {
            h.fail("Real AABB Nether logical roof failed to split into Overworld floor");
            return;
        }
        var reverse=SeamBodySplit.atTargetBoundary(
            new AABB(-.3,-64.5,-.3,.3,-62.7,.3),
            SeamGraph.EdgeSide.BOTTOM,-64,128);
        if(reverse.targetPart()==null||reverse.sourcePart()==null||
           !near(reverse.targetPart().minY,-64)||
           !near(reverse.sourcePart().minY,127.5)||
           !near(reverse.sourcePart().maxY,128)) {
            h.fail("Real AABB inverse clip of Overworld floor vs Nether roof failed");
            return;
        }
        var no=SeamBodySplit.atTargetBoundary(
            new AABB(0,-63.8,0,1,-62,1),SeamGraph.EdgeSide.BOTTOM,-64,128);
        if(no.sourcePart()!=null||no.targetPart()==null) {
            h.fail("Non-straddling AABB falsely acquired an image");
            return;
        }
        var custom=SeamBodySplit.atTargetBoundary(
            new AABB(-11,204,-16,-10,211,-15),
            SeamGraph.EdgeSide.TOP,209,37);
        if(custom.targetPart()==null||custom.sourcePart()==null||
           !near(custom.targetPart().maxY,209)||
           !near(custom.sourcePart().minY,37)||
           !near(custom.sourcePart().maxY,39)) {
            h.fail("Custom Y, unaligned 1:1 seam clip failed");
            return;
        }
        com.mojang.logging.LogUtils.getLogger().info(
            "WORLDSEAM_GEOMETRY_GAME_TEST_PASS: real AABBs clipped at both logical planes and arbitrary Y");
        h.succeed();
    }

    @GameTest(template="empty",batch="worldseam.geometry",timeoutTicks=100)
    public static void real64WideOneShipTwoChartProjection(GameTestHelper h) {
        var down=SeamStack.defaults().from("minecraft:overworld").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.BOTTOM).findFirst().orElseThrow();
        var up=SeamStack.defaults().from("minecraft:the_nether").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.TOP).findFirst().orElseThrow();
        var large=new AABB(-32,-70,-8,32,-57,8);
        var split=SeamShipChartBounds.project(large,down,-64,128);
        if(!split.straddles() || split.homePart()==null ||
           split.neighboringPart()==null ||
           !near(split.homePart().minY,-64) ||
           !near(split.homePart().maxY,-57) ||
           !near(split.neighboringPart().minY,122) ||
           !near(split.neighboringPart().maxY,128) ||
           !near(split.neighboringPart().minX,-32) ||
           !near(split.neighboringPart().maxX,32)) {
            h.fail("64-block physical ship bounds did not clip exactly into two chart hulls");
            return;
        }
        var body=new AABB(10,-67,10,18,-59,18);
        var forward=SeamShipChartBounds.project(body,down,-64,128);
        var backward=SeamShipChartBounds.project(body.move(0,192,0),up,128,-64);
        if(!forward.straddles()||!backward.straddles()||
           !forward.homePart().equals(backward.neighboringPart())||
           !forward.neighboringPart().equals(backward.homePart())) {
            h.fail("Single physical ship's two chart projections are not inverse");
            return;
        }
        var allHome=SeamShipChartBounds.project(
            new AABB(0,-60,0,2,-56,2),down,-64,128);
        if(allHome.straddles()||allHome.homePart()==null||
           allHome.neighboringPart()!=null) {
            h.fail("Single-chart hull falsely generated a second-image collider");
            return;
        }
        com.mojang.logging.LogUtils.getLogger().info(
            "WORLDSEAM_SHIP_CHART_GAME_TEST_PASS: native MC AABB 64-wide hull, inverse chart planes, no cloned body");
        h.succeed();
    }
    @GameTest(template="empty",batch="worldseam.native_terrain_probe",timeoutTicks=200)
    public static void remoteNativeVoxelContactNeverConfusesLogicalNetherRoof(GameTestHelper h) {
        var nether=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if(nether==null){h.fail("Missing real Nether ServerLevel");return;}
        var center=h.absolutePos(new net.minecraft.core.BlockPos(1,1,1));
        int x=center.getX(),z=center.getZ();
        // It is legitimate to synchronously prepare a TEST fixture at
        // setup time. Runtime probe itself must NEVER generate chunks.
        nether.getChunk(Math.floorDiv(x,16),Math.floorDiv(z,16));
        var at=new net.minecraft.core.BlockPos(x,127,z);
        var hidden=at.above();
        var old=nether.getBlockState(at);
        var oldHidden=nether.getBlockState(hidden);
        var image=new AABB(x+0.2,127.25,z+0.2,x+0.8,127.4,z+0.8);
        try {
            nether.setBlock(at,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            nether.setBlock(hidden,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            var clear=SeamShipRemoteContactProbe.scan(nether,image);
            if(!clear.complete()||clear.hasCandidate()) {
                h.fail("Hidden Nether Y128 blocks incorrectly treated as physical contact in logical Nether top at 128");
                return;
            }
            // A FULL native stone at Y127 must be a candidate for ONE
            // real ship whose other half is in the Overworld.
            nether.setBlock(at,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            var solid=SeamShipRemoteContactProbe.scan(nether,image);
            if(!solid.complete()||solid.solidCandidates()!=1) {
                h.fail("Failed to read real Nether Y127 collision voxels");
                return;
            }
            // Test Minecraft's actual slab VoxelShape against sub-block
            // placement, not just BlockState.isSolid or canOcclude.
            var topSlab=net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                    net.minecraft.world.level.block.state.properties.SlabType.TOP);
            nether.setBlock(at,topSlab,3);
            if(SeamShipRemoteContactProbe.scan(nether,image).hasCandidate()) {
                h.fail("Top half-slab outside hull incorrectly counted as contact");
                return;
            }
            var bottomSlab=net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                    net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
            nether.setBlock(at,bottomSlab,3);
            if(!SeamShipRemoteContactProbe.scan(nether,image).hasCandidate()) {
                h.fail("Bottom half-slab VoxelShape was not sampled");
                return;
            }
            var huge=new AABB(x-90,125,z-90,x+90,128,z+90);
            if(!SeamShipRemoteContactProbe.scan(nether,huge).truncated()) {
                h.fail("Oversize ship broadphase must be bounded, never claim unloaded area is clear");
                return;
            }
            var far=new AABB(x+80000,127,z+80000,x+80001,128,z+80001);
            var missing=SeamShipRemoteContactProbe.scan(nether,far);
            if(missing.complete()||missing.missingChunks()==0) {
                h.fail("Missing far native chunks must be reported, never force loaded or treated as air");
                return;
            }
            com.mojang.logging.LogUtils.getLogger().info(
                "WORLDSEAM_REAL_REMOTE_VOXEL_CONTACT_PASS: loaded native Nether stone, upper/lower slab VoxelShape, ignore hidden Nether Y128, no synchronous loads");
            h.succeed();
        } finally {
            nether.setBlock(at,old,3);
            nether.setBlock(hidden,oldHidden,3);
        }
    }

    @GameTest(template="empty",batch="worldseam.native_server_join",timeoutTicks=180)
    public static void realServerBlockAtlasCrossesLogicalPlaneWithoutPhantomAir(GameTestHelper h) {
        var server=h.getLevel().getServer();
        var home=server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);
        if(home==null||nether==null){h.fail("Missing real vanilla levels");return;}
        var down=SeamStack.defaults().from("minecraft:overworld").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.BOTTOM)
            .findFirst().orElseThrow();
        var up=SeamStack.defaults().from("minecraft:the_nether").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.TOP)
            .findFirst().orElseThrow();
        var world=h.absolutePos(new net.minecraft.core.BlockPos(2,1,2));
        int x=world.getX(),z=world.getZ();
        nether.getChunk(Math.floorDiv(x,16),Math.floorDiv(z,16));
        home.getChunk(Math.floorDiv(x,16),Math.floorDiv(z,16));
        var netherBlock=new net.minecraft.core.BlockPos(x,127,z);
        var homeBlock=new net.minecraft.core.BlockPos(x,-64,z);
        var oldNether=nether.getBlockState(netherBlock);
        var oldHidden=nether.getBlockState(netherBlock.above());
        var oldHome=home.getBlockState(homeBlock);
        var overView=new SeamJoinedServerBlockView(home,nether,down);
        var netherView=new SeamJoinedServerBlockView(nether,home,up);
        try {
            home.setBlock(homeBlock,
                net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
            nether.setBlock(netherBlock,
                net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(),3);
            nether.setBlock(netherBlock.above(),
                net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(),3);
            if(overView.homePlane()!=-64 || overView.neighborPlane()!=128 ||
               overView.getBlockState(new net.minecraft.core.BlockPos(x,-65,z))
                   .getBlock()!=net.minecraft.world.level.block.Blocks.GOLD_BLOCK ||
               netherView.getBlockState(new net.minecraft.core.BlockPos(x,128,z))
                   .getBlock()!=net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK ||
               overView.getBlockState(homeBlock)
                   .getBlock()!=net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK) {
                h.fail("Two native levels did not agree on exact 1:1 joining of Overworld floor Y=-64 and Nether roof Y128");
                return;
            }
            // Native Nether storage is allowed to contain Y128 bedrock,
            // but that block is NEVER what the joined collider atlas reads.
            if(netherView.getBlockState(new net.minecraft.core.BlockPos(x,128,z))
                .getBlock()==net.minecraft.world.level.block.Blocks.BEDROCK) {
                h.fail("Nether hidden Y128 bedrock incorrectly shadowed native Overworld geometry");
                return;
            }
            nether.setBlock(netherBlock,
                net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(),3);
            if(overView.getFluidState(new net.minecraft.core.BlockPos(x,-65,z))
                .isEmpty()) {
                h.fail("Joined server BlockGetter lost genuine remote Nether fluid state");
                return;
            }
            // Strict no-load behavior: look up a definitely absent remote
            // chunk inside the world border and refuse phantom AIR.
            int missingX=x+100000;
            try {
                overView.getBlockState(new net.minecraft.core.BlockPos(missingX,-65,z));
                h.fail("Missing neighbor chunk was falsely assumed to be air");
                return;
            } catch(SeamJoinedServerBlockView.UnloadedNeighborChunkException expected) {
                // Exactly the required behavior for future Rapier baking.
            }
            com.mojang.logging.LogUtils.getLogger().info(
                "WORLDSEAM_NATIVE_SERVER_ATLAS_PASS: OW -65->Nether 127, Nether 128->OW -64, fluid crossing and unloaded chunk fail-closed");
            h.succeed();
        } finally {
            home.setBlock(homeBlock,oldHome,3);
            nether.setBlock(netherBlock,oldNether,3);
            nether.setBlock(netherBlock.above(),oldHidden,3);
        }
    }

}
