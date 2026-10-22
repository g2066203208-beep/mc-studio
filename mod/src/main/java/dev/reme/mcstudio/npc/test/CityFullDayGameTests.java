package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CityDistrictDemo;
import dev.reme.mcstudio.npc.life.CityDayObserver;
import dev.reme.mcstudio.npc.life.ShopCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A 13-citizen populated REAL BLOCK city, observed for a continuous actual
 * Minecraft 24,000 ticks, without /time skips or scripted NPC movement.
 * GameTest itself runs faster than wall-clock gameplay (not a 24h benchmark).
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityFullDayGameTests {
    private CityFullDayGameTests() {}

    @GameTest(template="empty",timeoutTicks=25150)
    public static void native_thirteen_citizen_city_day_has_homes_jobs_food_roads_and_police(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=helper.absolutePos(new BlockPos(23,4,23));
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
            level.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<=10;y++)
                level.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        CityDistrictDemo.Result generated=CityDistrictDemo.build(level,center,null);
        if(!generated.success()||generated.citizens()!=13)
            throw new AssertionError("Actual city must spawn exactly 13 citizens "+generated);
        List<PlayerNpcEntity> citizens=level.getEntitiesOfClass(
            PlayerNpcEntity.class,
            new net.minecraft.world.phys.AABB(center.getX()-21,center.getY(),
                center.getZ()-21,center.getX()+22,center.getY()+12,
                center.getZ()+22),
            n -> n.getTags().contains(CityDayObserver.CITY_TAG));
        if(citizens.size()!=13)
            throw new AssertionError("City simulation started without every real citizen");
        // Every individual has a distinct REAL vanilla bed head and a
        // materialized walking/crafting/farming workplace.
        Set<BlockPos> beds=new HashSet<>();
        Set<java.util.UUID> ids=new HashSet<>();
        for(PlayerNpcEntity npc:citizens){
            if(!ids.add(npc.getUUID())||npc.assignedBedPos()==null
                || !beds.add(npc.assignedBedPos())
                || !level.getBlockState(npc.assignedBedPos()).is(Blocks.RED_BED)
                || level.getBlockState(npc.assignedBedPos())
                    .getValue(BedBlock.PART)!=BedPart.HEAD)
                throw new AssertionError("City resident lacks UNIQUE physical native bed: "+npc.getUUID());
        }
        if(!level.getBlockState(center.offset(-8,1,-10)).is(Blocks.CRAFTING_TABLE)
            || !(level.getBlockEntity(center.offset(-12,1,-12))
                instanceof ChestBlockEntity source)
            || source.countItem(Items.WHEAT)!=24
            || !(level.getBlockEntity(center.offset(-15,1,9))
                instanceof ChestBlockEntity)
            || !level.getBlockState(center.offset(16,1,14)).is(Blocks.CARROTS)
            || level.getBlockState(center.offset(16,1,14))
                .getValue(CropBlock.AGE)!=7)
            throw new AssertionError("Workers have virtual farm/courier/bakery rather than real blocks");
        long courierCount=citizens.stream().filter(n ->
            n.profession()==PlayerNpcEntity.Profession.COURIER
            && n.caravanPickup()!=null&&n.caravanDropoff()!=null).count();
        if(courierCount!=1)
            throw new AssertionError("Courier missing true two-chest delivery route");

        AtomicInteger active=new AtomicInteger();
        AtomicInteger shopsOpen=new AtomicInteger();
        AtomicInteger shopsClosed=new AtomicInteger();
        AtomicInteger observedSleep=new AtomicInteger();
        AtomicInteger guardsWorking=new AtomicInteger();
        AtomicInteger realCarrotHarvest=new AtomicInteger();
        AtomicInteger realBusinessIncome=new AtomicInteger();
        var moved=new HashSet<java.util.UUID>();
        java.util.Map<java.util.UUID,BlockPos> original=new java.util.HashMap<>();
        for(PlayerNpcEntity npc:citizens)original.put(npc.getUUID(),npc.blockPosition());
        for(int age=200;age<24000;age+=200){
            helper.runAfterDelay(age,()->{
                for(PlayerNpcEntity npc:citizens) {
                    if(!npc.isAlive()||npc.isRemoved()) {
                        helper.fail("Real citizen died during full day: "+npc.getUUID(),center);
                        return;
                    }
                    if(npc.blockPosition().distSqr(original.get(npc.getUUID()))>4)
                        moved.add(npc.getUUID());
                    if(npc.isSleeping())observedSleep.incrementAndGet();
                    if(npc.lastAction()!=null && !npc.lastAction().isBlank())
                        active.incrementAndGet();
                    if(npc.profession()==PlayerNpcEntity.Profession.SHOPKEEPER) {
                        if(ShopCounter.openForResidentSales(npc))shopsOpen.incrementAndGet();
                        else shopsClosed.incrementAndGet();
                        if(npc.backpack().countItem(Items.EMERALD)>4)
                            realBusinessIncome.incrementAndGet();
                    }
                    if(npc.profession()==PlayerNpcEntity.Profession.GUARD &&
                        npc.lastAction().contains("watch"))guardsWorking.incrementAndGet();
                    if(npc.profession()==PlayerNpcEntity.Profession.FARMER &&
                        npc.lastAction().contains("harvest"))realCarrotHarvest.incrementAndGet();
                }
            });
        }
        helper.runAfterDelay(24020,()->{
            long complete=citizens.stream()
                .filter(n->n.getPersistentData().getBoolean("RemeRealCityDayCompleted"))
                .count();
            System.out.println("REME_NPC_TRUE_CITY_DAY ticks=24000"
                +" actors="+citizens.size()
                +" completed="+complete
                +" physicallyMoved="+moved.size()
                +" activeSamples="+active.get()
                +" sleepSamples="+observedSleep.get()
                +" shopOpenSamples="+shopsOpen.get()
                +" shopClosedSamples="+shopsClosed.get()
                +" guardWatchSamples="+guardsWorking.get()
                +" farmerHarvestSamples="+realCarrotHarvest.get()
                +" shopIncomeSamples="+realBusinessIncome.get()
                +" courierActive="+courierCount
                +" sourceWheat="+source.countItem(Items.WHEAT));
            if(complete!=13||active.get()<100)
                helper.fail("Not all thirteen real city citizens completed a sampled full native day",center);
            else if(shopsOpen.get()<5||shopsClosed.get()<5)
                helper.fail("No physical day/night storefront shifts in an actual city",center);
            else if(moved.size()<5)
                helper.fail("Five citizens never moved around the real Minecraft streets",center);
            else helper.succeed();
        });
    }
}
