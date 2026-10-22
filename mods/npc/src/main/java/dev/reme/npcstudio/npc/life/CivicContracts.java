package dev.reme.npcstudio.npc.life;

import dev.reme.npcstudio.npc.PlayerNpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.UUID;

/** Real material and emerald escrow; no synthetic quest rewards. */
public final class CivicContracts {
    private CivicContracts() {}

    private static Item item(Commission.Goods type) {
        return switch (type) {
            case WHEAT -> Items.WHEAT;
            case BREAD -> Items.BREAD;
            case CARROT -> Items.CARROT;
            case POTATO -> Items.POTATO;
        };
    }

    private static int count(SimpleContainer inventory, Item item) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++)
            if (inventory.getItem(i).is(item))
                total += inventory.getItem(i).getCount();
        return total;
    }

    private static void remove(SimpleContainer inventory, Item item, int amount) {
        for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(item)) continue;
            int used = Math.min(amount, stack.getCount());
            stack.shrink(used);
            amount -= used;
        }
        inventory.setChanged();
    }

    public static boolean post(PlayerNpcEntity npc, Commission.Goods goods,
                               int amount, int reward) {
        if (npc.commission() != null &&
            (npc.commission().state() == Commission.State.OPEN ||
             npc.commission().state() == Commission.State.ACCEPTED)) return false;
        Commission contract;
        try { contract = new Commission(goods, amount, reward); }
        catch (IllegalArgumentException bad) { return false; }
        if (count(npc.backpack(), Items.EMERALD) < reward) return false;
        // Escrow is removed from actual backpack now; NBT contract owns it.
        remove(npc.backpack(), Items.EMERALD, reward);
        npc.setCommission(contract);
        npc.setAction("posted funded commission for " + amount + " " + goods);
        return true;
    }

    public static boolean accept(PlayerNpcEntity npc, ServerPlayer player) {
        Commission job = npc.commission();
        if (job == null || npc.civic().standing(player.getUUID(),
            npc.character().justice()) == CivicMind.Standing.WANTED)
            return false;
        return job.accept(player.getUUID());
    }

    public static boolean deliver(PlayerNpcEntity npc, ServerPlayer player) {
        Commission job = npc.commission();
        if (job == null || !job.canComplete(player.getUUID())) return false;
        ItemStack held = player.getMainHandItem();
        if (!held.is(item(job.goods())) || held.getCount() < job.quantity())
            return false;
        int freeSlot = player.getInventory().getFreeSlot();
        if (freeSlot < 0) return false;
        SimpleContainer trial = new SimpleContainer(npc.backpack().getContainerSize());
        for (int i = 0; i < trial.getContainerSize(); i++)
            trial.setItem(i, npc.backpack().getItem(i).copy());
        if (!trial.addItem(held.copyWithCount(job.quantity())).isEmpty()) return false;

        // All failure checks above this line. No partial payment or item minting.
        held.shrink(job.quantity());
        for (int i = 0; i < trial.getContainerSize(); i++)
            npc.backpack().setItem(i, trial.getItem(i).copy());
        npc.backpack().setChanged();
        player.getInventory().setItem(freeSlot, new ItemStack(Items.EMERALD, job.emeraldReward()));
        job.finish(player.getUUID());
        npc.memory().witness(npc.level().getGameTime(), player.getUUID(),
            ResidentMemory.Kind.HELPED, npc.blockPosition().toShortString());
        CivicSocial.giveCredit(npc, player, CivicMind.Kind.HONORED_CONTRACT);
        npc.setAction("completed real-goods commission with " + player.getName().getString());
        return true;
    }

    public static boolean cancel(PlayerNpcEntity npc) {
        Commission job = npc.commission();
        if (job == null || job.state() == Commission.State.COMPLETED ||
            job.state() == Commission.State.CANCELLED) return false;
        SimpleContainer trial = new SimpleContainer(npc.backpack().getContainerSize());
        for (int i = 0; i < trial.getContainerSize(); i++)
            trial.setItem(i, npc.backpack().getItem(i).copy());
        if (!trial.addItem(new ItemStack(Items.EMERALD,
            job.emeraldReward())).isEmpty()) return false;
        for (int i = 0; i < trial.getContainerSize(); i++)
            npc.backpack().setItem(i, trial.getItem(i).copy());
        npc.backpack().setChanged();
        job.cancel();
        return true;
    }
}
