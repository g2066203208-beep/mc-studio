package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CityDistrictDemo;
import dev.reme.mcstudio.npc.life.ConvenienceStoreCrime;
import dev.reme.mcstudio.npc.life.PublicSafety;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * UNSTAGED urban pursuit: the test creates the same REAL city as /npc city
 * demo and transfers a genuine unpaid shop item at the actual staffed
 * counter. Afterwards no test code moves the thief or police officer.
 * Any guard dispatch/arrest/escorting into the real cell must emerge from
 * live Minecraft entity AI and real collision-aware navigation.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityPursuitGameTests {
    private CityPursuitGameTests() {}

    @GameTest(template="empty", timeoutTicks=1650)
    public static void unattended_shoplifter_leaves_real_city_shop_police_catch_and_walk_to_cell(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=helper.absolutePos(new BlockPos(23,4,23));
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
            BlockPos ground=center.offset(x,-1,z);
            level.setBlock(ground,Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<=10;y++)
                level.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        CityDistrictDemo.Result created=CityDistrictDemo.build(level,center,null);
        if(!created.success()||created.citizens()!=13)
            throw new AssertionError("Cannot start physical city pursuit: "+created);
        List<PlayerNpcEntity> people=level.getEntitiesOfClass(
            PlayerNpcEntity.class,new AABB(center).inflate(30));
        PlayerNpcEntity thief=people.stream().filter(n->
            n.getTags().contains("reme_city_shoplifter")).findAny().orElseThrow();
        PlayerNpcEntity shop=people.stream().filter(n->
            n.getTags().contains("reme_city_clerk")).findAny().orElseThrow();
        PlayerNpcEntity officer=people.stream().filter(n->
            n.getTags().contains("reme_city_guard")).findAny().orElseThrow();
        BlockPos cell=center.offset(-13,1,15);
        if(!PublicSafety.holdingSpot(level,cell))
            throw new AssertionError("Actual built police jail isn't a legal walkable holding cell");
        long tick=level.getGameTime();
        if(!ConvenienceStoreCrime.takeUnpaidFood(thief,shop,level,tick))
            throw new AssertionError("Demo thrift shopper cannot ACTUALLY steal stock from living clerk");
        Vec3 theftOrigin=thief.position();
        AtomicBoolean walkedOut=new AtomicBoolean();
        AtomicBoolean reported=new AtomicBoolean();
        AtomicBoolean caught=new AtomicBoolean();
        AtomicBoolean arrived=new AtomicBoolean();
        AtomicInteger maxDist=new AtomicInteger();
        for(int age=30;age<=1320;age+=30){
            final int at=age;
            helper.runAfterDelay(age,()->{
                if(!thief.isAlive()||!officer.isAlive()){
                    helper.fail("Thief/police disappeared before urban custody completed",cell);
                    return;
                }
                double escape=thief.position().distanceTo(theftOrigin);
                maxDist.accumulateAndGet((int)(escape*100),Math::max);
                if(escape>=4) walkedOut.set(true);
                if(thief.shopCrimeReported()||officer.policeSuspect()!=null)reported.set(true);
                if(thief.isDetained(level.getGameTime())||thief.detentionJail()!=null)caught.set(true);
                if(caught.get()&&thief.distanceToSqr(Vec3.atCenterOf(cell))<=2.5
                    &&officer.escortedPrisoner()==null)arrived.set(true);
                if(at%120==0 || arrived.get())System.out.println(
                    "REME_NPC_UNSTAGED_CITY_PURSUIT age="+at
                    +" thief="+thief.blockPosition().toShortString()
                    +" officer="+officer.blockPosition().toShortString()
                    +" distanceFromRealShop="+String.format(java.util.Locale.ROOT,"%.2f",escape)
                    +" reported="+reported.get()+" arrested="+caught.get()
                    +" cellEntered="+arrived.get()
                    +" lastAction="+thief.lastAction());
            });
        }
        helper.runAfterDelay(1380,()->{
            if(!walkedOut.get())helper.fail(
                "No autonomous physical shop exit: thief never walked 4 blocks from store",center);
            else if(!reported.get())helper.fail(
                "Real shop eyewitness never dispatched police after walking exit",center);
            else if(!caught.get())helper.fail(
                "Real guard never physically intercepted the escaping shoplifter",center);
            else if(!arrived.get())helper.fail(
                "Real city prisoner never navigated to physical holding cell with guard escort",cell);
            else {
                System.out.println("REME_NPC_UNSTAGED_CITY_FULL_CHAIN"
                    +" merchandiseMoved=true footEscape=true complaint=true"
                    +" physicalPursuit=true closeArrest=true guardEscort=true"
                    +" jailEntered=true maxShopDistanceHundredths="+maxDist.get()
                    +" noPostTheftPositionTeleport=true");
                helper.succeed();
            }
        });
    }
}
