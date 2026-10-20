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
    public static void holdRmbClimbsAndReleaseFalls(GameTestHelper helper) {
        BlockPos wall = new BlockPos(3,3,5);
        helper.setBlock(wall, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos origin = helper.absolutePos(new BlockPos(3,2,3));
        player.moveTo(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5,0,0);
        ClimbState.remove(player);
        ClimbState state = ClimbState.of(player);

        ClimbRuntime.accept(player,1,-1,true,false,1,0);
        helper.assertTrue(state.peakHeld && state.gripping(), "Holding RMB didn't grab wall");
        double initialHeight = player.getY();
        ClimbMotion.tick(player,state);
        helper.assertTrue(state.gripping(), "Grip released unexpectedly while RMB was held");
        helper.assertTrue(player.getDeltaMovement().y > 0, "W must impart upward velocity along wall");
        helper.assertTrue(player.getY() == initialHeight, "Climb controller teleported player rather than setting velocity");
        ClimbRuntime.accept(player,2,-1,false,false,0,0);
        helper.assertTrue(!state.peakHeld && !state.gripping(), "RMB release left character attached");
        ClimbState.remove(player);
        helper.succeed();
    }

    @GameTest(template="climb_test", timeoutTicks=80)
    public static void unlimitedGripAndShiftLungeCooldown(GameTestHelper helper) {
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
        helper.assertTrue(s.gripping(), "Shift lunge must keep grip even without stamina");
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

    @GameTest(template="climb_test", timeoutTicks=80)
    public static void physicalMantleChecksCollisionAndRelease(GameTestHelper helper) {
        BlockPos wall = new BlockPos(3,3,5);
        helper.setBlock(wall, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos origin = helper.absolutePos(new BlockPos(3,2,3));
        player.moveTo(origin.getX()+0.5, origin.getY()+0.95,
                origin.getZ()+0.5, 0, 0);
        BlockPos absoluteWall = helper.absolutePos(wall);
        GripAnchor grip = new GripAnchor(null, absoluteWall,
                new Vec3(absoluteWall.getX()+0.5,absoluteWall.getY()+0.8,absoluteWall.getZ()),
                new Vec3(0,0,-1));
        ClimbState.remove(player);
        ClimbState state = ClimbState.of(player);
        state.left = grip;
        state.peakHeld = true;
        state.enabled = true;
        state.forward = 1f;
        GripAnchor.Contact contact = grip.resolve(player.level());
        helper.assertTrue(contact != null, "The wall contact must be valid");
        helper.setBlock(wall.above(), Blocks.STONE);
        helper.assertTrue(!PeakLedgeAssist.start(player,state,contact),
                "Cannot mantle if the ledge is obstructed above");
        helper.setBlock(wall.above(), Blocks.AIR);
        helper.assertTrue(PeakLedgeAssist.start(player,state,contact),
                "Clear ledge should initiate PEAK mantle");
        helper.assertTrue(state.poseAction == 3 && state.mantleTicks == PeakLedgeAssist.DURATION,
                "Mantle must synchronize animation action to clients");
        PeakLedgeAssist.tick(player,state);
        helper.assertTrue(player.getDeltaMovement().y > 0,
                "Mantle must apply actual upward velocity rather than only an animation");
        // Full travel in a simulated player requires vanilla tick / collision handling;
        // the test checks transition state and finite completion, not a visual outcome.
        for(int i=1;i<PeakLedgeAssist.DURATION;i++) PeakLedgeAssist.tick(player,state);
        helper.assertTrue(!state.gripping() && state.mantleTicks == 0,
                "Mantle must end and release its anchor within the bounded transition");
        helper.assertTrue(state.mantleRequiresRelease,
                "Mantling should not immediately re-grip while RMB is still held");
        ClimbRuntime.accept(player, 2, -1, true, false, 1, 0);
        helper.assertTrue(!state.gripping(), "Held RMB re-gripped the wall after mantle completion");
        ClimbRuntime.accept(player, 3, -1, false, false, 0, 0);
        helper.assertTrue(!state.mantleRequiresRelease, "Releasing RMB must re-arm grabbing");
        ClimbState.remove(player);
        helper.setBlock(wall, Blocks.AIR);
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
