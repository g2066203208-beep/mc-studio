package dev.reme.worldseam;

import dev.reme.worldseam.WorldSeamMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * An actual Minecraft GameTest server integration smoke test.
 * Does not simulate a client/render backend or claim frame continuity.
 */
@GameTestHolder(WorldSeamMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeamRuntimeGameTests {
    private SeamRuntimeGameTests() {}

    @GameTest(template = "empty", batch = "worldseam.vertical_seams", timeoutTicks = 100)
    public static void dimensionsLoadedAndOptInOff(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        if (server.getLevel(Level.OVERWORLD) == null ||
            server.getLevel(Level.NETHER) == null ||
            server.getLevel(Level.END) == null) {
            helper.fail("WorldSeam requires all three vanilla dimensions");
            return;
        }
        var over=server.getLevel(Level.OVERWORLD);
        var nether=server.getLevel(Level.NETHER);
        var end=server.getLevel(Level.END);
        if (over.getMinBuildHeight()!=-64 || over.getMaxBuildHeight()!=320 ||
            nether.getMinBuildHeight()!=0 || nether.getMaxBuildHeight()!=256 ||
            end.getMinBuildHeight()!=0 || end.getMaxBuildHeight()!=256) {
            helper.fail("Actual vanilla 1.21.1 dimension build height mismatch: "+
                "Overworld ["+over.getMinBuildHeight()+","+over.getMaxBuildHeight()+"), "+
                "Nether ["+nether.getMinBuildHeight()+","+nether.getMaxBuildHeight()+"), "+
                "End ["+end.getMinBuildHeight()+","+end.getMaxBuildHeight()+")");
            return;
        }
        var floor=SeamGraph.from("minecraft:overworld").stream()
            .filter(link -> link.exit()==SeamGraph.EdgeSide.BOTTOM)
            .findFirst().orElseThrow();
        var roof=SeamGraph.from("minecraft:the_nether").stream()
            .filter(link -> link.exit()==SeamGraph.EdgeSide.TOP)
            .findFirst().orElseThrow();
        double mappedDown=floor.targetY(-65,-64,128);
        double mappedUp=roof.targetY(127,128,-64);
        if (mappedDown!=127 || mappedUp!=-65) {
            helper.fail("Physical seam mismatch: Overworld Y=-65 must be "+
                "Nether Y=127, reverse mapped to Overworld Y=-65");
            return;
        }
        if (!SeamGraph.mutuallyConnected()) {
            helper.fail("Bidirectional seam topology is inconsistent");
            return;
        }
        if (SeamBounds.top(server.getLevel(Level.NETHER)) != 128) {
            helper.fail("Nether roof seam is not at Y=128");
            return;
        }
        // New world topology is Overworld > Nether > End, and reciprocal
        // links are generated from the saved stack, not assumed from old CI.
        SeamStack defaults=SeamStack.defaults();
        var order=defaults.entries().stream().map(SeamStack.Entry::id).toList();
        if(!order.equals(java.util.List.of(
            "minecraft:overworld","minecraft:the_nether","minecraft:the_end"))) {
            helper.fail("New-world default stack must be Overworld > Nether > End: "+order);
            return;
        }
        var toEnd=defaults.from("minecraft:the_nether").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.BOTTOM)
            .findFirst().orElseThrow();
        var toNether=defaults.from("minecraft:the_end").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.TOP)
            .findFirst().orElseThrow();
        if(!toEnd.target().equals("minecraft:the_end") ||
           !toNether.target().equals("minecraft:the_nether") ||
           toEnd.targetY(-0.5,nether.getMinBuildHeight(),
               end.getMaxBuildHeight())!=255.5 ||
           toNether.targetY(256.5,end.getMaxBuildHeight(),
               nether.getMinBuildHeight())!=0.5) {
            helper.fail("Nether bottom ↔ End top mapping must preserve 1:1 Y");
            return;
        }
        // Old save snapshots must retain their explicitly stored historical
        // order: a software upgrade cannot silently move someone's End.
        var oldSave=new SeamStack(java.util.List.of(
            new SeamStack.Entry("minecraft:the_end",null,null,true,true),
            new SeamStack.Entry("minecraft:overworld",null,null,true,true),
            new SeamStack.Entry("minecraft:the_nether",null,128,true,true)),false);
        if(!SeamStackStorage.read(SeamStackStorage.save(oldSave)).equals(oldSave)) {
            helper.fail("Existing old-topology save must retain its order");
            return;
        }
        com.mojang.logging.LogUtils.getLogger().info(
            "WORLDSEAM_STACK_V094_PASS: new defaults Overworld > Nether > End; saved historical order roundtrips");
        var options = SeamSettings.get(server);
        if (options.enabled() || options.carveNewChunks() || options.experimentalNoRespawn()) {
            helper.fail("WorldSeam or terrain carving started without explicit opt-in");
            return;
        }
        options.setEnabled(true);
        try {
            if (!options.experimentalNoRespawn()) {
                helper.fail("Enabling WorldSeam must arm no-respawn instead of vanilla loading");
                return;
            }
        } finally {
            options.setEnabled(false);
            options.setExperimentalNoRespawn(false);
            options.setCarveNewChunks(false);
        }
        var preset=new SeamStack(java.util.List.of(
            new SeamStack.Entry("minecraft:the_end",null,null,true,true),
            new SeamStack.Entry("minecraft:overworld",-64,290,true,true),
            new SeamStack.Entry("minecraft:the_nether",null,128,true,true)
        ),false);
        if (!SeamStackStorage.read(SeamStackStorage.save(preset)).equals(preset)) {
            helper.fail("WorldSeam NBT persistence fails to retain ordered stack/Y overrides");
            return;
        }
        try {
            SeamStackStorage.validate(preset,server);
        } catch(IllegalArgumentException ex) {
            helper.fail("WorldSeam vanilla stack runtime validation failed: "+ex.getMessage());
            return;
        }
        // Test REAL two-ServerLevel explosion propagation in an isolated
        // GameTest world: Overworld floor -> Nether roof. No synthetic
        // block-copy implementation, no unrelated aviation mod.
        var sourcePos=helper.absolutePos(new net.minecraft.core.BlockPos(0,1,0));
        int x=sourcePos.getX(),z=sourcePos.getZ();
        int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);
        var sourceRoof=new net.minecraft.core.BlockPos(x,-64,z);
        var remoteWitness=new net.minecraft.core.BlockPos(x,127,z);
        var remoteAir=new net.minecraft.core.BlockPos(x,128,z);
        try {
            // The explosion bridge NEVER force-loads destination chunks.
            // Both destination windows must be prewarmed for real block damage.
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                nether.getChunk(cx+dx,cz+dz);
            over.setBlock(sourceRoof,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            // Use low-resistance blocks, never bedrock, for a deterministic
            // explosion line of sight at the already-loaded target roof.
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                var at=remoteWitness.offset(dx,0,dz);
                nether.setBlock(at,
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                nether.setBlock(at.above(),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            }
            nether.setBlock(remoteWitness,
                net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(),3);

            // Actual REAL ServerLevel collision checks on BOTH sides while
            // one native entity AABB straddles the Nether Y128 -> OW Y-64
            // plane. Nether's hidden native Y128 blocks MUST NOT be allowed
            // to collide with the body belonging to the Overworld there.
            int px=x+5,pz=z+5;
            int pcx=Math.floorDiv(px,16),pcz=Math.floorDiv(pz,16);
            over.getChunk(pcx,pcz);
            nether.getChunk(pcx,pcz);
            var pig=helper.spawn(net.minecraft.world.entity.EntityType.PIG,
                new net.minecraft.core.BlockPos(1,1,1));
            var down=SeamStack.defaults().from("minecraft:overworld").stream()
                .filter(l->l.exit()==SeamGraph.EdgeSide.BOTTOM)
                .findFirst().orElseThrow();
            var pos=new net.minecraft.core.BlockPos(px,-64,pz);
            var netherPos=new net.minecraft.core.BlockPos(px,127,pz);
            for(int y=-64;y<=-61;y++)
                over.setBlock(pos.atY(y),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            nether.setBlock(netherPos,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            // This native block exists ABOVE THE LOGICAL ROOF, but its
            // physical space is owned by Overworld at the contact plane.
            nether.setBlock(netherPos.above(),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            if(!SeamArrival.hasJoinedClearance(over,nether,down,-64,128,
                px+.5,127.875,pz+.5,pig)) {
                helper.fail("Joined collision rejected clear OW+Nether halves because of unrelated native Nether Y128 stone");
                return;
            }
            over.setBlock(pos,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            if(SeamArrival.hasJoinedClearance(over,nether,down,-64,128,
                px+.5,127.875,pz+.5,pig)) {
                helper.fail("Joined collision ignored real OW floor solid touching top of straddling entity");
                return;
            }
            over.setBlock(pos,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            nether.setBlock(netherPos,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            if(SeamArrival.hasJoinedClearance(over,nether,down,-64,128,
                px+.5,127.875,pz+.5,pig)) {
                helper.fail("Joined collision ignored real Nether roof solid under straddling entity");
                return;
            }
            nether.setBlock(netherPos,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            pig.discard();
            com.mojang.logging.LogUtils.getLogger().info(
                "WORLDSEAM_JOINED_VOXEL_COLLISION_PASS: single entity envelope tested against TWO native ServerLevels; hidden Nether Y128 terrain excluded");

            options.setEnabled(true);
            options.setCrossSeamExplosions(true);
            over.explode(null,x+0.5,-63.75,z+0.5,4.0f,
                net.minecraft.world.level.Level.ExplosionInteraction.TNT);
            if(!nether.getBlockState(remoteWitness).isAir()) {
                helper.fail("Cross-seam native explosion did not destroy a real Nether glass block from Overworld");
                return;
            }
            com.mojang.logging.LogUtils.getLogger().info(
                "WORLDSEAM_CROSS_BLAST_PASS: Overworld floor TNT equivalent destroyed the Nether roof witness");
            // Reverse direction on the REAL second ServerLevel: a Nether
            // roof blast destroys a block belonging to the Overworld floor.
            // Keep both 3x3 windows fully loaded inside the disposable test.
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                over.getChunk(cx+dx,cz+dz);
            var reverseWitness=sourceRoof.offset(1,0,0);
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
                nether.setBlock(remoteWitness.offset(dx,0,dz),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                over.setBlock(sourceRoof.offset(dx,0,dz),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            }
            over.setBlock(reverseWitness,
                net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(),3);
            nether.explode(null,x+0.5,127.5,z+0.5,4.0f,
                net.minecraft.world.level.Level.ExplosionInteraction.TNT);
            if(!over.getBlockState(reverseWitness).isAir()) {
                helper.fail("Reverse Nether roof blast did not destroy real Overworld floor glass");
                return;
            }
            com.mojang.logging.LogUtils.getLogger().info(
                "WORLDSEAM_CROSS_BLAST_PASS_REVERSE: Nether roof blast destroyed Overworld glass");

        } finally {
            options.setCrossSeamExplosions(false);
            options.setEnabled(false);
        }
        helper.succeed();
    }
}
