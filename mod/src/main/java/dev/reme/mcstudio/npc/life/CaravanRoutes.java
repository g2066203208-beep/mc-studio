package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Explicit TWO CHEST logistics, not a virtual warehouse.
 * An OP marks both real vanilla chests. A courier loads up to 8 existing wheat,
 * physically travels, and unloads into the second chest. Carried cargo
 * remains in its actual NBT backpack during ambush or save/reload.
 *
 * No scanning private containers, no force-loading, no item creation, and
 * both sides of a transfer commit only after successful trial insertion.
 */
public final class CaravanRoutes {
    private CaravanRoutes() {}
    public static final int CAPACITY_PER_TRIP = 8;

    public static boolean configured(PlayerNpcEntity npc) {
        return npc.caravanPickup() != null && npc.caravanDropoff() != null
            && !npc.caravanPickup().equals(npc.caravanDropoff());
    }

    public static boolean tick(PlayerNpcEntity npc, ServerLevel level) {
        if (!configured(npc) || npc.mode() != PlayerNpcEntity.Mode.LIVE
            || npc.profession() != PlayerNpcEntity.Profession.COURIER) return false;
        BlockPos target = npc.backpack().countItem(Items.WHEAT) > 0
            ? npc.caravanDropoff() : npc.caravanPickup();
        if (!DailyRoaming.inRange(npc, target)
            || !level.isInWorldBounds(target)
            || !level.getWorldBorder().isWithinBounds(target)) {
            npc.setAction("caravan halted: depot outside authorized local territory");
            return true;
        }
        if (!level.hasChunkAt(target)) {
            npc.setAction("caravan paused at unloaded destination; never force loads");
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (!(blockEntity instanceof ChestBlockEntity chest)) {
            npc.setAction("caravan paused: assigned depot no longer an actual vanilla chest");
            return true;
        }
        if (npc.blockPosition().distSqr(target) > 6) {
            RoadNavigator.moveToward(npc, level, target, 0.95);
            npc.setAction("caravan travelling on familiar paths to "
                + (target.equals(npc.caravanPickup()) ? "pickup depot" : "destination depot"));
            return true;
        }
        if (target.equals(npc.caravanPickup())) {
            int loaded = transfer(chest, npc.backpack(), CAPACITY_PER_TRIP);
            npc.setAction(loaded > 0 ? "loaded " + loaded + " real wheat for caravan"
                : "waiting for real wheat at authorized source chest");
        } else {
            int unloaded = transfer(npc.backpack(), chest, CAPACITY_PER_TRIP);
            if (unloaded > 0) npc.recordDelivery();
            npc.setAction(unloaded > 0 ? "delivered " + unloaded + " real wheat into destination chest"
                : "destination chest full; real wheat retained in NPC bag");
        }
        return true;
    }

    /** One-way transfer, all-or-nothing on the 1..8 units actually available
     * and placeable; never removes goods from a source before checking target.
     */
    static int transfer(net.minecraft.world.Container from,
                        net.minecraft.world.Container to, int max) {
        if (max <= 0 || max > CAPACITY_PER_TRIP || from == to) return 0;
        SimpleContainer source = snapshot(from), destination = snapshot(to);
        int available = source.countItem(Items.WHEAT);
        int proposed = Math.min(max, available);
        if (proposed == 0) return 0;
        // If the destination cannot accept the full batch, shrink proposal
        // until accepted. A simulated partial insert must not be committed.
        int accepted = 0;
        SimpleContainer safeDestination = null;
        for (int quantity = proposed; quantity >= 1; quantity--) {
            SimpleContainer trial = snapshot(destination);
            if (trial.addItem(new ItemStack(Items.WHEAT, quantity)).isEmpty()) {
                accepted = quantity;
                safeDestination = trial;
                break;
            }
        }
        if (accepted == 0) return 0;
        int left = accepted;
        for (int i = 0; i < source.getContainerSize() && left > 0; i++) {
            ItemStack stack = source.getItem(i);
            if (!stack.is(Items.WHEAT)) continue;
            int taken = Math.min(stack.getCount(), left);
            stack.shrink(taken);
            left -= taken;
        }
        if (left != 0) return 0;
        commit(from, source);
        commit(to, safeDestination);
        return accepted;
    }

    private static SimpleContainer snapshot(net.minecraft.world.Container container) {
        SimpleContainer copy = new SimpleContainer(container.getContainerSize());
        for (int i = 0; i < container.getContainerSize(); i++)
            copy.setItem(i, container.getItem(i).copy());
        return copy;
    }
    private static void commit(net.minecraft.world.Container target, SimpleContainer source) {
        for (int i = 0; i < target.getContainerSize(); i++)
            target.setItem(i, source.getItem(i).copy());
        target.setChanged();
    }
}
