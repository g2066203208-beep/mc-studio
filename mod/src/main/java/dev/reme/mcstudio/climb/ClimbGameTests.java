package dev.reme.mcstudio.climb;

import dev.reme.mcstudio.StudioMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-engine collision probe smoke tests. These run only in a GameTest-enabled
 * world/test server, not during normal survival gameplay.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClimbGameTests {
    private ClimbGameTests() {}

    @GameTest(template = "climb_test", timeoutTicks = 80)
    public static void sixSurfaceDirections(GameTestHelper helper) {
        Player mock = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos player = helper.absolutePos(new BlockPos(3, 2, 3));

        // +Z, -Z, +X, -X, +Y (ceiling), -Y (floor).
        assertFace(helper, mock, player, new BlockPos(3, 3, 5), 0, 0);
        assertFace(helper, mock, player, new BlockPos(3, 3, 1), 180, 0);
        assertFace(helper, mock, player, new BlockPos(5, 3, 3), -90, 0);
        assertFace(helper, mock, player, new BlockPos(1, 3, 3), 90, 0);
        assertFace(helper, mock, player, new BlockPos(3, 4, 3), 0, -90);
        assertFace(helper, mock, player, new BlockPos(3, 1, 3), 0, 90);
        helper.succeed();
    }

    private static void assertFace(GameTestHelper helper, Player mock, BlockPos playerPos,
                                   BlockPos surface, float yaw, float pitch) {
        helper.setBlock(surface, Blocks.STONE);
        mock.moveTo(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5, yaw, pitch);
        GripAnchor candidate = SurfaceProbe.trace(mock);
        helper.assertTrue(candidate != null, "Missing grip at face " + surface);
        helper.assertTrue(candidate.block().equals(helper.absolutePos(surface)),
                "Hit incorrect surface " + surface + ": " + candidate.block());
        helper.setBlock(surface, Blocks.AIR);
    }
}
