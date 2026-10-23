package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Actual unpaid retail stock -> street escape -> FIRSTHAND shopkeeper report
 * -> police chase/custody. All actions take place on the server game thread,
 * in loaded chunks, at walkable real block positions, with real physical
 * stock. Dishonest shoppers are STILL civilians until actual theft is seen;
 * guards cannot arrest them just for low honesty, rumours, or lack of money.
 *
 * Unlike fake 'crime level' command events, local visible guard coverage
 * changes how often a resident attempts unpaid shopping; it never spawns
 * items or suspects automatically.
 */
public final class ConvenienceStoreCrime {
    private ConvenienceStoreCrime() {}
    public static final int MAX_STORE_DISTANCE = 12;
    public static final int MAX_GUARD_SECURITY = 2;

    /** Real patrolling guards physically near a shop determine deterrence. */
    public static int nearbySecurity(PlayerNpcEntity shop, ServerLevel level, long tick) {
        int guards = 0;
        for (PlayerNpcEntity npc : level.getEntitiesOfClass(
                PlayerNpcEntity.class, shop.getBoundingBox().inflate(12),
                other -> other != shop && other.isAlive()
                    && other.profession() == PlayerNpcEntity.Profession.GUARD
                    && other.mode() == PlayerNpcEntity.Mode.LIVE
                    && !other.isSleeping() && !other.isDetained(tick)
                    && !other.isAfraid(tick)
                    && other.identity().region().equals(shop.identity().region())
                    && level.hasChunkAt(other.blockPosition()))) {
            guards++;
            if (guards >= MAX_GUARD_SECURITY) break;
        }
        return guards;
    }

    /** 0 guards = 75% / 1 = 30% / 2+ = 5%, per rare visit attempt. */
    public static int escapeWithoutPayChance(int guardCoverage) {
        return guardCoverage == 0 ? 75 : guardCoverage == 1 ? 30 : 5;
    }

