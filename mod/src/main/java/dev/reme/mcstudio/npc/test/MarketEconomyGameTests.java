package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.NeighborhoodMarket;
import dev.reme.mcstudio.npc.life.MovementPolicy;
import dev.reme.mcstudio.npc.life.ResidentMemory;
import dev.reme.mcstudio.npc.life.ShopCounter;
import dev.reme.mcstudio.npc.life.SocietyRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests run with REAL Minecraft 1.21.1 inventory, entity, block and NBT APIs.
 * A seller with actual stock, a buyer holding a real emerald, and a farmer
 * with carrots form a closed local economy; no virtual market amounts.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketEconomyGameTests {
    private MarketEconomyGameTests() {}

    private static PlayerNpcEntity spawn(ServerLevel level, BlockPos at,
                                         PlayerNpcEntity.Profession profession) {
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("Missing NPC registry");
        npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        npc.setHomePos(at);
        npc.setWorkPos(at);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setProfession(profession);
        npc.setIdentity(SocietyRules.civilian("test_neighborhood_market"));
        if (!level.addFreshEntity(npc))
            throw new AssertionError("Couldn't add real " + profession + " NPC");
        return npc;
    }

    private static void work(PlayerNpcEntity npc) {
        npc.setDecision(new LivingPlanner.Decision(LivingPlanner.Intent.WORK, 100, "day shift"));
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void three_real_citizens_trade_food_and_emeralda_without_minting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(5, 9, 5));
        PlayerNpcEntity shop = spawn(level, start, PlayerNpcEntity.Profession.SHOPKEEPER);
        PlayerNpcEntity farmer = spawn(level, start.east(), PlayerNpcEntity.Profession.FARMER);
        PlayerNpcEntity buyer = spawn(level, start.south(), PlayerNpcEntity.Profession.RESIDENT);
        work(shop);
        shop.backpack().addItem(new ItemStack(Items.EMERALD, 2));
        shop.backpack().addItem(new ItemStack(Items.BREAD, 1));
        farmer.backpack().addItem(new ItemStack(Items.CARROT, 6));
        buyer.backpack().addItem(new ItemStack(Items.EMERALD, 1));

        long now = level.getGameTime();
        if (!ShopCounter.buyHarvestFromFarmer(shop, farmer, now)
            || shop.backpack().countItem(Items.CARROT) != 3
            || shop.backpack().countItem(Items.EMERALD) != 1
            || farmer.backpack().countItem(Items.CARROT) != 3
            || farmer.backpack().countItem(Items.EMERALD) != 1)
            throw new AssertionError("Farmer received phantom wages or harvest was duplicated");
        if (!ShopCounter.purchaseByResident(shop, buyer, now)
            || shop.backpack().countItem(Items.BREAD) != 0
            || shop.backpack().countItem(Items.EMERALD) != 2
            || buyer.backpack().countItem(Items.BREAD) != 1
            || buyer.backpack().countItem(Items.EMERALD) != 0)
            throw new AssertionError("Autonomous resident food purchase lost real currency or goods");
        // Pre-transaction totals: 3 emeralds; 6 carrots; 1 bread.
        if (shop.backpack().countItem(Items.EMERALD)
                + farmer.backpack().countItem(Items.EMERALD)
                + buyer.backpack().countItem(Items.EMERALD) != 3
            || shop.backpack().countItem(Items.CARROT)
                + farmer.backpack().countItem(Items.CARROT) != 6
            || buyer.backpack().countItem(Items.BREAD) != 1)
            throw new AssertionError("Three-citizen economy failed money/item conservation");
        if (shop.memory().trust(buyer.getUUID()) <= 0
            || farmer.memory().trust(shop.getUUID()) <= 0)
            throw new AssertionError("Trades did not develop bounded bilateral social trust");

        CompoundTag save = new CompoundTag();
        farmer.addAdditionalSaveData(save);
        PlayerNpcEntity restored = NpcRegistry.PLAYER_NPC.get().create(level);
        if (restored == null) throw new AssertionError("cannot test saved NPC");
        restored.readAdditionalSaveData(save);
        if (restored.backpack().countItem(Items.CARROT) != 3
            || restored.backpack().countItem(Items.EMERALD) != 1
            || restored.memory().trust(shop.getUUID()) <= 0)
            throw new AssertionError("Paid harvest or trade relationships disappeared on NBT reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void no_money_or_full_backpack_cannot_buy_or_sell_phantom_goods(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(4, 9, 4));
        PlayerNpcEntity shop = spawn(level, at, PlayerNpcEntity.Profession.SHOPKEEPER);
        PlayerNpcEntity buyer = spawn(level, at.east(), PlayerNpcEntity.Profession.RESIDENT);
        PlayerNpcEntity farmer = spawn(level, at.south(), PlayerNpcEntity.Profession.FARMER);
        work(shop);
        shop.backpack().setItem(0, new ItemStack(Items.BREAD, 2));
        for (int i = 1; i < 36; i++)
            shop.backpack().setItem(i, new ItemStack(Items.DIRT, 64));
        buyer.backpack().addItem(new ItemStack(Items.EMERALD, 2));

        if (ShopCounter.purchaseByResident(shop, buyer, level.getGameTime())
            || buyer.backpack().countItem(Items.EMERALD) != 2
            || shop.backpack().countItem(Items.BREAD) != 2)
            throw new AssertionError("Retail committed a sale with no shop cash capacity");
        // Clear one seller slot and fill buyer: leftover emerald in buyer's
        // occupied first slot cannot make room for the bought physical food.
        shop.backpack().setItem(35, ItemStack.EMPTY);
        for (int i = 1; i < 36; i++)
            buyer.backpack().setItem(i, new ItemStack(Items.DIRT, 64));
        if (ShopCounter.purchaseByResident(shop, buyer, level.getGameTime())
            || buyer.backpack().countItem(Items.EMERALD) != 2
            || shop.backpack().countItem(Items.BREAD) != 2)
            throw new AssertionError("Full buyer backpack lost payment or created phantom item");
        // No funded till -> cannot pretend to pay a farmer.
        shop.backpack().setItem(35, ItemStack.EMPTY);
        farmer.backpack().addItem(new ItemStack(Items.CARROT, 5));
        if (ShopCounter.buyHarvestFromFarmer(shop, farmer, level.getGameTime())
            || farmer.backpack().countItem(Items.CARROT) != 5)
            throw new AssertionError("Shop used money it does not have");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void automated_shop_refuses_distant_unemployed_or_hostile_trades(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(5, 9, 5));
        PlayerNpcEntity clerk = spawn(level, at, PlayerNpcEntity.Profession.SHOPKEEPER);
        PlayerNpcEntity visitor = spawn(level, at.south(), PlayerNpcEntity.Profession.RESIDENT);
        work(clerk);
        clerk.backpack().addItem(new ItemStack(Items.BREAD, 4));
        visitor.backpack().addItem(new ItemStack(Items.EMERALD, 2));
        long now = level.getGameTime();

        visitor.moveTo(at.getX() + 9.5, at.getY(), at.getZ() + 0.5, 0, 0);
        if (ShopCounter.purchaseByResident(clerk, visitor, now))
            throw new AssertionError("Bought from more than 3 blocks away");
        visitor.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        clerk.setDecision(new LivingPlanner.Decision(LivingPlanner.Intent.SLEEP, 100, "shop closed"));
        if (ShopCounter.purchaseByResident(clerk, visitor, now))
            throw new AssertionError("Shopkeeper sold food while sleeping/off shift");
        work(clerk);
        clerk.memory().witness(now, visitor.getUUID(),
            ResidentMemory.Kind.THEFT_WITNESSED, "actual crime test");
        if (ShopCounter.purchaseByResident(clerk, visitor, now))
            throw new AssertionError("Shopkeeper ignored a witnessed thief");
        if (visitor.backpack().countItem(Items.EMERALD) != 2
            || clerk.backpack().countItem(Items.BREAD) != 4)
            throw new AssertionError("Denied transactions modified NPC physical goods");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void crowded_real_market_twelve_stocked_meals_feed_only_twelve_of_sixteen_buyers(
        GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos home = helper.absolutePos(new BlockPos(5, 9, 5));
        PlayerNpcEntity clerk = spawn(level, home, PlayerNpcEntity.Profession.SHOPKEEPER);
        work(clerk);
        clerk.backpack().addItem(new ItemStack(Items.BREAD, 12));
        int succeeded = 0, refused = 0;
        for (int i = 0; i < 16; i++) {
            PlayerNpcEntity buyer = spawn(level, home.east(), PlayerNpcEntity.Profession.RESIDENT);
            buyer.backpack().addItem(new ItemStack(Items.EMERALD, 1));
            if (ShopCounter.purchaseByResident(clerk, buyer, level.getGameTime())) {
                succeeded++;
                if (buyer.backpack().countItem(Items.BREAD) != 1
                    || buyer.backpack().countItem(Items.EMERALD) != 0)
                    throw new AssertionError("Paid purchaser failed to receive ONE existing meal");
            } else {
                refused++;
                if (buyer.backpack().countItem(Items.EMERALD) != 1
                    || buyer.backpack().countItem(Items.BREAD) != 0)
                    throw new AssertionError("Sold out shop still charged a real NPC");
            }
        }
        if (succeeded != 12 || refused != 4
            || clerk.backpack().countItem(Items.EMERALD) != 12
            || clerk.backpack().countItem(Items.BREAD) != 0)
            throw new AssertionError("Crowded market minted goods/cash or oversold out-of-stock items");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void weekly_fair_is_local_social_and_rate_limited(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(5, 9, 5));
        PlayerNpcEntity host = spawn(level, at, PlayerNpcEntity.Profession.SHOPKEEPER);
        PlayerNpcEntity guest = spawn(level, at.east(), PlayerNpcEntity.Profession.RESIDENT);
        long tick = level.getGameTime();
        if (!NeighborhoodMarket.fairWindow(2L * 24000 + 9000, 0)
            || NeighborhoodMarket.fairWindow(3L * 24000 + 9000, 0)
            || NeighborhoodMarket.fairWindow(2L * 24000 + 13000, 0))
            throw new AssertionError("Weekly market schedule crossed to a wrong time/day");
        int socialBefore = guest.socialNeed();
        if (!NeighborhoodMarket.greetAtFair(guest, host, tick)
            || guest.socialNeed() < socialBefore
            || guest.memory().trust(host.getUUID()) <= 0
            || host.memory().trust(guest.getUUID()) <= 0
            || guest.nextSocialTick() <= tick)
            throw new AssertionError("Weekly market failed real meeting and relationship");
        int recorded = guest.memory().recentEvents().size();
        if (NeighborhoodMarket.greetAtFair(guest, host, tick + 1)
            || guest.memory().recentEvents().size() != recorded)
            throw new AssertionError("Fair generated repeated spam interactions during cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void a_real_hungry_citizen_buys_from_a_working_nearby_shop_in_autonomous_ticks(
        GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(5, 9, 5));
        for (int dx = -3; dx <= 5; dx++) for (int dz = -3; dz <= 5; dz++) {
            BlockPos floor = origin.offset(dx, -1, dz);
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 2);
        }
        PlayerNpcEntity shop = spawn(level, origin, PlayerNpcEntity.Profession.SHOPKEEPER);
        PlayerNpcEntity guest = spawn(level, origin.east(2), PlayerNpcEntity.Profession.RESIDENT);
        work(shop);
        guest.changeNeeds(-48, 0, 0);
        shop.backpack().addItem(new ItemStack(Items.BREAD, 1));
        guest.backpack().addItem(new ItemStack(Items.EMERALD, 1));
        final long start = level.getGameTime();
        helper.runAfterDelay(180, () -> {
            int sellerCash = shop.backpack().countItem(Items.EMERALD);
            int guestCash = guest.backpack().countItem(Items.EMERALD);
            // The buyer may already have eaten the paid bread by this point.
            // We verify a cash transfer AND an actual real-stock withdrawal.
            System.out.println("REME_NPC_AUTONOMOUS_MARKET_TICKS=180"
                + " shopEmerald=" + sellerCash + " guestEmerald=" + guestCash
                + " shopBread=" + shop.backpack().countItem(Items.BREAD)
                + " buyerHunger=" + guest.hunger()
                + " shopAction=" + shop.lastAction()
                + " guestAction=" + guest.lastAction()
                + " start=" + start);
            if (sellerCash != 1 || guestCash != 0
                || shop.backpack().countItem(Items.BREAD) != 0)
                helper.fail("Autonomous vanilla-world customer did not pay shop for real bread", origin);
            else helper.succeed();
        });
    }

    /**
     * Server-side player-interaction regression. FakePlayer is used ONLY as
     * a test caller, not as an NPC actor or a substitute for GUI acceptance.
     * No time-of-day mutation escapes this synchronous test.
     */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void player_shop_interaction_requires_real_shift_and_held_payment(
        GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(5, 9, 5));
        PlayerNpcEntity clerk = spawn(level, at, PlayerNpcEntity.Profession.SHOPKEEPER);
        var buyer = FakePlayerFactory.getMinecraft(level);
        buyer.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        buyer.getInventory().clearContent();
        buyer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 3));
        clerk.backpack().addItem(new ItemStack(Items.BREAD, 3));
        long oldDayTime = level.getDayTime();
        try {
            work(clerk);
            level.setDayTime(13000);
            if (ShopCounter.purchase(clerk, buyer, buyer.getMainHandItem()))
                throw new AssertionError("Human customer bought from a CLOSED night shop");
            level.setDayTime(1000);
            clerk.setDecision(new LivingPlanner.Decision(
                LivingPlanner.Intent.SLEEP, 100, "clerk off duty"));
            if (ShopCounter.purchase(clerk, buyer, buyer.getMainHandItem()))
                throw new AssertionError("Human customer bought from a SLEEPING clerk");
            work(clerk);
            if (ShopCounter.purchase(clerk, buyer, new ItemStack(Items.EMERALD, 1)))
                throw new AssertionError("Detached payment minted a physical shop item");
            if (clerk.backpack().countItem(Items.BREAD) != 3
                || clerk.backpack().countItem(Items.EMERALD) != 0
                || buyer.getMainHandItem().getCount() != 3
                || buyer.getInventory().countItem(Items.BREAD) != 0)
                throw new AssertionError("Refused player sale mutated goods or currency");
            if (!ShopCounter.purchase(clerk, buyer, buyer.getMainHandItem())
                || clerk.backpack().countItem(Items.BREAD) != 2
                || clerk.backpack().countItem(Items.EMERALD) != 1
                || buyer.getMainHandItem().getCount() != 2
                || buyer.getInventory().countItem(Items.BREAD) != 1)
                throw new AssertionError("Real held emerald did not buy exactly one stocked bread");
        } finally {
            level.setDayTime(oldDayTime);
            buyer.getInventory().clearContent();
        }
        helper.succeed();
    }
}
