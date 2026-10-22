package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CharacterProfile;
import dev.reme.mcstudio.npc.life.CivicMind;
import dev.reme.mcstudio.npc.life.ConvenienceStoreCrime;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.PublicSafety;
import dev.reme.mcstudio.npc.life.ShopCounter;
import dev.reme.mcstudio.npc.life.SocietyRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** End-to-end physical theft and law: staged positions are declared as staged,
 *  never confused with verified walking/chase travel.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConvenienceJusticeGameTests {
    private ConvenienceJusticeGameTests() {}

    private static PlayerNpcEntity actor(ServerLevel level, BlockPos spot,
                                         PlayerNpcEntity.Profession job, int honesty) {
        PlayerNpcEntity a=NpcRegistry.PLAYER_NPC.get().create(level);
        if (a==null) throw new AssertionError("No live NPC type");
        a.moveTo(spot.getX()+0.5, spot.getY(), spot.getZ()+0.5,0,0);
        a.setHomePos(spot);
        a.setWorkPos(spot);
        a.setMode(PlayerNpcEntity.Mode.LIVE);
        a.setIdentity(SocietyRules.civilian("real_store_crime"));
        a.setProfession(job);
        a.setCharacter(new CharacterProfile(honesty, 80, 40, 50));
        if (!level.addFreshEntity(a)) throw new AssertionError("NPC failed server spawn");
        return a;
    }

    private static BlockPos floor(ServerLevel level, BlockPos middle) {
        for (int x=-15;x<=15;x++) for(int z=-15;z<=15;z++) {
            BlockPos p=middle.offset(x,0,z);
            level.setBlock(p.below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
            level.setBlock(p.above(),Blocks.AIR.defaultBlockState(),2);
        }
        return middle;
    }

    @GameTest(template="empty",timeoutTicks=160)
    public static void actual_unpaid_item_then_real_eyewitness_report_nonlethal_arrest_and_save(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=floor(level,helper.absolutePos(new BlockPos(23,9,23)));
        PlayerNpcEntity clerk=actor(level,center,PlayerNpcEntity.Profession.SHOPKEEPER,80);
        PlayerNpcEntity crook=actor(level,center.east(),PlayerNpcEntity.Profession.RESIDENT,5);
        PlayerNpcEntity guard=actor(level,center.offset(4,0,1),PlayerNpcEntity.Profession.GUARD,80);
        clerk.setDecision(new LivingPlanner.Decision(LivingPlanner.Intent.WORK,100,"daytime"));
        clerk.backpack().addItem(new ItemStack(Items.BREAD,3));
        // One real loaded walkable holding block next to the iron bars.
        BlockPos jail=center.offset(8,0,5);
        level.setBlock(jail.east(),Blocks.IRON_BARS.defaultBlockState(),3);
        guard.setPoliceJail(jail);
        long now=level.getGameTime();
        int before=clerk.backpack().countItem(Items.BREAD)
            +crook.backpack().countItem(Items.BREAD);
        if (!ShopCounter.openForResidentSales(clerk))
            throw new AssertionError("Shop already closed at required 06:00 daytime fixture");
        if (!ConvenienceStoreCrime.takeUnpaidFood(crook,clerk,level,now))
            throw new AssertionError("Physical shopper did not take actual stock without payment");
        if (clerk.backpack().countItem(Items.BREAD)!=2
            || crook.backpack().countItem(Items.BREAD)!=1
            || clerk.backpack().countItem(Items.EMERALD)!=0
            || before!=3 || !crook.unpaidFrom(clerk.getUUID(),now)
            || guard.policeSuspect()!=null)
            throw new AssertionError("Unpaid goods duplicated/cash minted/arbitrary pre-crime arrest");
        long witness=clerk.civic().reports().stream().filter(x->
            x.id().equals(crook.unpaidIncident()) && x.direct()
            && x.kind()==CivicMind.Kind.THEFT).count();
        if(witness!=1)throw new AssertionError("No bona-fide actual shopkeeper eyewitness");
        // Stage an escape from shop (test preparation, not simulated walking).
        crook.moveTo(center.getX()+7.5,center.getY(),center.getZ()+.5,0,0);
        ConvenienceStoreCrime.tick(crook,level,now);
        if (!crook.shopCrimeReported() || !crook.getUUID().equals(guard.policeSuspect()))
            throw new AssertionError("Shop theft exit did not notify physically nearby responding guard");
        // Stage close range intercept to specifically exercise native police
        // custody only within 1.8 blocks (not a fake long-distance arrest).
        guard.moveTo(crook.getX()+1, crook.getY(), crook.getZ(),0,0);
        boolean caught=false;
        for(int i=0;i<30;i++){
            long tick=now+i;
            if(PublicSafety.tickGuard(guard,level,tick) && crook.isDetained(tick)){
                caught=true;break;
            }
        }
        if(!caught || crook.hasUnpaidShopItem(now+30)
            || !jail.equals(crook.detentionJail()))
            throw new AssertionError("Shoplifter not physically intercepted and assigned authorized cell");
        CompoundTag tag=new CompoundTag();
        crook.addAdditionalSaveData(tag);
        PlayerNpcEntity restored=NpcRegistry.PLAYER_NPC.get().create(level);
        if(restored==null)throw new AssertionError("Cannot reload thief");
        restored.readAdditionalSaveData(tag);
        if(!jail.equals(restored.detentionJail())
            || restored.hasUnpaidShopItem(now+30))
            throw new AssertionError("Arrest/past criminal state corrupted by NBT reload");
        System.out.println("REME_NPC_STORE_CRIME_PATH stock=3->2 suspectFood=1"
            +" unpaid=1 report=1 guardDispatch=1 nonlethalArrest=1 cellAssigned=1 nbt=1");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=120)
    public static void lawful_customer_missing_money_and_rumor_cannot_trigger_any_police_arrest(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=floor(level,helper.absolutePos(new BlockPos(23,9,23)));
        PlayerNpcEntity clerk=actor(level,center,PlayerNpcEntity.Profession.SHOPKEEPER,70);
        PlayerNpcEntity lawful=actor(level,center.east(),PlayerNpcEntity.Profession.RESIDENT,85);
        PlayerNpcEntity guard=actor(level,center.offset(3,0,1),PlayerNpcEntity.Profession.GUARD,80);
        clerk.backpack().addItem(new ItemStack(Items.BREAD,3));
        guard.setPoliceJail(center.offset(7,0,4));
        clerk.setDecision(new LivingPlanner.Decision(LivingPlanner.Intent.WORK,90,"daytime"));
        long now=level.getGameTime();
        if(ConvenienceStoreCrime.takeUnpaidFood(lawful,clerk,level,now)
            || clerk.backpack().countItem(Items.BREAD)!=3
            || PublicSafety.reportTheft(clerk,lawful,level,java.util.UUID.randomUUID(),now)!=0
            || guard.policeSuspect()!=null)
            throw new AssertionError("Honest unfunded shopper or hearsay manufactured criminal evidence");
        if (ConvenienceStoreCrime.escapeWithoutPayChance(0)!=75
            || ConvenienceStoreCrime.escapeWithoutPayChance(1)!=30
            || ConvenienceStoreCrime.escapeWithoutPayChance(2)!=5)
            throw new AssertionError("Local security must lower opportunistic shoplifting");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void cash_payment_persists_while_late_shift_register_stays_closed(
        GameTestHelper helper) {
        ServerLevel world=helper.getLevel();
        BlockPos p=floor(world,helper.absolutePos(new BlockPos(23,9,23)));
        PlayerNpcEntity seller=actor(world,p,PlayerNpcEntity.Profession.SHOPKEEPER,80);
        PlayerNpcEntity buyer=actor(world,p.east(),PlayerNpcEntity.Profession.RESIDENT,80);
        seller.setDecision(new LivingPlanner.Decision(LivingPlanner.Intent.WORK,99,"morning work"));
        seller.backpack().addItem(new ItemStack(Items.BREAD,1));
        buyer.backpack().addItem(new ItemStack(Items.EMERALD,1));
        long now=world.getGameTime();
        if (!ShopCounter.purchaseByResident(seller,buyer,now)
            || seller.backpack().countItem(Items.EMERALD)!=1
            || buyer.backpack().countItem(Items.EMERALD)!=0
            || buyer.backpack().countItem(Items.BREAD)!=1)
            throw new AssertionError("Lawful daytime customer didn't actually pay");
        // Stable property of time arithmetic, independent of test batch clock.
        if (ShopCounter.scheduledAt(0,0)==false
            || !ShopCounter.scheduledAt(10000,0)
            || ShopCounter.scheduledAt(11000,0)
            || ShopCounter.scheduledAt(13000,0))
            throw new AssertionError("Shop must open at 06:00 and close at 17:00");
        helper.succeed();
    }
}
