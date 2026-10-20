package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.Comparator;
import java.util.List;

/**
 * Explicitly assigned single vanilla chest logistics only.
 * Does not search arbitrary/private chests or simulate an unloaded chunk.
 * All transfers are checked against two temporary inventory snapshots.
 */
public final class SupplyLogistics {
    private SupplyLogistics() {}

    public static boolean deposit(PlayerNpcEntity npc, ServerLevel level) {
        BlockPos pos = npc.supplyPos();
        if (pos == null || !level.hasChunkAt(pos)) return false;
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof ChestBlockEntity chest)) return false;
        if (npc.blockPosition().distSqr(pos) > 8) {
            RoadNavigator.moveToward(npc, level, pos, 0.95);
            return true;
        }
        SimpleContainer trialBag = copy(npc.backpack());
        SimpleContainer trialChest = copy(chest);
        int moved = 0;
        for (int i = 0; i < trialBag.getContainerSize() && moved < 8; i++) {
            ItemStack stack = trialBag.getItem(i);
            if (stack.isEmpty()) continue;
            if (!(stack.is(Items.WHEAT) || stack.is(Items.BREAD) ||
                  stack.is(Items.CARROT) || stack.is(Items.POTATO) ||
                  stack.is(Items.BEETROOT))) continue;
            // Preserve two bread for resident's own survival.
            if (stack.is(Items.BREAD) && stack.getCount() <= 2) continue;
            ItemStack one = stack.copyWithCount(1);
            if (trialChest.addItem(one).isEmpty()) {
                stack.shrink(1);
                moved++;
            }
        }
        if (moved == 0) {
            npc.setAction("assigned supply chest is full or no eligible goods");
            return false;
        }
        apply(npc.backpack(), trialBag);
        apply(chest, trialChest);
        npc.recordDelivery();
        npc.setAction("deposited " + moved + " real goods in assigned chest");
        return true;
    }

    public static boolean withdraw(PlayerNpcEntity npc, ServerLevel level, Item wanted, int count) {
        BlockPos pos = npc.supplyPos();
        if (pos == null || !level.hasChunkAt(pos) || count <= 0 || count > 8) return false;
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof ChestBlockEntity chest)) return false;
        SimpleContainer bag = copy(npc.backpack());
        SimpleContainer box = copy(chest);
        int available = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack stack = box.getItem(i);
            if (stack.is(wanted)) available += stack.getCount();
        }
        if (available < count) return false;
        if (npc.blockPosition().distSqr(pos) > 8) {
            RoadNavigator.moveToward(npc, level, pos, 0.95);
            return true;
        }
        for (int i = 0; i < box.getContainerSize() && count > 0; i++) {
            ItemStack stack = box.getItem(i);
            if (!stack.is(wanted)) continue;
            int take = Math.min(stack.getCount(), count);
            if (!bag.addItem(stack.copyWithCount(take)).isEmpty()) return false;
            stack.shrink(take);
            count -= take;
        }
        apply(npc.backpack(), bag);
        apply(chest, box);
        npc.setAction("collected real goods from assigned supply chest");
        return true;
    }

    /**
     * Community courier collects surplus wheat from a consenting, non-hostile
     * farmer in the same region. No free items: both bags update as a transaction.
     */
    public static boolean collectFromFarmer(PlayerNpcEntity courier, ServerLevel level) {
        if (courier.supplyPos() == null) return false;
        List<PlayerNpcEntity> farmers = level.getEntitiesOfClass(PlayerNpcEntity.class,
            courier.getBoundingBox().inflate(14), other ->
                other != courier && other.isAlive() &&
                other.profession() == PlayerNpcEntity.Profession.FARMER &&
                !courier.hostileTo(other) &&
                other.identity().region().equals(courier.identity().region()) &&
                other.backpack().countItem(Items.WHEAT) >= 7);
        if (farmers.isEmpty()) return false;
        PlayerNpcEntity farmer = farmers.stream()
            .min(Comparator.comparingDouble(courier::distanceToSqr)).orElseThrow();
        if (courier.distanceToSqr(farmer) > 8) {
            RoadNavigator.moveToward(courier, level, farmer.blockPosition(), 0.95);
            courier.setAction("travelling to farmer for regional supply delivery");
            return true;
        }
        SimpleContainer c = copy(courier.backpack());
        SimpleContainer f = copy(farmer.backpack());
        int available = f.countItem(Items.WHEAT);
        int amount = Math.min(6, available - 3); // keep a reserve with the farmer
        if (amount < 1) return false;
        ItemStack cargo = new ItemStack(Items.WHEAT, amount);
        if (!c.addItem(cargo).isEmpty()) return false;
        int left = amount;
        for (int i = 0; i < f.getContainerSize() && left > 0; i++) {
            ItemStack stack = f.getItem(i);
            if (!stack.is(Items.WHEAT)) continue;
            int taken = Math.min(left, stack.getCount());
            stack.shrink(taken);
            left -= taken;
        }
        if (left != 0) return false;
        apply(courier.backpack(), c);
        apply(farmer.backpack(), f);
        courier.setAction("collected " + amount + " existing wheat from farmer");
        farmer.setAction("shared " + amount + " wheat with assigned regional courier");
        return true;
    }

    private static SimpleContainer copy(net.minecraft.world.Container original) {
        SimpleContainer copy = new SimpleContainer(original.getContainerSize());
        for (int i = 0; i < original.getContainerSize(); i++)
            copy.setItem(i, original.getItem(i).copy());
        return copy;
    }

    private static void apply(net.minecraft.world.Container target, SimpleContainer source) {
        for (int i = 0; i < target.getContainerSize(); i++)
            target.setItem(i, source.getItem(i).copy());
        target.setChanged();
    }
}
