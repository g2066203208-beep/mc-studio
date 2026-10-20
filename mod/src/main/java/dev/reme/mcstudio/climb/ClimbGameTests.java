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

/** Three in-game smoke tests for PEAK style continuous grab and terrain geometry. */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClimbGameTests {
    private ClimbGameTests() {}

    @GameTest(template="climb_test", timeoutTicks=80)
    public static void allSixGrabbableDirections(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos at = helper.absolutePos(new BlockPos(3,2,3));
        assertFace(helper,player,at,new BlockPos(3,3,5),0,0);
        assertFace(helper,player,at,new BlockPos(3,3,1),180,0);
        assertFace(helper,player,at,new BlockPos(5,3,3),-90,0);
        assertFace(helper,player,at,new BlockPos(1,3,3),90,0);
        assertFace(helper,player,at,new BlockPos(3,4,3),0,-90);
        assertFace(helper,player,at,new BlockPos(3,1,3),0,90);
        helper.succeed();
    }

    @GameTest(template="climb_test", timeoutTicks=80)
    public static void holdLmbClimbsAndReleaseFalls(GameTestHelper helper) {
        BlockPos wall = new BlockPos(3,3,5);
        helper.setBlock(wall, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos origin = helper.absolutePos(new BlockPos(3,2,3));
        player.moveTo(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5,0,0);
        ClimbState.remove(player);
        ClimbState state = ClimbState.of(player);
        state.stamina = 100f;

        ClimbRuntime.accept(player,1,-1,true,false,1,0);
        helper.assertTrue(state.peakHeld && state.gripping(), "Holding LMB didn't grab wall");
        double initialHeight = player.getY();
        ClimbMotion.tick(player,state);
        helper.assertTrue(state.stamina < 100, "Climbing didn't consume stamina");
        helper.assertTrue(player.getDeltaMovement().y > 0, "W must impart upward velocity along wall");
        helper.assertTrue(player.getY() == initialHeight, "Climb controller teleported player rather than setting velocity");
        ClimbRuntime.accept(player,2,-1,false,false,0,0);
        helper.assertTrue(!state.peakHeld && !state.gripping(), "LMB release left character attached");
        ClimbState.remove(player);
        helper.succeed();
    }

    @GameTest(template="climb_test", timeoutTicks=80)
    public static void shiftLungeCostAndAutomaticStamina(GameTestHelper helper) {
        BlockPos wall = new BlockPos(3,3,5);
        helper.setBlock(wall,Blocks.STONE);
        Player player=helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos origin=helper.absolutePos(new BlockPos(3,2,3));
        player.moveTo(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5,0,0);
        ClimbState.remove(player);
        ClimbState s=ClimbState.of(player);
        ClimbRuntime.accept(player,1,-1,true,false,1,0);
        helper.assertTrue(s.gripping(),"Wall grip is required for lunge");
        ClimbRuntime.accept(player,2,-1,true,true,1,0);
        helper.assertTrue(s.lungeRequested,"SHIFT rising-edge should trigger one lunge");
        ClimbMotion.tick(player,s);
        helper.assertTrue(s.lungeCooldown>0, "Lunge cooldown did not begin");
        helper.assertTrue(s.stamina <= PeakGripRules.MAX_STAMINA-PeakGripRules.LUNGE_COST,
            "SHIFT lunge must expend stamina");
        helper.assertTrue(!s.lungeRequested,"Lunge edge was not consumed");

        helper.assertTrue(ClimbMotion.desiredTangent(new Vec3(0,0,-1),0,1,0).y>0.95,
            "W does not move upward on wall");
        helper.assertTrue(ClimbMotion.desiredTangent(new Vec3(0,-1,0),0,1,0).z>0.95,
            "W does not move forward while grabbing ceiling");
        helper.assertTrue(ClimbMotion.desiredTangent(new Vec3(0,1,0),0,1,0).z>0.95,
            "W does not move forward over floor");
        ClimbState.remove(player);
        helper.succeed();
    }

    private static void assertFace(GameTestHelper helper, Player mock, BlockPos origin,
                                   BlockPos surface, float yaw,float pitch) {
        helper.setBlock(surface,Blocks.STONE);
        mock.moveTo(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5,yaw,pitch);
        GripAnchor hit=SurfaceProbe.trace(mock);
        helper.assertTrue(hit!=null && hit.block().equals(helper.absolutePos(surface)),
            "Wrong grab surface: "+surface);
        helper.setBlock(surface,Blocks.AIR);
    }
}
