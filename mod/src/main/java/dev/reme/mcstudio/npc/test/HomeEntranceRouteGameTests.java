package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.HomeEntranceRoute;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native 1.21.1 GameTests: a bedroom behind a REAL facade has an actual door,
 * not a fictional shortcut through solid stone or an NPC teleport.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HomeEntranceRouteGameTests {
    private HomeEntranceRouteGameTests() {}

    private static BlockPos house(GameTestHelper helper, boolean doorOpen) {
        ServerLevel level=helper.getLevel();
        BlockPos center=helper.absolutePos(new BlockPos(12,3,12));
        for(int dx=-6;dx<=6;dx++)for(int dz=-6;dz<=6;dz++) {
            BlockPos at=center.offset(dx,0,dz);
            level.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);
            for(int dy=0;dy<4;dy++)
                level.setBlock(at.above(dy),Blocks.AIR.defaultBlockState(),2);
        }
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++) {
            if(x!=-3&&x!=3&&z!=-3&&z!=3)continue;
            for(int y=0;y<3;y++)
                level.setBlock(center.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        }
        BlockPos door=center.south(3);
        level.setBlock(door,Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER)
            .setValue(DoorBlock.OPEN,doorOpen),3);
        level.setBlock(door.above(),Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER)
            .setValue(DoorBlock.OPEN,doorOpen),3);
        BlockPos bed=center.north().west();
        level.setBlock(bed,Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING,Direction.NORTH)
            .setValue(BedBlock.PART,BedPart.HEAD),3);
        level.setBlock(bed.south(),Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING,Direction.NORTH)
            .setValue(BedBlock.PART,BedPart.FOOT),3);
        return center;
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void open_residential_door_has_two_real_standing_stages(
        GameTestHelper helper) {
        BlockPos center=house(helper,true);
        var entry=HomeEntranceRoute.nearestOpenDoor(helper.getLevel(),center.north());
        if(entry==null
            ||!entry.outside().equals(center.south(4))
            ||!entry.inside().equals(center.south(2)))
            throw new AssertionError("Home entry must be actual exterior and interior open-door foot blocks");
        // No bypass through a closed wooden door or locked police iron gate.
        helper.getLevel().setBlock(center.south(3),
            helper.getLevel().getBlockState(center.south(3))
                .setValue(DoorBlock.OPEN,false),3);
        helper.getLevel().setBlock(center.south(3).above(),
            helper.getLevel().getBlockState(center.south(3).above())
                .setValue(DoorBlock.OPEN,false),3);
        if(HomeEntranceRoute.nearestOpenDoor(helper.getLevel(),center.north())!=null)
            throw new AssertionError("Closed door incorrectly accepted as open physical entry");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=320)
    public static void exhausted_resident_walks_through_real_door_and_sleeps(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=house(helper,true);
        BlockPos bed=center.north().west();
        PlayerNpcEntity npc=NpcRegistry.PLAYER_NPC.get().create(level);
        if(npc==null)throw new AssertionError("NPC registration missing");
        BlockPos start=center.south(5);
        npc.moveTo(start.getX()+0.5,start.getY(),start.getZ()+0.5,0,0);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setProfession(PlayerNpcEntity.Profession.RESIDENT);
        npc.setHomePos(center.north());
        npc.setAssignedBedPos(bed);
        npc.changeNeeds(0,-95,0);
        npc.setDecision(new LivingPlanner.Decision(
            LivingPlanner.Intent.SLEEP,1000,"physical night rest"));
        if(!level.addFreshEntity(npc))throw new AssertionError("Cannot spawn real resident");
        final boolean[] crossed={false};
        final boolean[] moved={false};
        final double[] last={npc.getZ()};
        for(int t=5;t<=250;t+=5)helper.runAfterDelay(t,()->{
            if(!npc.isAlive())helper.fail("Resident vanished on route to bed",start);
            double z=npc.getZ();
            if(Math.abs(z-last[0])>3.0)
                helper.fail("Resident position jumped without vanilla foot navigation",npc.blockPosition());
            if(Math.abs(z-last[0])>.04)moved[0]=true;
            if(z<center.south(3).getZ()+.1)crossed[0]=true;
            last[0]=z;
        });
        helper.runAfterDelay(260,()->{
            if(!moved[0]||!crossed[0]||!npc.isSleeping()
                ||!npc.getSleepingPos().orElse(BlockPos.ZERO).equals(bed))
                helper.fail("NPC never physically crossed open doorway into its assigned vanilla bed: "
                    +"pos="+npc.blockPosition()+" moved="+moved[0]
                    +" crossed="+crossed[0]+" sleeping="+npc.isSleeping()
                    +" action="+npc.lastAction(),npc.blockPosition());
            else helper.succeed();
        });
    }
}
