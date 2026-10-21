package dev.reme.shiprepair;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

/** Real Sable assembly + actor ticks, not simulated external-motion transforms. */
@GameTestHolder(ShipRepairMod.ID)
@PrefixGameTestTemplate(false)
public final class ShipRepairGameTests {
    private ShipRepairGameTests() {}

    @GameTest(template="empty", timeoutTicks=100)
    public static void recordsAndRepairsActualMovingSublevel(GameTestHelper helper) {
        var level=helper.getLevel();
        BlockPos origin=helper.absolutePos(new BlockPos(2,1,2));
        BlockPos stationWorld=origin.above();
        BlockPos hull=origin.east();
        BlockPos stairs=origin.south();
        var firstStairs=Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH);
        level.setBlock(origin,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(stationWorld,ShipRepairMod.STATION.get().defaultBlockState(),3);
        level.setBlock(hull,Blocks.GOLD_BLOCK.defaultBlockState(),3);
        level.setBlock(stairs,firstStairs,3);
        var ship=SubLevelAssemblyHelper.assembleBlocks(level,origin,
                List.of(origin,stationWorld,hull,stairs),
                new BoundingBox3i(origin.getX(),origin.getY(),origin.getZ(),
                        hull.getX(),stationWorld.getY(),stairs.getZ()));
        var plotOrigin=ship.getPlot().getCenterBlock();
        var stationPos=plotOrigin.above();
        var hullPos=plotOrigin.east();
        var stairsPos=plotOrigin.south();
        var extraPos=plotOrigin.west();
        var chestPos=plotOrigin.north();
        var player=helper.makeMockPlayer(GameType.CREATIVE);

        helper.runAtTickTime(8,()->{
            var be=level.getBlockEntity(stationPos);
            helper.assertTrue(be instanceof RepairStationBlockEntity,
                    "The repair station BE must move with the actual assembled Sable plot");
            ((RepairStationBlockEntity)be).click(player,false);
        });
        helper.runAtTickTime(20,()->{
            var station=(RepairStationBlockEntity)level.getBlockEntity(stationPos);
            helper.assertTrue(station!=null && station.savedBlockCount()==4 && !station.busy(),
                    "Sable actor must automatically capture ALL four ship blocks by its tick");
            level.setBlock(hullPos,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(stairsPos,Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH),3);
            level.setBlock(extraPos,Blocks.BLUE_WOOL.defaultBlockState(),3);
            level.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),3);
            station.click(player,false);
        });
        helper.runAtTickTime(33,()->{
            var station=(RepairStationBlockEntity)level.getBlockEntity(stationPos);
            helper.assertTrue(station!=null && !station.busy(),
                    "Sable actor must autonomously execute repair job, not require manual tick");
            helper.assertTrue(level.getBlockState(hullPos).is(Blocks.GOLD_BLOCK),
                    "Missing hull must be restored in plot coordinate space");
            helper.assertTrue(level.getBlockState(stairsPos).equals(firstStairs),
                    "Original stair orientation and BlockState properties must be restored");
            helper.assertTrue(level.getBlockState(extraPos).isAir(),
                    "A random newly added simple block must be removed");
            helper.assertTrue(level.getBlockState(chestPos).is(Blocks.CHEST),
                    "A newly added inventory block must NOT be destroyed or duplicated");
            helper.succeed();
        });
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void refusesGlobalWorldRestore(GameTestHelper helper) {
        var world=helper.getLevel();
        var pos=helper.absolutePos(new BlockPos(2,1,2));
        helper.setBlock(new BlockPos(2,1,2),ShipRepairMod.STATION.get());
        var station=(RepairStationBlockEntity)world.getBlockEntity(pos);
        helper.assertTrue(station!=null,"Ordinary stationary world station BE must be registered");
        station.click(helper.makeMockPlayer(GameType.CREATIVE),false);
        helper.assertTrue(station.savedBlockCount()==0 && !station.busy(),
                "Outside a Sable sub-level never record or write to vanilla world");
        helper.succeed();
    }
}
