package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.RoadNavigator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native A* must not report an impassable facade as an active commute.
 * All movement is by the real entity navigation, never setPos/teleport.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoadNavigatorGameTests {
    private RoadNavigatorGameTests() {}

    @GameTest(template="empty",timeoutTicks=180)
    public static void solid_wall_rejects_ghost_route_then_open_passage_allows_walking(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(15, 4, 15));
        for (int x=-12; x<=12; x++)
            for (int z=-12; z<=12; z++) {
                level.setBlock(center.offset(x,-1,z), Blocks.STONE.defaultBlockState(), 3);
                for (int y=0; y<=3; y++)
                    level.setBlock(center.offset(x,y,z), Blocks.AIR.defaultBlockState(), 3);
            }
        // A complete 25-wide, three-high physical stone partition; no
        // navigable way around it on the prepared floor.
        for (int z=-12; z<=12; z++)
            for (int y=0; y<=2; y++)
                level.setBlock(center.offset(0,y,z), Blocks.STONE.defaultBlockState(), 3);
        BlockPos start = center.west(2);
        BlockPos goal = center.east(4);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC entity not registered");
        npc.setMode(PlayerNpcEntity.Mode.STAY);
        npc.setHomePos(start);
        npc.moveTo(start.getX()+0.5, start.getY(), start.getZ()+0.5, 0, 0);
        if (!level.addFreshEntity(npc))
            throw new AssertionError("Cannot spawn real pathfinding NPC");
        helper.runAfterDelay(2, () -> {
            if (RoadNavigator.moveToward(npc, level, goal, 1.0))
                helper.fail("Unreachable solid-wall target falsely accepted as a commute", start);
            if (!npc.getNavigation().isDone())
                helper.fail("Unreachable wall started a ghost active navigation path", start);
            // A genuine two-high passage now connects the same locations.
            level.setBlock(center, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(center.above(), Blocks.AIR.defaultBlockState(), 3);
            if (!RoadNavigator.moveToward(npc, level, goal, 1.0))
                helper.fail("Native A* refused the newly open physical passage", start);
        });
        Vec3[] previous = {Vec3.atCenterOf(start)};
        helper.runAfterDelay(5, () -> previous[0] = npc.position());
        for (int age=10; age<=100; age+=10)
            helper.runAfterDelay(age, () -> {
                if (npc.position().distanceToSqr(previous[0]) > 49.0)
                    helper.fail("NPC jumped instead of walking", npc.blockPosition());
                previous[0] = npc.position();
            });
        helper.runAfterDelay(120, () -> {
            if (!npc.isAlive() || npc.isRemoved()
                || npc.blockPosition().distSqr(goal) > 4)
                helper.fail("NPC never walked through the opened passage: "
                    + npc.blockPosition().toShortString() + " action=" + npc.lastAction(),
                    npc.blockPosition());
            else helper.succeed();
        });
    }
}
