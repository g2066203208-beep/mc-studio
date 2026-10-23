package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CityDistrictDemo;
import dev.reme.mcstudio.npc.life.CityJailGate;
import dev.reme.mcstudio.npc.life.PublicSafety;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Regression for intermittent 100/101 failure in PR #125:
 * a real shoplifter reached the east wall of the REAL city jail, stayed
 * at cell.east(2).south(2) for 1000+ ticks, and never reached the south
 * rear entrance. The position is INSIDE the station's eastern corridor,
 * while the south exterior wall is solid. No entity teleport is allowed
 * after the one initial test spawn.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityStationDetourGameTests {
    private CityStationDetourGameTests() {}

    private static BlockPos buildRealCity(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=helper.absolutePos(new BlockPos(23,4,23));
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
            BlockPos floor=center.offset(x,-1,z);
            level.setBlock(floor,Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<=10;y++)
                level.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        CityDistrictDemo.Result result=CityDistrictDemo.build(level,center,null);
        if(!result.success()||result.citizens()!=15)
            throw new AssertionError("Actual physical city missing: "+result);
        return center.offset(-13,1,15);
    }

    @GameTest(template="empty",timeoutTicks=150)
    public static void built_police_station_uses_real_front_door_detour(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos jail=buildRealCity(helper);
        if(!CityJailGate.isGate(level,jail))
            throw new AssertionError("No real iron-bar cell door");
        BlockPos trapped=jail.east(2).south(2);
        BlockPos front=jail.offset(8,0,-3);
        BlockPos corner=jail.offset(8,0,4);
        if(!level.getBlockState(jail.offset(7,0,-3)).is(Blocks.OAK_DOOR)
            ||!level.getBlockState(jail.south(3)).is(Blocks.OAK_DOOR))
            throw new AssertionError("The real two-door station layout changed");
        if(!PublicSafety.custodyCityDetourWaypoint(level,jail,trapped).equals(front)
            ||!PublicSafety.custodyCityDetourWaypoint(level,jail,front).equals(corner)
            ||!PublicSafety.custodyCityDetourWaypoint(level,jail,corner).equals(jail.south(4))
            // Regression from failed CI #39948341847: a thief arrested
            // 30+ blocks away MUST keep the existing bounded street leg,
            // not target the 22-node unreachable east-corner path.
            ||!PublicSafety.custodyCityDetourWaypoint(level,jail,
                jail.offset(17,0,-27)).equals(jail.south(4))
            ||!PublicSafety.custodyCityDetourWaypoint(level,jail,jail.south(2)).equals(jail.south(1))
            ||!PublicSafety.custodyCityDetourWaypoint(level,jail,jail.south(1)).equals(jail))
            throw new AssertionError("East-door/south-sidewalk/iron-gate route oscillates");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=850)
    public static void prisoner_trapped_east_of_bars_walks_out_real_station_door_to_cell(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos jail=buildRealCity(helper);
        BlockPos start=jail.east(2).south(2);
        if(!CityJailGate.setOpen(level,jail,true))
            throw new AssertionError("Real iron door did not open");
        if(!level.getBlockState(start).isAir()
            ||!level.getBlockState(start.above()).isAir())
            throw new AssertionError("Trapped east-corridor starting position obstructed");
        PlayerNpcEntity prisoner=NpcRegistry.PLAYER_NPC.get().create(level);
        if(prisoner==null)throw new AssertionError("NPC registration missing");
        prisoner.setMode(PlayerNpcEntity.Mode.LIVE);
        prisoner.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5,0,0);
        prisoner.detainAt(jail,level.getGameTime()+1400);
        if(!level.addFreshEntity(prisoner))
            throw new AssertionError("Could not spawn one real prisoner in the station");
        Vec3[] last={prisoner.position()};
        AtomicBoolean reachedFront=new AtomicBoolean();
        AtomicBoolean reachedCorner=new AtomicBoolean();
        AtomicBoolean reachedRear=new AtomicBoolean();
        for(int age=10;age<=690;age+=10) {
            final int elapsed=age;
            helper.runAfterDelay(age,()->{
                if(!prisoner.isAlive()||prisoner.isRemoved()){
                    helper.fail("Prisoner despawned mid-escort",start);
                    return;
                }
                if(prisoner.position().distanceToSqr(last[0])>49.0){
                    helper.fail("Custody cheated with position jump",prisoner.blockPosition());
                    return;
                }
                last[0]=prisoner.position();
                if(prisoner.distanceToSqr(Vec3.atCenterOf(jail.offset(8,0,-3)))<12)
                    reachedFront.set(true);
                if(prisoner.distanceToSqr(Vec3.atCenterOf(jail.offset(8,0,4)))<12)
                    reachedCorner.set(true);
                if(prisoner.distanceToSqr(Vec3.atCenterOf(jail.south(4)))<12)
                    reachedRear.set(true);
                if(elapsed%100==0)
                    System.out.println("REME_NPC_REAL_STATION_DETOUR age="+elapsed
                        +" uuid="+prisoner.getUUID()
                        +" feet="+prisoner.blockPosition().toShortString()
                        +" front="+reachedFront.get()+" corner="+reachedCorner.get()
                        +" rear="+reachedRear.get()
                        +" action="+prisoner.lastAction());
            });
        }
        helper.runAfterDelay(730,()->{
            if(!reachedFront.get()||!reachedCorner.get()||!reachedRear.get()
                ||!prisoner.blockPosition().equals(jail)
                ||!prisoner.isDetained(level.getGameTime())
                ||CityJailGate.isOpen(level,jail))
                helper.fail("Prisoner never physically walked front station door ->"
                    +" south sidewalk -> rear oak door -> locked iron cell"
                    +" front="+reachedFront.get()+" corner="+reachedCorner.get()
                    +" rear="+reachedRear.get()
                    +" feet="+prisoner.blockPosition().toShortString()
                    +" gateOpen="+CityJailGate.isOpen(level,jail)
                    +" action="+prisoner.lastAction(),prisoner.blockPosition());
            else helper.succeed();
        });
    }
}