    public static boolean tick(PlayerNpcEntity shopper, ServerLevel level, long tick) {
        if (!level.getServer().isSameThread() || !shopper.isAlive()
            || shopper.mode() != PlayerNpcEntity.Mode.LIVE
            || shopper.profession() != PlayerNpcEntity.Profession.RESIDENT
            || shopper.isSleeping() || shopper.isDetained(tick)) return false;

        if (shopper.hasUnpaidShopItem(tick)) {
            // Actually unconscious: no script-driven fleeing while knocked down.
            if (shopper.isPoliceStunned(tick)) {
                shopper.getNavigation().stop();
                shopper.setAction("unconscious after nonlethal officer strikes; awaiting handcuffs");
                return true;
            }
            // Don't let hunger/social AI override an actual criminal escape.
            PlayerNpcEntity merchant = level.getEntity(shopper.unpaidMerchant())
                instanceof PlayerNpcEntity s ? s : null;
            if (merchant == null || !merchant.isAlive()) {
                // No omniscient global dispatch or remote chunk loading.
                shopper.setAction("fleeing with unpaid real goods; owner absent");
                return false;
            }
            if (!shopper.shopCrimeReported()) {
                if (shopper.distanceToSqr(merchant) >= 36
                    && shopper.distanceToSqr(merchant) <= 256) {
                    int officers = PublicSafety.reportTheft(
                        merchant, shopper, level, shopper.unpaidIncident(), tick);
                    if (officers > 0) {
                        shopper.markShopCrimeReported();
                        System.out.println("REME_NPC_STORY stage=police_report tick="
                            + tick + " shop=" + merchant.getUUID()
                            + " suspect=" + shopper.getUUID()
                            + " dispatched=" + officers);
                    } else {
                        merchant.setAction("noticed unpaid customer leaving; no nearby responding police");
                    }
                }
                BlockPos exit = merchant.workPos().east(9);
                if (DailyRoaming.inRange(shopper, exit) && level.hasChunkAt(exit)
                    && shopper.blockPosition().distSqr(exit) > 4)
                    RoadNavigator.moveToward(shopper, level, exit, 1.12);
                shopper.setAction("leaving staffed convenience store with stolen real food, unpaid");
                return true;
            }
            // Continue REAL evasion after the initial shop door. The old
            // east(13) stationary marker made a thief wait for the officer.
            // Replan only when a genuine vanilla navigation path is exhausted.
            if (MovementPolicy.due(tick, shopper.getId(), 20)
                && shopper.getNavigation().isDone())
                fleeAlongLoadedStreets(shopper, merchant, level);
            return true;
        }

        if (shopper.character().honesty() > 20
            || tick < shopper.nextShopCrimeTick()
            || !MovementPolicy.due(tick, shopper.getId(), 80)
            || shopper.isAfraid(tick) || shopper.energy() < 25
            || shopper.backpack().countItem(Items.BREAD) > 0
            || shopper.backpack().countItem(Items.CARROT) > 0
            || shopper.backpack().countItem(Items.APPLE) > 0) return false;

        List<PlayerNpcEntity> shops = level.getEntitiesOfClass(
            PlayerNpcEntity.class, shopper.getBoundingBox().inflate(MAX_STORE_DISTANCE),
            other -> other != shopper && other.isAlive()
                && ShopCounter.openForResidentSales(other)
                && !other.isAfraid(tick) && !other.isDetained(tick)
                && other.identity().region().equals(shopper.identity().region())
                && !other.hostileTo(shopper) && !shopper.hostileTo(other)
                && ShopCounter.stockedFood(other.backpack()) > 0
                && DailyRoaming.inRange(shopper, other.workPos())
                && level.hasChunkAt(other.blockPosition()));
        if (shops.isEmpty()) return false;
        PlayerNpcEntity seller = shops.stream()
            .min(Comparator.comparingDouble(shopper::distanceToSqr)).orElseThrow();
        if (shopper.distanceToSqr(seller) > 6.25) {
            if (RoadNavigator.moveToward(shopper, level, seller.blockPosition(), 0.92)) {
                shopper.setAction("entering convenience store to browse without money");
                return true;
            }
            return false;
        }
        shopper.postponeShopCrime(tick + 240L);
        if (shopper.getRandom().nextInt(100)
            >= escapeWithoutPayChance(nearbySecurity(seller, level, tick)))
            return false;
        return takeUnpaidFood(shopper, seller, level, tick);
    }

