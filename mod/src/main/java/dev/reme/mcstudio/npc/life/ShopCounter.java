package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.server.level.ServerLevel;
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
