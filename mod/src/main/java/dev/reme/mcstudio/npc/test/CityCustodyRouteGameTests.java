package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CityJailGate;
import dev.reme.mcstudio.npc.life.PublicSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native 1.21.1 GameTests: a prisoner must WALK around a barred wall,
 * cross the real rear entrance and open iron gate, and enter the cell.
 * Test spawn placement is setup only; gameplay never teleports a detainee.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityCustodyRouteGameTests {
    private CityCustodyRouteGameTests() {}

    @GameTest(template="empty",timeoutTicks=100)
    public static void custody_waypoints_do_not_oscillate_at_real_iron_gate(
        GameTestHelper helper) {
        BlockPos jail=helper.absolutePos(new BlockPos(10,2,10));
        if(!PublicSafety.custodyWaypoint(jail,jail.east(3)).equals(jail.south(4))
            ||!PublicSafety.custodyWaypoint(jail,jail.south(4)).equals(jail.south(1))
            ||!PublicSafety.custodyWaypoint(jail,jail.south(3)).equals(jail.south(1))
            ||!PublicSafety.custodyWaypoint(jail,jail.south(2)).equals(jail.south(1))
            ||!PublicSafety.custodyWaypoint(jail,jail.south(1)).equals(jail))
            throw new AssertionError("Custody stage jumps outside after crossing gate");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=300)
    public static void detainee_physically_enters_built_cell_via_rear_intake(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos jail=helper.absolutePos(new BlockPos(10,2,10));
        for(int x=-6;x<=6;x++)for(int z=-6;z<=8;z++){
            BlockPos tile=jail.offset(x,0,z);
            level.setBlock(tile.below(),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<4;y++)
                level.setBlock(tile.above(y),Blocks.AIR.defaultBlockState(),2);
        }
        // Actual one-tile jail cell, enclosed on all sides except an
        // authorized iron gate on its south intake.
        for(int z=-1;z<=2;z++)
            for(int x:new int[]{-1,1})
                for(int y=0;y<3;y++)
                    level.setBlock(jail.offset(x,y,z),Blocks.IRON_BARS.defaultBlockState(),2);
        for(int x=-1;x<=1;x++)
            for(int y=0;y<3;y++)
                level.setBlock(jail.offset(x,y,-1),Blocks.IRON_BARS.defaultBlockState(),2);
        for(int x:new int[]{-1,1})
            for(int y=0;y<3;y++)
                level.setBlock(jail.offset(x,y,2),Blocks.IRON_BARS.defaultBlockState(),2);
        // Real iron door and real open rear oak door, never fake air passage.
        BlockPos gate=jail.south(2);
        level.setBlock(gate,Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);
        level.setBlock(gate.above(),Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        BlockPos rear=jail.south(3);
        level.setBlock(rear,Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.OPEN,true)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);
        level.setBlock(rear.above(),Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.OPEN,true)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        if(!PublicSafety.holdingSpot(level,jail)
            ||!CityJailGate.isGate(level,jail)
            ||!CityJailGate.setOpen(level,jail,true))
            throw new AssertionError("Actual loaded native iron cell/gate not built");
        PlayerNpcEntity suspect=NpcRegistry.PLAYER_NPC.get().create(level);
        if(suspect==null)throw new AssertionError("NPC registry unavailable");
        suspect.setMode(PlayerNpcEntity.Mode.LIVE);
        suspect.moveTo(jail.getX()+3.5,jail.getY(),jail.getZ()+0.5,0,0);
        suspect.detainAt(jail,level.getGameTime()+900);
        if(!level.addFreshEntity(suspect))
            throw new AssertionError("Real prisoner could not spawn outside cell");
        BlockPos initial=suspect.blockPosition();
        if(initial.distSqr(jail)<=4)
            throw new AssertionError("Test cheated by spawning inside cell");
        helper.runAfterDelay(220,()->{
            if(!suspect.isAlive() || suspect.isRemoved())
                helper.fail("Real prisoner disappeared during custody",initial);
            else if(!suspect.blockPosition().equals(jail)
                ||!suspect.isDetained(level.getGameTime())
                ||CityJailGate.isOpen(level,jail))
                helper.fail("Prisoner failed physical rear intake, iron gate or lock:"
                    +" pos="+suspect.blockPosition().toShortString()
                    +" gateOpen="+CityJailGate.isOpen(level,jail)
                    +" action="+suspect.lastAction(),suspect.blockPosition());
            else helper.succeed();
        });
    }
    /**
     * The original GUI city has an actual suspect 35+ blocks from its cell.
     * A direct native path was truncated to 20 nodes and canReach=false.
     * This test MUST walk the full distance through loaded vanilla paving,
     * then physically enter the real two-block-high iron gate and lock it.
     */
    @GameTest(template="empty",timeoutTicks=760)
    public static void far_arrest_walks_to_real_cell_without_teleport(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos jail=helper.absolutePos(new BlockPos(10,2,10));
        for(int dx=-5;dx<=35;dx++)for(int dz=-19;dz<=9;dz++){
            BlockPos tile=jail.offset(dx,0,dz);
            level.setBlock(tile.below(),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            for(int y=0;y<3;y++)
                level.setBlock(tile.above(y),Blocks.AIR.defaultBlockState(),2);
        }
        // A physically enclosed cell: east/west/north bars, iron south gate,
        // and an actual open oak back door. The surrounding paving is real.
        for(int z=-1;z<=2;z++)
            for(int x:new int[]{-1,1})
                for(int y=0;y<3;y++)
                    level.setBlock(jail.offset(x,y,z),Blocks.IRON_BARS.defaultBlockState(),2);
        for(int x=-1;x<=1;x++)
            for(int y=0;y<3;y++)
                level.setBlock(jail.offset(x,y,-1),Blocks.IRON_BARS.defaultBlockState(),2);
        BlockPos gate=jail.south(2);
        level.setBlock(gate,Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);
        level.setBlock(gate.above(),Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        BlockPos back=jail.south(3);
        level.setBlock(back,Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.OPEN,true)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);
        level.setBlock(back.above(),Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.OPEN,true)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        if(!PublicSafety.holdingSpot(level,jail)
            ||!CityJailGate.setOpen(level,jail,true))
            throw new AssertionError("Long-range physical jail fixture not built");
        PlayerNpcEntity suspect=NpcRegistry.PLAYER_NPC.get().create(level);
        if(suspect==null)throw new AssertionError("NPC registry unavailable");
        suspect.setMode(PlayerNpcEntity.Mode.LIVE);
        BlockPos start=jail.offset(31,0,-16);
        suspect.moveTo(start.getX()+0.5,start.getY(),start.getZ()+0.5,0,0);
        suspect.detainAt(jail,level.getGameTime()+1100);
        if(!level.addFreshEntity(suspect))
            throw new AssertionError("Long-range prisoner could not spawn");
        if(start.distSqr(jail)<30*30)
            throw new AssertionError("Test did not exceed native pathfinding range");
        final net.minecraft.world.phys.Vec3[] last={suspect.position()};
        for(int at=10;at<=640;at+=10)
            helper.runAfterDelay(at,()->{
                if(!suspect.isAlive()||suspect.isRemoved()){
                    helper.fail("Long-range prisoner disappeared",start);
                    return;
                }
                if(suspect.position().distanceToSqr(last[0])>7.0*7.0){
                    helper.fail("Prisoner skipped real walking / teleported over ten ticks",
                        suspect.blockPosition());
                    return;
                }
                last[0]=suspect.position();
            });
        helper.runAfterDelay(680,()->{
            if(!suspect.blockPosition().equals(jail)
                ||!suspect.isDetained(level.getGameTime())
                ||CityJailGate.isOpen(level,jail))
                helper.fail("Long-range custody never reached the physical cell:"
                    +" start="+start.toShortString()
                    +" actual="+suspect.blockPosition().toShortString()
                    +" gateOpen="+CityJailGate.isOpen(level,jail)
                    +" action="+suspect.lastAction(),suspect.blockPosition());
            else helper.succeed();
        });
    }

}
