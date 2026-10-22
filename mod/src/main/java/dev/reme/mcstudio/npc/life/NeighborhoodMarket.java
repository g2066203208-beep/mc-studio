package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * Resident-to-resident economy in genuinely loaded Minecraft chunks.
 *
 * A hungry resident with a REAL emerald walks to an open shop and purchases
 * existing food. A funded shopkeeper visits a farmer with REAL carrots to buy
 * wholesale produce. There is no invented cash, remote player chest access,
 * virtual stock, teleported citizen or global entity scan.
 */
public final class NeighborhoodMarket {
    private NeighborhoodMarket() {}
    private static final int BUYER_RADIUS = 18;
    private static final int SUPPLIER_RADIUS = 12;

    public static boolean needsMarket(PlayerNpcEntity npc, long tick) {
        return npc.mode() == PlayerNpcEntity.Mode.LIVE
            && !npc.isSleeping() && !npc.isAfraid(tick)
            && !npc.isDetained(tick)
            && npc.identity().faction() != SocietyRules.Faction.RAIDERS
            && npc.hunger() <= 65 && npc.energy() >= 24
            && npc.backpack().countItem(Items.EMERALD) > 0
            && npc.backpack().countItem(Items.BREAD) == 0
            && npc.backpack().countItem(Items.APPLE) == 0
            && npc.backpack().countItem(Items.CARROT) == 0
            && npc.backpack().countItem(Items.POTATO) == 0
            && npc.backpack().countItem(Items.BAKED_POTATO) == 0
            && npc.miningTarget() == null
            && (npc.profession() != PlayerNpcEntity.Profession.COURIER
                || !CaravanRoutes.configured(npc));
    }

    private static boolean openShop(PlayerNpcEntity seller, long tick) {
        return ShopCounter.openForResidentSales(seller)
            && !seller.isAfraid(tick) && !seller.isDetained(tick);
    }

    /**
     * A real-world path takes precedence over optional wandering only when
     * the goal and funds are valid. Rechecks every 40 ticks; movement itself
     * continues between AI pulses. A full inventory cannot magically buy food.
     */
    public static boolean tickCustomer(PlayerNpcEntity buyer, ServerLevel level, long tick) {
        if (!needsMarket(buyer, tick)) return false;
        long clock = Math.floorMod(level.getDayTime() + buyer.dailyShiftTicks(), 24000L);
        if (clock >= 12500 || !MovementPolicy.due(tick, buyer.getId(), 40)) return false;
        List<PlayerNpcEntity> sellers = level.getEntitiesOfClass(
            PlayerNpcEntity.class, buyer.getBoundingBox().inflate(BUYER_RADIUS),
            other -> other != buyer && other.isAlive() && openShop(other, tick)
                && !buyer.hostileTo(other) && !other.hostileTo(buyer)
                && buyer.identity().region().equals(other.identity().region())
                && DailyRoaming.inRange(buyer, other.workPos())
                && level.hasChunkAt(other.blockPosition())
                && ShopCounter.stockedFood(other.backpack()) > 0);
        if (sellers.isEmpty()) return false;
        PlayerNpcEntity clerk = sellers.stream()
            .min(Comparator.comparingDouble(buyer::distanceToSqr)).orElseThrow();
        if (buyer.distanceToSqr(clerk) <= 9.0) {
            if (ShopCounter.purchaseByResident(clerk, buyer, tick)) return true;
            return false; // capacity/payment refusal: don't suppress all survival work
        }
        if (RoadNavigator.moveToward(buyer, level, clerk.blockPosition(), 0.93)) {
            buyer.setAction("walking to a REAL staffed shop to buy food");
            return true;
        }
        return false;
    }

    /**
     * Shop buys actual food from a local farmer with its real saved cash.
     * This is the first payroll-style income path for autonomous citizens.
     */
    public static boolean tickSupplier(PlayerNpcEntity shop, ServerLevel level, long tick) {
        if (shop.profession() != PlayerNpcEntity.Profession.SHOPKEEPER
            || shop.mode() != PlayerNpcEntity.Mode.LIVE
            || shop.isSleeping() || shop.isAfraid(tick)
            || shop.isDetained(tick)
            || shop.intent() != LivingPlanner.Intent.WORK
            || ShopCounter.stockedFood(shop.backpack()) >= 4
            || shop.backpack().countItem(Items.EMERALD) < 1
            || !MovementPolicy.due(tick, shop.getId(), 40)) return false;
        List<PlayerNpcEntity> farmers = level.getEntitiesOfClass(
            PlayerNpcEntity.class, shop.getBoundingBox().inflate(SUPPLIER_RADIUS),
            other -> other != shop && other.isAlive()
                && other.profession() == PlayerNpcEntity.Profession.FARMER
                && other.mode() == PlayerNpcEntity.Mode.LIVE
                && !other.isSleeping() && !other.isDetained(tick)
                && !other.isAfraid(tick)
                && shop.identity().region().equals(other.identity().region())
                && !other.hostileTo(shop) && !shop.hostileTo(other)
                && DailyRoaming.inRange(shop, other.blockPosition())
                && level.hasChunkAt(other.blockPosition())
                && other.backpack().countItem(Items.CARROT) >= 3);
        if (farmers.isEmpty()) return false;
        PlayerNpcEntity farmer = farmers.stream()
            .min(Comparator.comparingDouble(shop::distanceToSqr)).orElseThrow();
        if (shop.distanceToSqr(farmer) <= 9.0) {
            return ShopCounter.buyHarvestFromFarmer(shop, farmer, tick);
        }
        if (RoadNavigator.moveToward(shop, level, farmer.blockPosition(), 0.88)) {
            shop.setAction("visiting actual farmer to pay for local harvest");
            return true;
        }
        return false;
    }

