package dev.reme.mcstudio.climb;

import dev.reme.mcstudio.StudioMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
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
        // Regression: W must climb UP a vertical wall instead of being projected to zero.
        Vec3 upWall = ClimbMotion.desiredTangent(new Vec3(0, 0, -1), 0, 1, 0);
        helper.assertTrue(upWall.y > 0.95, "W does not ascend a front-facing wall");
        Vec3 acrossWall = ClimbMotion.desiredTangent(new Vec3(0, 0, -1), 0, 0, 1);
        helper.assertTrue(acrossWall.x < -0.95, "D does not traverse wall sideways");
        Vec3 ground = ClimbMotion.desiredTangent(new Vec3(0, 1, 0), 0, 1, 0);
        Vec3 ceiling = ClimbMotion.desiredTangent(new Vec3(0, -1, 0), 0, 1, 0);
        helper.assertTrue(ground.z > 0.95 && ceiling.z > 0.95,
                "W must move forward along floor/ceiling, not into them");
        Vec3 slopedNormal = new Vec3(0.5, 0.5, Math.sqrt(0.5)).normalize();
        Vec3 onSlope = ClimbMotion.desiredTangent(slopedNormal, 20, 1, 1);
        helper.assertTrue(Math.abs(onSlope.dot(slopedNormal)) < 1.0e-6,
                "Climb movement is not tangent on a sloped block");
        helper.succeed();
    }


    @GameTest(template = "climb_test", timeoutTicks = 80)
    public static void independentLeftAndRightHands(GameTestHelper helper) {
        Player mock = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos player = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos wall = new BlockPos(3, 3, 5);
        helper.setBlock(wall, Blocks.STONE);
        mock.moveTo(player.getX() + 0.5, player.getY(), player.getZ() + 0.5, 0, 0);

        // Both requests independently run their own server-side geometry check.
        ClimbRuntime.accept(mock, 1, 0, true, true, 0, 0);
        ClimbState state = ClimbState.of(mock);
        helper.assertTrue(state.left != null && state.right == null, "Left hand did not grip independently");

        ClimbRuntime.accept(mock, 2, 1, true, true, 0, 0);
        helper.assertTrue(state.left != null && state.right != null, "Both hands cannot grip at once");

        ClimbRuntime.accept(mock, 3, 0, false, true, 0, 0);
        helper.assertTrue(state.left == null && state.right != null,
                "Releasing left hand also released right");

        ClimbRuntime.accept(mock, 4, 0, false, true, 0, 0);
        helper.assertTrue(state.right != null, "Releasing an already-free hand changed the held grip");

        // Removing the actual block invalidates the remaining anchor.
        helper.setBlock(wall, Blocks.AIR);
        ClimbMotion.tick(mock, state);
        helper.assertTrue(!state.gripping(), "Grip persisted after source block was removed");
        ClimbState.remove(mock);
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
