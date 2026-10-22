package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * A conservative, player-like loot-collection step following a proven
 * vanilla FakePlayer mining action.
 *
 * Only pick up explicitly claimed ItemEntity UUIDs produced during that
 * particular break; never vacuum unrelated player drops, never create items,
 * never pull unloaded drops from the world and never remove excess stacks.
 */
public final class PlayerLootPickup {
    private PlayerLootPickup() {}

    /** True if this task owns the current resident action slot. */
    public static boolean tick(PlayerNpcEntity npc, ServerLevel level) {
        Set<UUID> claims = npc.pendingMiningDrops();
        if (claims.isEmpty() || npc.mode() != PlayerNpcEntity.Mode.LIVE)
            return false;
        if (!level.getServer().isSameThread() || !npc.isAlive()) return false;
        if (npc.isAfraid(level.getGameTime()) ||
            npc.intent() == LivingPlanner.Intent.ESCAPE) {
            npc.getNavigation().stop();
            return false;
        }
        if (level.getGameTime() > npc.miningLootExpiry()) {
            npc.clearMiningDrops();
            npc.setAction("gave up collecting expired ore drops without inventing items");
            return false;
        }
        boolean encountered = false;
        for (UUID id : new ArrayList<>(claims)) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof ItemEntity item) || !item.isAlive()
                || item.getItem().isEmpty()) {
                // Drops emitted during an entity tick may not enter the
                // persistent entity index until a subsequent tick.
                // Never delete a legitimate claim immediately after spawn.
                if (level.getGameTime() - npc.miningLootStarted() > 12L)
                    npc.forgetMiningDrop(id);
                continue;
            }
            if (!level.hasChunkAt(item.blockPosition())) {
                // Loaded-only behavior. Do not force-load or remove the item.
                continue;
            }
            encountered = true;
            if (npc.distanceToSqr(item) > 2.25) {
                // Stay near the NPC's assigned home territory and don't walk
                // across the map to chase an item carried away by water.
                if (!DailyRoaming.inRange(npc, item.blockPosition())) {
                    npc.forgetMiningDrop(id);
                    continue;
                }
                // RoadNavigator intentionally stops when block distance <5,
                // but ItemEntity pickup needs <=1.5 blocks in actual entity
                // geometry. Finish the last meters with vanilla navigation
                // toward the item entity's precise position.
                if (npc.distanceToSqr(item) <= 16.0
                    && npc.onGround() && npc.hasLineOfSight(item)) {
                    // The final 1.5-4 metres are LOS-clear: use vanilla
                    // MoveControl with precise DOUBLE coordinates and normal
                    // colliding Mob physics. Long-range pathfinder waypoints
                    // can quantize at extreme GameTest world coordinates
                    // (even beyond 16,777,216 where floats lose one block).
                    // This is never a teleport and never crosses a wall.
                    npc.getNavigation().stop();
                    npc.getMoveControl().setWantedPosition(
                        item.getX(), npc.getY(), item.getZ(), 0.88);
                } else if (npc.distanceToSqr(item) <= 16.0) {
                    if (npc.getNavigation().isDone()
                        || npc.canRepath(item.blockPosition(), level.getGameTime()))
                        npc.getNavigation().moveTo(item.getX(), item.getY(),
                            item.getZ(), 0.98);
                } else {
                    RoadNavigator.moveToward(npc, level, item.blockPosition(), 0.95);
                }
                npc.setAction("walking into actual pickup range of mined item");
                return true;
            }
            if (item.hasPickUpDelay()) {
                npc.setAction("waiting for real vanilla item pickup delay");
                return true;
            }
            if (pickUp(npc, item)) {
                npc.setAction("picked up real mined items into the 36-slot backpack");
                return true;
            }
            // Inventory full: do not delete or change another entity's stack.
            npc.setAction("backpack full: mined items remain physically on ground");
            return false;
        }
        if (npc.pendingMiningDrops().isEmpty()) {
            npc.clearMiningDrops();
            return false;
        }
        // A still-pending UUID may be waiting in the entity manager insertion
        // queue; retain the task until it becomes visible or the grace ends.
        return encountered || (!claims.isEmpty() &&
            level.getGameTime() - npc.miningLootStarted() <= 12L);
    }

    /**
     * Exactly one authoritative item-entity to inventory transfer.
     * Partial insertion is legal: the remainder remains a real world item.
     * @return whether at least one actual item was moved
     */
    public static boolean pickUp(PlayerNpcEntity npc, ItemEntity item) {
        if (!npc.isAlive() || !item.isAlive()
            || npc.level() != item.level()
            || npc.level().isClientSide
            || npc.distanceToSqr(item) > 2.25
            || item.hasPickUpDelay()
            || item.getItem().isEmpty()) return false;

        // Other item entities (including unrelated items of the same kind)
        // must not be taken. Even if a claimed entity merges with a player's
        // existing stack, only its actual original drop quantity is ours.
        int remainingClaim = npc.remainingMiningDrop(item.getUUID());
        if (remainingClaim <= 0) return false;
        SimpleContainer bag = npc.backpack();
        ItemStack source = item.getItem();
        int offer = Math.min(source.getCount(), remainingClaim);
        ItemStack refused = bag.addItem(source.copyWithCount(offer));
        int moved = offer - refused.getCount();
        if (moved <= 0) return false;
        bag.setChanged();
        int groundRemainder = source.getCount() - moved;
        if (groundRemainder <= 0) item.discard();
        else item.setItem(source.copyWithCount(groundRemainder));
        npc.recordMinedPickup(item.getUUID(), moved);
        return true;
    }
}
