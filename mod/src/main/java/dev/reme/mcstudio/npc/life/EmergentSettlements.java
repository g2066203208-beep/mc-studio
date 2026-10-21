package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Camps/hamlets arise from real nearby residents with independent homes and
 * shared affiliation. Never manufactures citizens or buildings from a counter.
 * Loaded-local survey only; a 3-person community is not yet a built town.
 */
public final class EmergentSettlements {
    private EmergentSettlements() {}

    public record Settlement(UUID founder, BlockPos hub, int households,
                             String culture, String tier) {}

    public static Settlement survey(ServerLevel level, PlayerNpcEntity observer) {
        if (!observer.isAlive()) return null;
        var identity = observer.identity();
        BlockPos anchor = observer.homePos();
        List<PlayerNpcEntity> nearby = level.getEntitiesOfClass(
            PlayerNpcEntity.class, new AABB(anchor).inflate(40),
            other -> other.isAlive() && other.mode() == PlayerNpcEntity.Mode.LIVE
                && other.identity().nation() == identity.nation()
                && other.identity().faction() == identity.faction()
                && other.identity().region().equals(identity.region())
                && MovementPolicy.within(anchor.getX(), anchor.getZ(),
                    other.homePos().getX(), other.homePos().getZ(), 32));
        if (nearby.size() < 3) return null;
        nearby.sort(Comparator.comparing(PlayerNpcEntity::getUUID));
        Set<BlockPos> homes = new HashSet<>();
        int x = 0, y = 0, z = 0;
        for (PlayerNpcEntity resident : nearby) {
            if (homes.add(resident.homePos())) {
                x += resident.homePos().getX();
                y += resident.homePos().getY();
                z += resident.homePos().getZ();
            }
        }
        if (homes.size() < 3) return null;
        BlockPos hub = new BlockPos(Math.floorDiv(x, homes.size()),
            Math.floorDiv(y, homes.size()), Math.floorDiv(z, homes.size()));
        String tier = identity.faction() == SocietyRules.Faction.RAIDERS
            ? "band" : homes.size() >= 12 ? "town"
                : homes.size() >= 6 ? "village" : "hamlet";
        return new Settlement(nearby.get(0).getUUID(), hub,
            homes.size(), identity.nation() + "/" + identity.faction(), tier);
    }

    /** Autonomous founding chronicle update, independent of chat commands or
     * profession: farmers, bakers, couriers and guards are households too.
     * Phased at 240 ticks per resident, local loaded-only census.
     */
    public static void observe(PlayerNpcEntity resident, ServerLevel level, long tick) {
        if (resident.mode() != PlayerNpcEntity.Mode.LIVE
            || !MovementPolicy.due(tick, resident.getId(), 240)) return;
        Settlement group = survey(level, resident);
        if (group == null) return;
        SettlementLedger.get(level).record(group.founder(), group.hub(),
            group.households(), tick, group.culture());
    }

    public static boolean gather(PlayerNpcEntity resident, ServerLevel level, long tick) {
        if (resident.mode() != PlayerNpcEntity.Mode.LIVE
            || resident.intent() != LivingPlanner.Intent.SOCIALIZE
            || resident.energy() < 40
            || !MovementPolicy.due(tick, resident.getId(), 240))
            return false;
        Settlement group = survey(level, resident);
        if (group == null) return false;
        SettlementLedger.get(level).record(group.founder(), group.hub(),
            group.households(), tick, group.culture());
        BlockPos hub = group.hub();
        // A census cannot force a route into a wall, closed house, cave or an
        // unloaded chunk. The hub is a destination only if walkable.
        if (!level.hasChunkAt(hub) || !level.getBlockState(hub).isAir()
            || !level.getBlockState(hub.above()).isAir()
            || !level.getBlockState(hub.below()).isSolid()
            || !DailyRoaming.inRange(resident, hub)) return false;
        if (resident.blockPosition().distSqr(hub) > 9) {
            if (!RoadNavigator.moveToward(resident, level, hub, 0.84)) return false;
            resident.setAction("travelling to organic " + group.tier()
                + " gathering: " + group.households() + " homes");
        } else {
            resident.setAction("meeting neighbours in organic " + group.tier()
                + " (" + group.households() + " households)");
        }
        return true;
    }
}
