package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Low-frequency local ambush by a genuinely hostile RAIDER NPC on a loaded
 * COURIER. Not an infinite source of goods or instant ranged inventory edit:
 * must approach to melee reach, possess capacity, and move real cargo stacks.
 * Theft creates first-hand criminal evidence for the victim and nearby
 * witnesses; village guards already target hostile factions.
 */
public final class BanditActions {
    private BanditActions() {}

    public static boolean tick(PlayerNpcEntity raider, ServerLevel level, long tick) {
        if (!level.getServer().isSameThread()
            || !raider.isAlive()
            || raider.mode() != PlayerNpcEntity.Mode.LIVE
            || raider.identity().faction() != SocietyRules.Faction.RAIDERS
            || raider.energy() < 25 || tick < raider.nextRaidTick()
            || raider.isAfraid(tick)) return false;

        List<PlayerNpcEntity> victims = level.getEntitiesOfClass(
            PlayerNpcEntity.class, raider.getBoundingBox().inflate(12.0),
            other -> other != raider && other.isAlive()
                && other.profession() == PlayerNpcEntity.Profession.COURIER
                && raider.hostileTo(other)
                && other.backpack().countItem(Items.WHEAT) >= 1
                && DailyRoaming.inRange(raider, other.blockPosition())
                && level.hasChunkAt(other.blockPosition()));
        if (victims.isEmpty()) return false;
        PlayerNpcEntity courier = victims.stream()
            .min(Comparator.comparingDouble(raider::distanceToSqr)).orElseThrow();
        if (raider.distanceToSqr(courier) > 4.0) {
            if (RoadNavigator.moveToward(raider, level, courier.blockPosition(), 1.02)) {
                raider.setAction("tracking a real caravan with wheat cargo");
                return true;
            }
            return false;
        }
        // No theft through closed walls or from an unreachable height.
        if (!raider.hasLineOfSight(courier)
            || Math.abs(raider.getY() - courier.getY()) > 1.5) return false;

        int available = courier.backpack().countItem(Items.WHEAT);
        int offer = Math.min(2, available);
        ItemStack goods = new ItemStack(Items.WHEAT, offer);
        if (!raider.backpack().canAddItem(goods)) return false;

        // The original courier bag is the single source of truth.
        // Insert only if the entire amount fits; rollback is not needed as
        // no world mutation occurs until the receiver accepts it.
        ItemStack rejected = raider.backpack().addItem(goods.copy());
        if (!rejected.isEmpty()) return false;
        int remaining = offer;
        for (int i = 0; i < courier.backpack().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = courier.backpack().getItem(i);
            if (!stack.is(Items.WHEAT)) continue;
            int taken = Math.min(stack.getCount(), remaining);
            stack.shrink(taken);
            remaining -= taken;
        }
        if (remaining != 0) {
            // Defensive invariant: candidate inventory unexpectedly changed.
            // This is server-thread-only; should be impossible, but don't
            // silently create items if another mod mutates the bag mid-action.
            raider.backpack().removeItemType(Items.WHEAT, offer);
            return false;
        }
        courier.backpack().setChanged();
        raider.backpack().setChanged();
        raider.postponeRaid(tick + 400L);
        courier.witnessedBanditTheft(raider, tick);
        UUID incident = UUID.randomUUID();
        courier.civic().witness(incident, raider.getUUID(), courier.getUUID(),
            CivicMind.Kind.THEFT, tick);
        // Other citizens can KNOW a direct crime only when physically nearby.
        for (PlayerNpcEntity witness : level.getEntitiesOfClass(
                PlayerNpcEntity.class, courier.getBoundingBox().inflate(12.0),
                other -> other != courier && other != raider && other.isAlive()
                    && !other.hostileTo(courier) && other.hasLineOfSight(raider))) {
            witness.civic().witness(incident, raider.getUUID(), witness.getUUID(),
                CivicMind.Kind.THEFT, tick);
            witness.memory().witness(tick, raider.getUUID(),
                ResidentMemory.Kind.THEFT_WITNESSED, courier.blockPosition().toShortString());
        }
        courier.setAction("robbed of " + offer + " actual wheat by hostile raider; afraid");
        raider.setAction("stole " + offer + " real wheat from caravan; witnesses remember");
        return true;
    }
}
