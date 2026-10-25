package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.HomeEntranceRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** An outdoor resident rounding a facade must reach the actual door, not
 * target an indoor tile across an impassable wall. No production teleport.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HouseCornerEntranceGameTests {
    private HouseCornerEntranceGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 330)
    public static void outdoor_resident_rounds_wall_opens_door_and_enters(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(15, 4, 15));
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) {
                BlockPos at = center.offset(x, 0, z);
                level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y <= 3; y++)
                    level.setBlock(at.above(y), Blocks.AIR.defaultBlockState(), 3);
            }
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) != 3 && Math.abs(z) != 3) continue;
                for (int y = 0; y < 3; y++)
                    level.setBlock(center.offset(x, y, z),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        BlockPos door = center.south(3);
        level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);

        BlockPos outside = center.south(4);
        BlockPos inside = center.south(2);
        // Outside the EAST facade but on the indoor side of the SOUTH
        // doorway's plane. Euclidean nearest-side selection is misleading.
        BlockPos start = center.east(5).south();
        HomeEntranceRoute.Entrance entry =
            new HomeEntranceRoute.Entrance(outside, inside);
        if (!HomeEntranceRoute.nextDoorStage(entry, start).equals(inside))
            throw new AssertionError("Fixture must reproduce misleading indoor stage");

        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC registration missing");
        npc.setMode(PlayerNpcEntity.Mode.STAY);
        npc.setHomePos(center.north());
        npc.moveTo(start.getX() + 0.5, start.getY(),
            start.getZ() + 0.5, 0, 0);
        if (!level.addFreshEntity(npc))
            throw new AssertionError("Cannot spawn physical NPC");

        boolean[] opened = {false};
        boolean[] entered = {false};
        boolean[] walked = {false};
        Vec3[] previous = {npc.position()};
        for (int age = 2; age <= 270; age += 5)
            helper.runAfterDelay(age, () -> {
                if (!npc.isAlive())
                    helper.fail("Resident vanished on corner route", start);
                if (npc.position().distanceToSqr(previous[0]) > 16)
                    helper.fail("Resident jumped rather than walking", npc.blockPosition());
                if (npc.position().distanceToSqr(previous[0]) > 0.01)
                    walked[0] = true;
                previous[0] = npc.position();
                if (level.getBlockState(door).getValue(DoorBlock.OPEN))
                    opened[0] = true;
                if (npc.blockPosition().distSqr(inside) <= 1)
                    entered[0] = true;
                if (!entered[0] && age % 10 == 2)
                    HomeEntranceRoute.moveTowardEntrance(npc, level,
                        center.north(), 1.0);
            });
        helper.runAfterDelay(290, () -> {
            if (!walked[0] || !opened[0] || !entered[0])
                helper.fail("Side-wall route failed to physically enter wooden door: "
                    + "pos=" + npc.blockPosition() + " walked=" + walked[0]
                    + " opened=" + opened[0] + " entered=" + entered[0]
                    + " action=" + npc.lastAction(), npc.blockPosition());
            else helper.succeed();
        });
    }
}
