package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Contextual guard, scout, courier and escort actions. */
public final class SocietyActions {
    private SocietyActions() {}

    public static boolean tick(PlayerNpcEntity npc, ServerLevel level, long tick) {
        if (npc.mode() != PlayerNpcEntity.Mode.LIVE) return false;
        if (tick % 40 != Math.floorMod(npc.getId(), 40)) return false;
        return switch (npc.profession()) {
            case GUARD -> guard(npc, level, tick);
            case SCOUT -> scout(npc, level, tick);
            case COURIER -> SupplyLogistics.deposit(npc, level);
            default -> false;
        };
    }

    private static boolean guard(PlayerNpcEntity guard, ServerLevel level, long tick) {
        List<PlayerNpcEntity> enemies = level.getEntitiesOfClass(PlayerNpcEntity.class,
            guard.getBoundingBox().inflate(12),
            target -> target != guard && target.isAlive() &&
                guard.hostileTo(target) && guard.hasLineOfSight(target));
        LivingEntity threat = enemies.stream()
            .min(Comparator.comparingDouble(guard::distanceToSqr)).orElse(null);

        if (threat == null) {
            List<Monster> monsters = level.getEntitiesOfClass(Monster.class,
                guard.getBoundingBox().inflate(12),
                target -> target.isAlive() && guard.hasLineOfSight(target));
            threat = monsters.stream()
                .min(Comparator.comparingDouble(guard::distanceToSqr)).orElse(null);
        }

        if (threat != null) {
            guard.setTarget(threat);
            guard.setAction("defending settlement from " + threat.getName().getString());
            // Count only observable engagement initiation, not hypothetical patrols.
            guard.journey().succeeded(LifeJourney.Achievement.DEFEND);
            return true;
        }
        guard.setTarget(null);
        var leader = guard.escortLeader() == null
            ? null : level.getPlayerByUUID(guard.escortLeader());
        if (leader != null && leader.isAlive()) {
            if (guard.distanceToSqr(leader) > 16) {
                RoadNavigator.moveToward(guard, level, leader.blockPosition(), 1.08);
                guard.setAction("escorting player " + leader.getName().getString());
            }
            return true;
        }
        if (guard.blockPosition().distSqr(guard.workPos()) > 36) {
            RoadNavigator.moveToward(guard, level, guard.workPos(), 0.9);
            guard.setAction("patrolling settlement");
            return true;
        }
        return false;
    }

    private static boolean scout(PlayerNpcEntity scout, ServerLevel level, long tick) {
        // Find existing dropped items; no synthetic loot and no acquisition
        // from unloaded chunks. Ownership is not overridden.
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
            scout.getBoundingBox().inflate(2.0), item -> item.isAlive() &&
                item.getOwner() == null);
        for (ItemEntity item : drops) {
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) continue;
            ItemStack leftover = scout.backpack().addItem(stack.copy());
            if (leftover.getCount() == stack.getCount()) continue;
            if (leftover.isEmpty()) item.discard();
            else item.setItem(leftover);
            scout.setAction("collected an existing item from ground");
            return true;
        }

        int cx = scout.blockPosition().getX() >> 4;
        int cz = scout.blockPosition().getZ() >> 4;
        if (scout.visitChunk(cx, cz)) {
            scout.setAction("discovered new loaded region/chunk");
        }
        BlockPos destination = scout.exploreTarget();
        if (destination == null || scout.blockPosition().distSqr(destination) < 9
            || !level.hasChunkAt(destination)) {
            BlockPos candidate = scout.blockPosition().offset(
                scout.getRandom().nextInt(25) - 12, 0,
                scout.getRandom().nextInt(25) - 12);
            if (!level.hasChunkAt(candidate)) return true;
            scout.setExploreTarget(candidate);
            destination = candidate;
        }
        RoadNavigator.moveToward(scout, level, destination, 0.90);
        return true;
    }
}