    /**
     * A once-a-week town market is social, not a global scripted cutscene.
     * Citizens who are already free during leisure time walk to the stall,
     * talk to a real owner, exchange evidence-bounded rumors, and become
     * happier. A persistent daily cooldown is not required for simulation
     * consistency; the resident's built-in nextSocialTick prevents spam.
     */
    public static boolean tickFair(PlayerNpcEntity npc, ServerLevel level, long tick) {
        if (!fairWindow(level.getDayTime(), npc.dailyShiftTicks())
            || npc.mode() != PlayerNpcEntity.Mode.LIVE
            || npc.intent() != LivingPlanner.Intent.SOCIALIZE
            || npc.isSleeping() || npc.isDetained(tick) || npc.isAfraid(tick)
            || npc.hunger() <= 30 || npc.energy() <= 28
            || npc.identity().faction() == SocietyRules.Faction.RAIDERS
            || npc.nextSocialTick() > tick
            || !MovementPolicy.due(tick, npc.getId(), 100)) return false;
        List<PlayerNpcEntity> shops = level.getEntitiesOfClass(
            PlayerNpcEntity.class, npc.getBoundingBox().inflate(16),
            other -> other != npc && other.isAlive()
                && other.profession() == PlayerNpcEntity.Profession.SHOPKEEPER
                && other.mode() == PlayerNpcEntity.Mode.LIVE
                && npc.identity().region().equals(other.identity().region())
                && !npc.hostileTo(other)
                && DailyRoaming.inRange(npc, other.workPos())
                && level.hasChunkAt(other.blockPosition()));
        if (shops.isEmpty()) return false;
        PlayerNpcEntity host = shops.stream()
            .min(Comparator.comparingDouble(npc::distanceToSqr)).orElseThrow();
        if (npc.distanceToSqr(host) > 12.0) {
            if (RoadNavigator.moveToward(npc, level, host.blockPosition(), 0.85)) {
                npc.setAction("walking to weekly neighborhood market gathering");
                return true;
            }
            return false;
        }
        return greetAtFair(npc, host, tick);
    }

    /** Vanilla day zero starts at 06:00; every seventh day hosts a fair. */
    public static boolean fairWindow(long dayTime, int shift) {
        long day = Math.floorDiv(dayTime, 24000L);
        long clock = Math.floorMod(dayTime + shift, 24000L);
        return Math.floorMod(day, 7L) == 2L && clock >= 9000 && clock <= 11000;
    }

    /** Actual local social encounter; no remote news injection. */
    public static boolean greetAtFair(PlayerNpcEntity guest, PlayerNpcEntity host, long tick) {
        if (guest == host || !guest.isAlive() || !host.isAlive()
            || host.profession() != PlayerNpcEntity.Profession.SHOPKEEPER
            || guest.mode() != PlayerNpcEntity.Mode.LIVE
            || host.mode() != PlayerNpcEntity.Mode.LIVE
            || guest.isAfraid(tick) || host.isAfraid(tick)
            || guest.isDetained(tick) || host.isDetained(tick)
            || guest.nextSocialTick() > tick
            || guest.distanceToSqr(host) > 12.0
            || guest.hostileTo(host)
            || !guest.identity().region().equals(host.identity().region()))
            return false;
        // Real first-hand greeting + rumor exchange. Secondhand rumors
        // stay marked hearsay and never become fabricated eyewitness proof.
        guest.rememberGreeting(host, tick);
        host.rememberGreeting(guest, tick);
        int stories = CivicSocial.exchangeNews(guest, host, tick);
        guest.changeNeeds(0, 0, 8);
        guest.setNextSocialTick(tick + 2400L);
        boolean played = guest.level() instanceof ServerLevel live
            && TownEvents.playFairNote(guest, host, live);
        guest.setAction("met neighbors at a weekly market; shared " + stories
            + " local stories" + (played ? "; played nearby real note block" : ""));
        host.setAction("welcomed a resident at the weekly town market");
        return true;
    }
}
