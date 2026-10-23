package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.life.ResidentBedLife;
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
}
