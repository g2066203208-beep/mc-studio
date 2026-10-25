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

/**
 * Two independent residents approaching a one-block-wide real home entrance.
 * This is a physical crowd regression: both must walk around solid walls,
 * open/pass the SAME wooden door, and continue into the room. Neither a
 * coordinate jump nor an action string is accepted as arrival.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrowdedHomeEntranceGameTests {
    private CrowdedHomeEntranceGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 640)
    public static void two_residents_physically_share_narrow_home_door(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(16, 4, 16));
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                BlockPos foot = center.offset(x, 0, z);
                level.setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y <= 3; y++)
                    level.setBlock(foot.above(y), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.abs(x) != 4 && Math.abs(z) != 4) continue;
                for (int y = 0; y < 3; y++)
                    level.setBlock(center.offset(x, y, z),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
        BlockPos door = center.south(4);
        level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);

        PlayerNpcEntity[] residents = new PlayerNpcEntity[2];
        BlockPos[] starts = {center.east(7).south(2), center.west(7).south(2)};
        BlockPos[] destinations = {center.north().east(2), center.north().west(2)};
        Vec3[] previous = new Vec3[2];
        boolean[] walked = new boolean[2];
        boolean[] entered = new boolean[2];
        boolean[] reachedRoom = new boolean[2];
        boolean[] doorOpened = {false};
        for (int i = 0; i < residents.length; i++) {
            PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
            if (npc == null) throw new AssertionError("Registered NPC missing");
            BlockPos start = starts[i];
            npc.setMode(PlayerNpcEntity.Mode.STAY);
            npc.setHomePos(center.north());
            npc.moveTo(start.getX() + 0.5, start.getY(),
                start.getZ() + 0.5, 0, 0); // initial TEST spawn only
            if (!level.addFreshEntity(npc))
                throw new AssertionError("Failed to spawn physical resident " + i);
            residents[i] = npc;
            previous[i] = npc.position();
        }

        for (int tick = 5; tick <= 540; tick += 5) {
            final int age = tick;
            helper.runAfterDelay(tick, () -> {
                if (level.getBlockState(door).getValue(DoorBlock.OPEN))
                    doorOpened[0] = true;
                for (int i = 0; i < residents.length; i++) {
                    PlayerNpcEntity npc = residents[i];
                    if (!npc.isAlive())
                        helper.fail("Crowded entrance resident disappeared", starts[i]);
                    double distance = npc.position().distanceToSqr(previous[i]);
                    if (distance > 9.0)
                        helper.fail("Resident jumped instead of walking through crowded entrance",
                            npc.blockPosition());
                    if (distance > 0.01) walked[i] = true;
                    previous[i] = npc.position();

                    BlockPos feet = npc.blockPosition();
                    boolean inHouse = Math.abs(feet.getX() - center.getX()) <= 3
                        && Math.abs(feet.getZ() - center.getZ()) <= 3
                        && feet.getY() == center.getY();
                    if (inHouse) entered[i] = true;
                    if (entered[i] && feet.distSqr(destinations[i]) <= 2)
                        reachedRoom[i] = true;

                    if (age % 10 == 5 && !entered[i]) {
                        HomeEntranceRoute.moveTowardEntrance(npc, level,
                            center.north(), 1.0);
                    } else if (entered[i] && !reachedRoom[i]
                            && age % 20 == 5) {
                        // After crossing, continue walking into a distinct
                        // interior aisle so the next citizen can pass.
                        BlockPos goal = destinations[i];
                        npc.getNavigation().moveTo(goal.getX() + 0.5,
                            goal.getY(), goal.getZ() + 0.5, 1.0);
                    }
                }
            });
        }
        helper.runAfterDelay(580, () -> {
            if (!doorOpened[0] || !walked[0] || !walked[1]
                || !entered[0] || !entered[1]
                || !reachedRoom[0] || !reachedRoom[1]) {
                helper.fail("Crowded real wooden entrance did not pass both walkers: "
                    + "opened=" + doorOpened[0]
                    + " walked=" + walked[0] + "," + walked[1]
                    + " entered=" + entered[0] + "," + entered[1]
                    + " interior=" + reachedRoom[0] + "," + reachedRoom[1]
                    + " feet=" + residents[0].blockPosition()
                    + "," + residents[1].blockPosition()
                    + " actions=" + residents[0].lastAction()
                    + " | " + residents[1].lastAction(), door);
            } else helper.succeed();
        });
    }
}
