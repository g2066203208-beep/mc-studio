package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.life.ResidentBedLife;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-world physics regression: being 1.4 blocks away diagonally, or
 * adjacent behind a real solid wall, must never count as touching a bed.
 * This checks the exact predicate used before native startSleeping.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BedsideArrivalGameTests {
    private BedsideArrivalGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void actual_bedside_requires_free_orthogonal_standing_tile(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos head = helper.absolutePos(new BlockPos(4, 5, 4));
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos feet = head.offset(dx, 0, dz);
                level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(feet, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 2);
            }
        if (!ResidentBedLife.isActualBedside(level, head, head.east()))
            throw new AssertionError("Reachable exact east bedside was rejected");
        if (ResidentBedLife.isActualBedside(level, head, head.east().south())
            || ResidentBedLife.isActualBedside(level, head, head.above())
            || ResidentBedLife.isActualBedside(level, head, head.east(2)))
            throw new AssertionError("Diagonal, different height or 2-block-away bed spoof");
        level.setBlock(head.east(), Blocks.STONE.defaultBlockState(), 2);
        if (ResidentBedLife.isActualBedside(level, head, head.east()))
            throw new AssertionError("Solid wall accepted as an actual standing bedside");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 70)
    public static void temporary_occupied_or_blocked_bed_never_erases_saved_claim(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos head = helper.absolutePos(new BlockPos(5, 5, 5));
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos tile = head.offset(dx, 0, dz);
                level.setBlock(tile.below(), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(tile, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(tile.above(), Blocks.AIR.defaultBlockState(), 2);
            }
        BlockPos foot = head.relative(Direction.SOUTH);
        level.setBlock(head, Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING, Direction.NORTH)
            .setValue(BedBlock.PART, BedPart.HEAD)
            .setValue(BedBlock.OCCUPIED, true), 2);
        level.setBlock(foot, Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING, Direction.NORTH)
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(BedBlock.OCCUPIED, true), 2);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC registration unavailable");
        npc.moveTo(head.getX()+2.5, head.getY(), head.getZ()+0.5, 0, 0);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setHomePos(head.east());
        npc.setAssignedBedPos(head);
        npc.setDecision(new LivingPlanner.Decision(
            LivingPlanner.Intent.SLEEP, 1000, "native bedtime"));
        if (!level.addFreshEntity(npc))
            throw new AssertionError("Could not spawn the real server NPC");
        ResidentBedLife.tick(npc, level, level.getGameTime());
        if (!head.equals(npc.assignedBedPos()) || npc.isSleeping())
            throw new AssertionError("A visitor occupying the bed stole the saved claim");

        // Restore the original physical bed without ever deleting the head.
        level.setBlock(head, level.getBlockState(head)
            .setValue(BedBlock.OCCUPIED, false), 2);
        level.setBlock(foot, level.getBlockState(foot)
            .setValue(BedBlock.OCCUPIED, false), 2);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            // SOUTH is the real vanilla bed FOOT, not an aisle. Never
            // overwrite it to manufacture a blocked-bed test.
            if (dir == Direction.SOUTH) continue;
            BlockPos neighbor = head.relative(dir);
            level.setBlock(neighbor, Blocks.STONE.defaultBlockState(), 2);
        }
        ResidentBedLife.tick(npc, level, level.getGameTime()+1);
        if (!head.equals(npc.assignedBedPos()) || npc.isSleeping())
            throw new AssertionError("Temporarily blocked bed side erased ownership or faked sleep");
        helper.succeed();
    }

}
