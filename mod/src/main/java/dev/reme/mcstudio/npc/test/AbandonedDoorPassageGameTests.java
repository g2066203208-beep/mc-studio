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
 * A vanished resident must not permanently reserve the only wooden entrance.
 * Real NPCs and vanilla pathfinding are used; no teleport is accepted as
 * evidence that the surviving resident entered the house.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AbandonedDoorPassageGameTests {
    private AbandonedDoorPassageGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 750)
    public static void removed_owner_releases_real_wooden_entrance(GameTestHelper helper) {
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
                    level.setBlock(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        BlockPos door = center.south(4);
        level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);

        PlayerNpcEntity owner = NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity follower = NpcRegistry.PLAYER_NPC.get().create(level);
        if (owner == null || follower == null)
            throw new AssertionError("Registered physical NPC missing");
        owner.setMode(PlayerNpcEntity.Mode.STAY);
        follower.setMode(PlayerNpcEntity.Mode.STAY);
        owner.setHomePos(center.north());
        follower.setHomePos(center.north());
        BlockPos ownerStart = center.south(5);
        BlockPos followerStart = center.south(8).east(2);
        owner.moveTo(ownerStart.getX() + 0.5, ownerStart.getY(),
            ownerStart.getZ() + 0.5, 0, 0); // initial test spawn only
        follower.moveTo(followerStart.getX() + 0.5, followerStart.getY(),
            followerStart.getZ() + 0.5, 0, 0); // initial test spawn only
        if (!level.addFreshEntity(owner) || !level.addFreshEntity(follower))
            throw new AssertionError("Could not spawn physical doorway residents");

        boolean[] openedByOwner = {false};
        boolean[] followerWalked = {false};
        boolean[] followerEntered = {false};
        Vec3[] lastPosition = {follower.position()};
        helper.runAfterDelay(5, () -> {
            HomeEntranceRoute.moveTowardEntrance(owner, level, center.north(), 1.0);
            openedByOwner[0] = level.getBlockState(door).getValue(DoorBlock.OPEN);
            if (!openedByOwner[0])
                helper.fail("Original resident did not physically open the door", door);
        });
        // Remove the holder BEFORE it crosses. The passage must be released
        // by owner lifecycle cleanup, not by a successful threshold crossing.
        helper.runAfterDelay(10, owner::discard);
        for (int tick = 20; tick <= 590; tick += 5) {
            helper.runAfterDelay(tick, () -> {
                if (!follower.isAlive())
                    helper.fail("Surviving doorway resident disappeared", followerStart);
                double distance = follower.position().distanceToSqr(lastPosition[0]);
                if (distance > 9.0)
                    helper.fail("Survivor jumped instead of physically walking",
                        follower.blockPosition());
                if (distance > 0.01) followerWalked[0] = true;
                lastPosition[0] = follower.position();
                BlockPos feet = follower.blockPosition();
                if (Math.abs(feet.getX() - center.getX()) <= 3
                        && Math.abs(feet.getZ() - center.getZ()) <= 3
                        && feet.getY() == center.getY())
                    followerEntered[0] = true;
                if (!followerEntered[0])
                    HomeEntranceRoute.moveTowardEntrance(follower, level,
                        center.north(), 1.0);
            });
        }
        helper.runAfterDelay(650, () -> {
            if (!openedByOwner[0] || !owner.isRemoved()
                    || !followerWalked[0] || !followerEntered[0]) {
                helper.fail("Removed doorway owner stranded next resident: opened="
                    + openedByOwner[0] + " removed=" + owner.isRemoved()
                    + " walked=" + followerWalked[0]
                    + " entered=" + followerEntered[0]
                    + " feet=" + follower.blockPosition()
                    + " action=" + follower.lastAction(), door);
            } else helper.succeed();
        });
    }
}
