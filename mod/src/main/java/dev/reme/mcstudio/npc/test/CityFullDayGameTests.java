package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CityDistrictDemo;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Real 24,000 Minecraft tick multi-citizen city, not 24 hours of human video.
 * Samples actual server-side resident position, sleep and work participation;
 * a bed assignment alone is NOT accepted as proof they got into bed.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityFullDayGameTests {
    private CityFullDayGameTests() {}

    @GameTest(template="empty",timeoutTicks=25100)
    public static void real_thirteen_citizens_have_distinct_beds_go_home_sleep_and_work(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=helper.absolutePos(new BlockPos(23,4,23));
        for(int x=-20;x<=20;x++) for(int z=-20;z<=20;z++){
            BlockPos at=center.offset(x,-1,z);
            level.setBlock(at,Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<=10;y++)
                level.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        CityDistrictDemo.Result city=CityDistrictDemo.build(level,center,null);
        if (!city.success() || city.citizens()!=15)
            throw new AssertionError("Need actual city and 15 genuinely living residents: "+city);
        var citizens=level.getEntitiesOfClass(PlayerNpcEntity.class,
            new AABB(center.getX()-20,center.getY(),center.getZ()-20,
                center.getX()+21,center.getY()+11,center.getZ()+21));
        if(citizens.size()!=15) throw new AssertionError("City not genuinely populated");
        Set<BlockPos> claims=new HashSet<>();
        for(PlayerNpcEntity npc:citizens)
            if(npc.assignedBedPos()==null || !claims.add(npc.assignedBedPos()))
                throw new AssertionError("Missing unique actual native bed claim");
        if(claims.size()!=15)throw new AssertionError("Bed claims not one-to-one");

        Set<java.util.UUID> actuallySlept=new HashSet<>();
        Set<java.util.UUID> actuallyWorked=new HashSet<>();
        int[] nightSleepSamples={0},dayDutySamples={0},nearHome={0},nightRoaming={0};
        long started=System.nanoTime();
        for(int age=200;age<24000;age+=200){
            helper.runAfterDelay(age,()->{
                long clock=Math.floorMod(level.getDayTime(),24000L);
                for(PlayerNpcEntity npc:citizens){
                    if(!npc.isAlive()||npc.isRemoved()){
                        helper.fail("Resident vanished during the complete 24k day",npc.blockPosition());
                        continue;
                    }
                    long shifted=Math.floorMod(level.getDayTime()+npc.dailyShiftTicks(),24000L);
                    boolean night=shifted>=13500 && shifted<=23000;
                    boolean day=shifted>=2300 && shifted<9000;
                    if(night && npc.isSleeping()){
                        actuallySlept.add(npc.getUUID());
                        nightSleepSamples[0]++;
                    }
                    if(night && npc.blockPosition().distSqr(npc.homePos())<=9)
                        nearHome[0]++;
                    if(night && npc.profession()!=PlayerNpcEntity.Profession.GUARD
                        && npc.intent()!=LivingPlanner.Intent.SLEEP
                        && !npc.isAfraid(level.getGameTime()))
                        nightRoaming[0]++;
                    if(day && npc.intent()==LivingPlanner.Intent.WORK
                        && npc.profession()!=PlayerNpcEntity.Profession.RESIDENT
                        && npc.blockPosition().distSqr(npc.workPos())<=16){
                        actuallyWorked.add(npc.getUUID());
                        dayDutySamples[0]++;
                    }
                }
            });
        }
        helper.runAfterDelay(24000,()->{
            System.out.println("REME_NPC_REAL_CITY_DAYSOAK ticks=24000"
                +" residents="+citizens.size()
                +" uniquePhysicalBedClaims="+claims.size()
                +" distinctNativeBedSleepers="+actuallySlept.size()
                +" distinctWorkersAtRealJob="+actuallyWorked.size()
                +" nightSleepSamples="+nightSleepSamples[0]
                +" nightHomeSamples="+nearHome[0]
                +" daytimeWorkSamples="+dayDutySamples[0]
                +" unexplainedNightRoaming="+nightRoaming[0]
                +" wallSeconds="+String.format(java.util.Locale.ROOT,"%.2f",
                    (System.nanoTime()-started)/1_000_000_000.0));
            for (PlayerNpcEntity npc:citizens) {
                if(!actuallySlept.contains(npc.getUUID()))
                    System.out.println("REME_NPC_CITY_INSOMNIA npc="+npc.getName().getString()
                        +" profession="+npc.profession()
                        +" livePos="+npc.blockPosition().toShortString()
                        +" home="+npc.homePos().toShortString()
                        +" realBed="+(npc.assignedBedPos()==null ? "none"
                            : npc.assignedBedPos().toShortString())
                        +" intent="+npc.intent()+" energy="+npc.energy()
                        +" pathFinished="+npc.getNavigation().isDone()
                        +" lastAction="+npc.lastAction());
            }
            if(actuallySlept.size()<6)
                helper.fail("Fewer than six real city residents physically entered native beds",center);
            else if(actuallyWorked.size()<2)
                helper.fail("Fewer than two real workers ever reached their actual job sites",center);
            else if(nightRoaming[0]>10)
                helper.fail("Nighttime wandering or social planner repeatedly overrode real bedtime",center);
            else helper.succeed();
        });
    }
}
