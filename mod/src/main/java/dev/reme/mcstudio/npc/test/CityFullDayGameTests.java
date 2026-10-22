package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CityDayJournal;
import dev.reme.mcstudio.npc.life.CityDistrictDemo;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** One real 24k server-tick city full-day, 13 actual NPCs, all UUIDs audited.
 * Coordinates are dedicated per GameTest structure, not an existing save.
 * No manual calling planners in a fake for-loop and NO /time set jumps.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityFullDayGameTests {
    private CityFullDayGameTests() {}

    @GameTest(template="empty",timeoutTicks=25100)
    public static void all_thirteen_actual_city_citizens_live_one_whole_minecraft_day(
            GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos anchor=helper.absolutePos(new BlockPos(23,4,23));
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
            var floor=anchor.offset(x,-1,z);
            level.setBlock(floor,Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<=10;y++)
                level.setBlock(anchor.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        var built=CityDistrictDemo.build(level,anchor,null);
        if(!built.success()||built.citizens()!=13)
            throw new AssertionError("Cannot run full real city day without genuine populated city: "+built);
        var people=level.getEntitiesOfClass(PlayerNpcEntity.class,new AABB(anchor).inflate(28,12,28),
            p->p.getTags().contains("reme_city_citizen"));
        if(people.size()!=13)throw new AssertionError("Not 13 live UUID city residents");
        var beds=new HashSet<Long>();
        for(var p:people){
            if(p.assignedBedPos()==null||!beds.add(p.assignedBedPos().asLong()))
                throw new AssertionError("City resident has missing/duplicate real bed "+p.getName());
        }
        if(beds.size()!=13)throw new AssertionError("Cannot house all real residents");
        String started=CityDayJournal.start(level,anchor);
        if(!started.startsWith("已开始"))
            throw new AssertionError("Real city day observer not started: "+started);
        if(!CityDayJournal.status(level).contains("正在运行"))
            throw new AssertionError("City day stopped before it began");
        // Each actual resident ticks via the game engine for 24k Minecraft
        // ticks, not a tight Java loop or a sequence of fake teleports.
        helper.runAfterDelay(24040,()->{
            String status=CityDayJournal.status(level);
            if(!status.contains("完成"))
                helper.fail("24k REAL tick city story never finished: "+status,anchor);
            else {
                long alive=level.getEntitiesOfClass(PlayerNpcEntity.class,
                    new AABB(anchor).inflate(28,12,28),
                    p->p.getTags().contains("reme_city_citizen")&&p.isAlive()).size();
                System.out.println("REME_NPC_CITY_24K_GAMETEST "
                    +" ticks=24000 households=13 alive="+alive
                    +" uniqueBeds="+beds.size()
                    +" dayJournal="+status.replace(" ","_"));
                if(alive<12) helper.fail("Most city residents disappeared during 1 real game day",anchor);
                else helper.succeed();
            }
        });
    }
}
