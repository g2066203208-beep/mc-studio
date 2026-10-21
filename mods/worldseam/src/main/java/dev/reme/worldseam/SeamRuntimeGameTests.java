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
        if (!SeamGraph.mutuallyConnected()) {
            helper.fail("Bidirectional seam topology is inconsistent");
            return;
        }
        if (SeamBounds.top(server.getLevel(Level.NETHER)) != 128) {
            helper.fail("Nether roof seam is not at Y=128");
            return;
        }
        var options = SeamSettings.get(server);
        if (options.enabled() || options.carveNewChunks()) {
            helper.fail("WorldSeam or terrain carving started without explicit opt-in");
            return;
        }
        helper.succeed();
    }
}