    /** Choose a real walkable street tile AWAY from the nearest pursuing
     * officer. No teleport, fake escape event, generated terrain or chunk load.
     * Returning false means the suspect is physically trapped, not escaped. */
    private static boolean fleeAlongLoadedStreets(PlayerNpcEntity thief,
                                                   PlayerNpcEntity merchant,
                                                   ServerLevel level) {
        PlayerNpcEntity pursuing = level.getEntitiesOfClass(
            PlayerNpcEntity.class, thief.getBoundingBox().inflate(18),
            npc -> npc.isAlive() && npc.profession() == PlayerNpcEntity.Profession.GUARD
                && thief.getUUID().equals(npc.policeSuspect()))
            .stream().min(Comparator.comparingDouble(thief::distanceToSqr))
            .orElse(null);
        BlockPos origin = thief.blockPosition();
        BlockPos best = null;
        double highest = Double.NEGATIVE_INFINITY;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos goal = origin.relative(dir, 7);
            if (!DailyRoaming.inRange(thief, goal) || !level.hasChunkAt(goal)
                || !level.hasChunkAt(goal.below())
                || !level.getBlockState(goal).isAir()
                || !level.getBlockState(goal.above()).isAir()) continue;
            var floor = level.getBlockState(goal.below());
            if (!(floor.is(Blocks.GRAY_CONCRETE) || floor.is(Blocks.SMOOTH_STONE)
                || floor.is(Blocks.WHITE_CONCRETE) || floor.is(Blocks.YELLOW_CONCRETE)
                || floor.is(Blocks.STONE_BRICKS) || floor.is(Blocks.GRAVEL))) continue;
            double score = goal.distSqr(merchant.blockPosition()) * 0.2;
            if (pursuing != null) score += goal.distSqr(pursuing.blockPosition());
            if (score > highest) { highest = score; best = goal; }
        }
        if (best == null) return false;
        boolean moving = RoadNavigator.moveToward(thief, level, best, 1.32);
        if (moving) {
            thief.setAction("actively fleeing pursuing guard along physical city road");
            System.out.println("REME_NPC_STORY stage=active_flight suspect="
                + thief.getUUID() + " x=" + thief.getX() + " z=" + thief.getZ()
                + " destination=" + best.toShortString()
                + " officer=" + (pursuing == null ? "none" : pursuing.getUUID()));
        }
        return moving;
    }

    /**
     * Call ONLY for an actually arrived shopper. Real item transfers atomically;
     * marks an evidence-backed pending unpaid check, NOT an immediate arrest.
     */
    public static boolean takeUnpaidFood(PlayerNpcEntity shopper,
                                         PlayerNpcEntity seller,
                                         ServerLevel level, long tick) {
        if (!level.getServer().isSameThread() || shopper == seller
            || !shopper.isAlive() || !seller.isAlive()
            || shopper.character().honesty() > 20
            || shopper.profession() != PlayerNpcEntity.Profession.RESIDENT
            || shopper.mode() != PlayerNpcEntity.Mode.LIVE
            || shopper.isDetained(tick) || shopper.isSleeping()
            || !ShopCounter.openForResidentSales(seller)
            || shopper.hasUnpaidShopItem(tick)
            || shopper.distanceToSqr(seller) > 6.25
            || Math.abs(shopper.getY() - seller.getY()) > 1.5
            || !shopper.hasLineOfSight(seller)
            || !seller.identity().region().equals(shopper.identity().region())
            || !level.hasChunkAt(shopper.blockPosition())
            || !level.hasChunkAt(seller.blockPosition())) return false;
        Item kind = seller.backpack().countItem(Items.BREAD) > 0 ? Items.BREAD
            : seller.backpack().countItem(Items.APPLE) > 0 ? Items.APPLE
            : seller.backpack().countItem(Items.CARROT) > 0 ? Items.CARROT : null;
        if (kind == null) return false;
        SimpleContainer from = snapshot(seller.backpack());
        SimpleContainer to = snapshot(shopper.backpack());
        if (!withdraw(from, kind)
            || !to.addItem(new ItemStack(kind)).isEmpty()) return false;
        commit(seller.backpack(), from);
        commit(shopper.backpack(), to);
        UUID incident = UUID.randomUUID();
        // Shopkeeper personally sees the product leave the real shop; a
        // civilian shopper is NOT suddenly changed to a hostile Raider faction.
        seller.civic().witness(incident, shopper.getUUID(), seller.getUUID(),
            CivicMind.Kind.THEFT, tick);
        seller.memory().witness(tick, shopper.getUUID(),
            ResidentMemory.Kind.THEFT_WITNESSED, seller.blockPosition().toShortString());
        shopper.markUnpaidShopItem(seller.getUUID(), incident, tick);
        seller.setAction("witnessed resident taking REAL " + kind + " without paying; monitoring exit");
        shopper.setAction("took one real product without payment; trying to leave store");
        System.out.println("REME_NPC_STORY stage=unpaid_pickup tick=" + tick
            + " shop=" + seller.getUUID() + " suspect=" + shopper.getUUID()
            + " goods=" + kind + " quantity=1 payment=0");
        return true;
    }

    private static SimpleContainer snapshot(SimpleContainer original) {
        SimpleContainer bag = new SimpleContainer(original.getContainerSize());
        for (int i=0;i<bag.getContainerSize();i++)
            bag.setItem(i, original.getItem(i).copy());
        return bag;
    }
    private static boolean withdraw(SimpleContainer bag, Item item) {
        for (int i=0;i<bag.getContainerSize();i++)
            if (bag.getItem(i).is(item) && !bag.getItem(i).isEmpty()) {
                bag.getItem(i).shrink(1);
                return true;
            }
        return false;
    }
    private static void commit(SimpleContainer dest, SimpleContainer from) {
        for (int i=0;i<dest.getContainerSize();i++)
            dest.setItem(i, from.getItem(i).copy());
        dest.setChanged();
    }
}
