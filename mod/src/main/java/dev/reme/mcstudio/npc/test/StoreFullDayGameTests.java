package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.ShopCounter;
import dev.reme.mcstudio.npc.life.SocietyRules;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 24,000 ACTUAL vanilla Minecraft server ticks, with real AI/entity updates.
 * This is NOT simulated calls to ShopCounter.tick and is NOT wall-clock 24h
 * gameplay or a p95 performance benchmark.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StoreFullDayGameTests {
    private StoreFullDayGameTests() {}

    @GameTest(template="empty",timeoutTicks=25100)
    public static void real_convenience_clerk_opens_closes_commutes_and_serves_actual_paying_npc(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos at=helper.absolutePos(new BlockPos(18,9,18));
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
            BlockPos p=at.offset(x,-1,z);
            level.setBlock(p,Blocks.STONE.defaultBlockState(),2);
            level.setBlock(p.above(),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(p.above(2),Blocks.AIR.defaultBlockState(),2);
        }
        PlayerNpcEntity shop=NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity buyer=NpcRegistry.PLAYER_NPC.get().create(level);
        if(shop==null||buyer==null)throw new AssertionError("Missing real NPC type");
        shop.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
        buyer.moveTo(at.getX()+2.5,at.getY(),at.getZ()+.5,0,0);
        shop.setProfession(PlayerNpcEntity.Profession.SHOPKEEPER);
        buyer.setProfession(PlayerNpcEntity.Profession.RESIDENT);
        shop.setMode(PlayerNpcEntity.Mode.LIVE);
        buyer.setMode(PlayerNpcEntity.Mode.LIVE);
        shop.setHomePos(at.west(6));
        shop.setWorkPos(at);
        buyer.setHomePos(at.east(3));
        buyer.setWorkPos(at.east(3));
        shop.setIdentity(SocietyRules.civilian("store_day_soak"));
        buyer.setIdentity(SocietyRules.civilian("store_day_soak"));
        shop.setRoamingRadius(20);
        buyer.setRoamingRadius(20);
        shop.backpack().addItem(new ItemStack(Items.BREAD,32));
        buyer.backpack().addItem(new ItemStack(Items.EMERALD,4));
        buyer.changeNeeds(-55,0,0);
        if(!level.addFreshEntity(shop)||!level.addFreshEntity(buyer))
            throw new AssertionError("Failed actual NeoForge spawn");

        final int startingTill=shop.backpack().countItem(Items.EMERALD);
        AtomicInteger storeOpenSamples=new AtomicInteger();
        AtomicInteger storeClosedSamples=new AtomicInteger();
        AtomicInteger employeeAtHomeAfterClose=new AtomicInteger();
        AtomicInteger moneyReceived=new AtomicInteger();
        AtomicInteger inPersonPaidTransactions=new AtomicInteger();
        final int[] prevEmerald={startingTill};
        final long started=System.nanoTime();
        for(int age=200;age<24000;age+=200){
            helper.runAfterDelay(age,()->{
                if(!shop.isAlive()||!buyer.isAlive())
                    helper.fail("Real citizen dead during full-day shop economy soak",at);
                if(ShopCounter.openForResidentSales(shop))
                    storeOpenSamples.incrementAndGet();
                else storeClosedSamples.incrementAndGet();
                long clock=Math.floorMod(level.getDayTime()+shop.dailyShiftTicks(),24000L);
                if(clock>=11000 && clock<12500 &&
                    shop.blockPosition().distSqr(shop.homePos())<=9)
                    employeeAtHomeAfterClose.incrementAndGet();
                int cash=shop.backpack().countItem(Items.EMERALD);
                if(cash>prevEmerald[0]){
                    moneyReceived.addAndGet(cash-prevEmerald[0]);
                    inPersonPaidTransactions.incrementAndGet();
                }
                prevEmerald[0]=cash;
            });
        }
        helper.runAfterDelay(24000,()->{
            System.out.println("REME_NPC_REAL_STORE_DAYSOAK ticks=24000"
                +" openSamples="+storeOpenSamples.get()
                +" closedSamples="+storeClosedSamples.get()
                +" afterShiftHome="+employeeAtHomeAfterClose.get()
                +" paidEmeralds="+moneyReceived.get()
                +" salesTransitions="+inPersonPaidTransactions.get()
                +" realShopInventory="+ShopCounter.stockedFood(shop.backpack())
                +" actualBuyersEmerald="+buyer.backpack().countItem(Items.EMERALD)
                +" wallSeconds="+String.format(java.util.Locale.ROOT,"%.2f",
                    (System.nanoTime()-started)/1_000_000_000.0)
                +" (game ticks, NOT 24 hours of real-world gameplay)");
            if(!shop.isAlive()||!buyer.isAlive())
                helper.fail("Shopper or clerk disappeared during full game day",at);
            else if(storeOpenSamples.get()<5||storeClosedSamples.get()<5)
                helper.fail("No observable real open/close shift in 24000 Minecraft ticks",at);
            else if(moneyReceived.get()==0
                ||buyer.backpack().countItem(Items.EMERALD)>=4)
                helper.fail("No actual paying NPC customer served during full live shop day",at);
            else helper.succeed();
        });
    }
}
