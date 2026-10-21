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
        helper.succeed();
    }
}
