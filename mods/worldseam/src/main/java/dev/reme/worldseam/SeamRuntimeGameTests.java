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
            // The explosion handler refuses to wake 9 unloaded target chunks
            // on its own; a real prewarmed target needs to be present.
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
        } finally {
            options.setCrossSeamExplosions(false);
            options.setEnabled(false);
        }
        helper.succeed();
    }
}
