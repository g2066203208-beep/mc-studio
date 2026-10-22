package dev.reme.npcstudio.npc.life;

import dev.reme.npcstudio.npc.PlayerNpcEntity;
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
        if (npc.identity().faction() == SocietyRules.Faction.RAIDERS
            && MovementPolicy.due(tick, npc.getId(), 40))
            return BanditActions.tick(npc, level, tick);
        if (npc.isAfraid(tick) && npc.profession() != PlayerNpcEntity.Profession.GUARD)
            return false; // the threat-response planner now takes precedence

        // Guards need timely checks so short patrol windows and threats are not skipped.
        // Other professions retain the lower-cost 80-tick cadence.
        int interval = npc.profession() == PlayerNpcEntity.Profession.GUARD ? 40 : 80;
        if (!MovementPolicy.due(tick, npc.getId(), interval)) return false;
        // Beyond scripted trade: resident households independently form a
        // hamlet once multiple real NPC homes cluster together. Social visits
        // happen only during actual SOCIALIZE intent, not on every tick.
        if (npc.profession() == PlayerNpcEntity.Profession.RESIDENT
            && EmergentSettlements.gather(npc, level, tick)) return true;
        return switch (npc.profession()) {
            case GUARD -> guard(npc, level, tick);
            case SCOUT -> scout(npc, level, tick);
            case COURIER -> courier(npc, level);
            case RESIDENT -> switch (npc.journey().goal()) {
                case EXPLORE_LANDS -> scout(npc, level, tick);
                case DEFEND_SETTLEMENT -> guard(npc, level, tick);
                default -> npc.character().interestedInExploration()
                    && npc.energy() > 55
                    && Math.floorMod(level.getDayTime(), 24000L) > 7500L
                    && Math.floorMod(level.getDayTime(), 24000L) < 11000L
                    && scout(npc, level, tick);
            };
            default -> false;
        };
    }

    private static boolean courier(PlayerNpcEntity npc, ServerLevel level) {
        // A configured two-depot caravan takes precedence over the legacy
        // one-chest farmer exchange. It moves actual wheat inventory that
        // hostile raiders may steal while the courier is travelling.
        if (CaravanRoutes.configured(npc))
            return CaravanRoutes.tick(npc, level);
        if (npc.supplyPos() == null) return false;
        if (npc.backpack().countItem(net.minecraft.world.item.Items.WHEAT) > 0)
            return SupplyLogistics.deposit(npc, level);
        if (SupplyLogistics.collectFromFarmer(npc, level)) return true;
        return SupplyLogistics.deposit(npc, level);
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
            return true;
        }
        guard.setTarget(null);
        var leader = guard.escortLeader() == null
            ? null : level.getPlayerByUUID(guard.escortLeader());
        if (leader != null && leader.isAlive()) {
            BlockPos formation = squadPosition(leader, guard.formationSlot());
            if (guard.blockPosition().distSqr(formation) > 7) {
                RoadNavigator.moveToward(guard, level, formation, 1.08);
                guard.setAction("escorting " + leader.getName().getString() +
                    " in squad slot " + guard.formationSlot());
            }
            return true;
        }
        if (guard.blockPosition().distSqr(guard.workPos()) > 36) {
            RoadNavigator.moveToward(guard, level, guard.workPos(), 0.9);
            guard.setAction("returning to guard post");
            return true;
        }
        // Respect a live route instead of restarting it on every AI pulse.
        if (!guard.getNavigation().isDone()) return true;

        // No global search: try a cardinal watchpoint, then three alternatives.
        // If terrain or another mod blocks the 8-block target, fall back to a
        // nearer walkable point, never force-load a chunk or walk beyond home.
        if (guard.energy() >= 28 && guard.hunger() >= 24) {
            for (int quarter = 0; quarter < 4; quarter++) {
                var step = PatrolPlanner.choose(tick + quarter * 260L, guard.getId());
                for (int radius : new int[]{8, 5, 3}) {
                    BlockPos destination = guard.workPos().offset(
                        Integer.signum(step.dx()) * radius, 0,
                        Integer.signum(step.dz()) * radius);
                    if (!DailyRoaming.inRange(guard, destination)
                        || !level.hasChunkAt(destination)
                        || !level.getBlockState(destination.below()).isSolid()
                        || !level.getBlockState(destination).isAir()
                        || !level.getBlockState(destination.above()).isAir()
                        || guard.blockPosition().distSqr(destination) <= 4) continue;
                    if (RoadNavigator.moveToward(guard, level, destination, 0.86)) {
                        guard.setAction("walking a watch round around the guard post");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BlockPos squadPosition(Player leader, int slot) {
        double angle = Math.toRadians(leader.getYRot());
        int row = slot / 3 + 1;
        int column = slot % 3 - 1;
        int dx = (int) Math.round(Math.sin(angle) * (row * 2.0) +
            Math.cos(angle) * (column * 2.0));
        int dz = (int) Math.round(-Math.cos(angle) * (row * 2.0) +
            Math.sin(angle) * (column * 2.0));
        return leader.blockPosition().offset(dx, 0, dz);
    }

    private static boolean scout(PlayerNpcEntity scout, ServerLevel level, long tick) {
        Player leader = scout.escortLeader() == null
            ? null : level.getPlayerByUUID(scout.escortLeader());
        if (leader != null && leader.isAlive()) {
            BlockPos target = squadPosition(leader, scout.formationSlot());
            if (scout.blockPosition().distSqr(target) > 8)
                RoadNavigator.moveToward(scout, level, target, 1.05);
            scout.setAction("scouting alongside expedition leader");
            return true;
        }
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
            if (!level.hasChunkAt(candidate) ||
                !DailyRoaming.inRange(scout, candidate)) {
                scout.setExploreTarget(scout.homePos());
                return true;
            }
            scout.setExploreTarget(candidate);
            destination = candidate;
        }
        RoadNavigator.moveToward(scout, level, destination, 0.90);
        return true;
    }
}
