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
        if (npc.blockPosition().distSqr(pos) > 8) {
            RoadNavigator.moveToward(npc, level, pos, 0.95);
            return true;
        }
        SimpleContainer bag = copy(npc.backpack());
        SimpleContainer box = copy(chest);
        int available = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack stack = box.getItem(i);
            if (stack.is(wanted)) available += stack.getCount();
        }
        if (available < count) return false;
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
