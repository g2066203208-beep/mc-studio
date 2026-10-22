package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Honest local shop counter. One emerald for one actual stock unit: no loot
 * tables, fabricated goods, infinite emeralds or inventory side channels.
 *
 * At this stage staff stock is their own 36-slot physical NBT backpack.
 * A full city shop/warehouse/price/economy system remains separate work.
 */
public final class ShopCounter {
    private ShopCounter() {}

    public static boolean purchase(PlayerNpcEntity clerk, Player buyer,
                                   ItemStack payment) {
        if (clerk.profession() != PlayerNpcEntity.Profession.SHOPKEEPER
            || clerk.mode() != PlayerNpcEntity.Mode.LIVE
            || clerk.isDetained(clerk.level().getGameTime())
            || !clerk.isAlive() || !buyer.isAlive()
            || !payment.is(Items.EMERALD) || payment.isEmpty()
            || buyer.distanceToSqr(clerk) > 16.0
            || clerk.memory().trust(buyer.getUUID()) <= -25
            || clerk.civic().standing(buyer.getUUID(), clerk.character().justice())
                == CivicMind.Standing.WANTED
            || buyer.getInventory().getFreeSlot() < 0) return false;

        // Use deterministic, understandable menu-free convenience assortment;
        // seller needs to actually have each item. Future UI can list stock.
        Item product = firstStock(clerk.backpack());
        if (product == null) return false;
        SimpleContainer tentative = new SimpleContainer(clerk.backpack().getContainerSize());
        for (int slot = 0; slot < tentative.getContainerSize(); slot++)
            tentative.setItem(slot, clerk.backpack().getItem(slot).copy());

        boolean withdrawn = false;
        for (int slot = 0; slot < tentative.getContainerSize(); slot++) {
            ItemStack stack = tentative.getItem(slot);
            if (stack.is(product) && !stack.isEmpty()) {
                stack.shrink(1);
                withdrawn = true;
                break;
            }
        }
        if (!withdrawn || !tentative.addItem(new ItemStack(Items.EMERALD)).isEmpty())
            return false;

        // Entire transaction is server main thread and checked before commit.
        // No partial insertion or loss of emerald/stock on failed purchase.
        for (int slot = 0; slot < tentative.getContainerSize(); slot++)
            clerk.backpack().setItem(slot, tentative.getItem(slot).copy());
        clerk.backpack().setChanged();
        payment.shrink(1);
        buyer.getInventory().setItem(buyer.getInventory().getFreeSlot(),
            new ItemStack(product));
        clerk.setAction("sold one real " + product.toString() + " for one emerald");
        clerk.journey().succeeded(LifeJourney.Achievement.BARTER);
        return true;
    }

    private static Item firstStock(SimpleContainer bag) {
        for (Item item : new Item[]{Items.BREAD, Items.APPLE, Items.CARROT}) {
            if (bag.countItem(item) > 0) return item;
        }
        return null;
    }

    public static int stockedFood(SimpleContainer bag) {
        return bag.countItem(Items.BREAD) + bag.countItem(Items.APPLE)
            + bag.countItem(Items.CARROT);
    }

    /**
     * OP-approved single real vanilla chest -> NPC's persisted actual
     * backpack. The two inventories are snapshotted BEFORE changing either.
     * Does not copy resources from unassigned chests, force-load chunks or
     * create goods on its own. Caller must be physically near the chest.
     */
    public static int restock(PlayerNpcEntity clerk, ServerLevel level) {
        BlockPos spot = clerk.shopStockChest();
        if (spot == null || clerk.profession() != PlayerNpcEntity.Profession.SHOPKEEPER
            || clerk.mode() != PlayerNpcEntity.Mode.LIVE || !clerk.isAlive()
            || clerk.isDetained(level.getGameTime())
            || !DailyRoaming.inRange(clerk, spot)
            || !level.isInWorldBounds(spot)
            || !level.getWorldBorder().isWithinBounds(spot)
            || !level.hasChunkAt(spot)
            || clerk.distanceToSqr(Vec3.atCenterOf(spot)) > 9.0
            || !(level.getBlockEntity(spot) instanceof ChestBlockEntity source))
            return 0;
        SimpleContainer from = snapshot(source);
        SimpleContainer to = snapshot(clerk.backpack());
        for (Item kind : new Item[]{Items.BREAD, Items.APPLE, Items.CARROT}) {
            int available = Math.min(8, from.countItem(kind));
            for (int count = available; count >= 1; count--) {
                SimpleContainer trial = snapshot(to);
                if (!trial.addItem(new ItemStack(kind, count)).isEmpty()) continue;
                int remaining = count;
                for (int i=0; i<from.getContainerSize() && remaining>0; i++) {
                    ItemStack stack = from.getItem(i);
                    if (!stack.is(kind)) continue;
                    int consumed = Math.min(remaining, stack.getCount());
                    stack.shrink(consumed);
                    remaining -= consumed;
                }
                if (remaining != 0) return 0;
                commit(source, from);
                commit(clerk.backpack(), trial);
                clerk.setAction("collected " + count + " existing shop goods from assigned real supply chest");
                return count;
            }
        }
        return 0;
    }

    private static SimpleContainer snapshot(Container source) {
        SimpleContainer copy = new SimpleContainer(source.getContainerSize());
        for (int i=0; i<source.getContainerSize(); i++)
            copy.setItem(i, source.getItem(i).copy());
        return copy;
    }

    private static void commit(Container target, SimpleContainer source) {
        for (int i=0; i<target.getContainerSize(); i++)
            target.setItem(i, source.getItem(i).copy());
        target.setChanged();
    }

    public static boolean tick(PlayerNpcEntity clerk, ServerLevel level, long tick) {
        if (clerk.profession() != PlayerNpcEntity.Profession.SHOPKEEPER
            || clerk.mode() != PlayerNpcEntity.Mode.LIVE
            || clerk.intent() != LivingPlanner.Intent.WORK) return false;
        if (!MovementPolicy.due(tick, clerk.getId(), 40)) return true;
        if (!level.hasChunkAt(clerk.workPos())
            || !DailyRoaming.inRange(clerk, clerk.workPos())) {
            clerk.setAction("shop closed: configured counter outside loaded working area");
            return true;
        }
        // A real store can resupply itself, but ONLY from the chest an OP
        // explicitly designated for this citizen. No global loot scanning.
        BlockPos supply = clerk.shopStockChest();
        if (supply != null && stockedFood(clerk.backpack()) < 4) {
            if (!level.hasChunkAt(supply)
                || !(level.getBlockEntity(supply) instanceof ChestBlockEntity)) {
                clerk.setAction("shop needs stock: supply chest removed or unloaded");
                return true;
            }
            if (clerk.distanceToSqr(Vec3.atCenterOf(supply)) > 9.0) {
                RoadNavigator.moveToward(clerk, level, supply, 0.88);
                clerk.setAction("travelling to authorized real shop supplies");
                return true;
            }
            if (restock(clerk, level) > 0) return true;
        }
        if (clerk.blockPosition().distSqr(clerk.workPos()) > 6) {
            RoadNavigator.moveToward(clerk, level, clerk.workPos(), 0.83);
            clerk.setAction("walking to shop counter for real daytime shift");
        } else {
            clerk.getNavigation().stop();
            clerk.setAction(firstStock(clerk.backpack()) == null
                ? "shop open but out of actual bread/apple/carrot stock"
                : "shop open: selling physical supplies for real emeralds");
        }
        return true;
    }
}
