package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Autonomous, real-inventory neighborhood aid. A hungry resident may receive
 * ONE existing meal from a nearby allied resident with a real surplus.
 *
 * The transfer never scans containers, touches player items, force-loads
 * chunks, creates food or interrupts high-priority guard/mining/caravan motion.
 * Two 36-slot inventory snapshots must both pass before committing.
 */
public final class MutualAid {
    private MutualAid() {}

    private static final Item[] MEALS = {
        Items.BREAD, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.COOKED_PORKCHOP,
        Items.APPLE, Items.CARROT, Items.POTATO, Items.BEETROOT
    };
    private static final long COOLDOWN = 600L;

    public static boolean tick(PlayerNpcEntity hungry, ServerLevel level, long tick) {
        if (!level.getServer().isSameThread()
            || !hungry.isAlive()
            || hungry.mode() != PlayerNpcEntity.Mode.LIVE
            || hungry.identity().faction() == SocietyRules.Faction.RAIDERS
            || hungry.isDetained(tick)
            || hungry.isAfraid(tick)
            || hungry.hunger() > 40
            || hasMeal(hungry.backpack())
            || !MovementPolicy.due(tick, hungry.getId(), 80))
            return false;

        // The existing bounded NBT-persisted eyewitness journal also carries
        // a per-recipient aid cooldown across world saves; no duplicate shadow
        // world ledger or tick-global residents list is necessary.
        for (ResidentMemory.Event event : hungry.memory().recentEvents()) {
            long age = tick - event.worldTick();
            if (event.kind() == ResidentMemory.Kind.HELPED
                && age >= 0 && age < COOLDOWN) return false;
        }

        var donors = level.getEntitiesOfClass(PlayerNpcEntity.class,
            hungry.getBoundingBox().inflate(3.0),
            donor -> donor != hungry && donor.isAlive()
                && donor.mode() == PlayerNpcEntity.Mode.LIVE
                && !donor.isDetained(tick) && !donor.isAfraid(tick)
                && donor.hunger() >= 65
                && donor.profession() != PlayerNpcEntity.Profession.SHOPKEEPER
                && donor.profession() != PlayerNpcEntity.Profession.COURIER
                && donor.profession() != PlayerNpcEntity.Profession.BUILDER
                && !donor.hostileTo(hungry) && !hungry.hostileTo(donor)
                && donor.identity().nation() == hungry.identity().nation()
                && donor.identity().faction() == hungry.identity().faction()
                && donor.identity().region().equals(hungry.identity().region())
                && donor.distanceToSqr(hungry) <= 9.0
                && donor.hasLineOfSight(hungry)
                && hungry.hasLineOfSight(donor));
        donors.sort(Comparator.comparingDouble(hungry::distanceToSqr));
        for (PlayerNpcEntity donor : donors) {
            for (Item kind : MEALS) {
                // Two complete units remain for the donor's own survival.
                if (donor.backpack().countItem(kind) < 3) continue;
                SimpleContainer source = copy(donor.backpack());
                SimpleContainer destination = copy(hungry.backpack());
                if (!destination.addItem(new ItemStack(kind, 1)).isEmpty()) continue;
                if (!withdrawOne(source, kind)) continue;
                // Both inventories are mutable on the server thread only.
                // No one-sided changes in the failure paths above.
                commit(donor.backpack(), source);
                commit(hungry.backpack(), destination);
                hungry.memory().witness(tick, donor.getUUID(),
                    ResidentMemory.Kind.HELPED, hungry.blockPosition().toShortString());
                donor.memory().witness(tick, hungry.getUUID(),
                    ResidentMemory.Kind.GIFT, donor.blockPosition().toShortString());
                hungry.setAction("received one real emergency meal from "
                    + donor.getName().getString());
                donor.setAction("shared one stocked meal with hungry neighbor "
                    + hungry.getName().getString());
                return true;
            }
        }
        return false;
    }

    private static boolean hasMeal(SimpleContainer bag) {
        for (Item kind : MEALS) if (bag.countItem(kind) > 0) return true;
        return false;
    }

    private static boolean withdrawOne(SimpleContainer bag, Item wanted) {
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack item = bag.getItem(i);
            if (item.is(wanted) && !item.isEmpty()) {
                item.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static SimpleContainer copy(SimpleContainer source) {
        SimpleContainer result = new SimpleContainer(source.getContainerSize());
        for (int i = 0; i < source.getContainerSize(); i++)
            result.setItem(i, source.getItem(i).copy());
        return result;
    }

    private static void commit(SimpleContainer target, SimpleContainer source) {
        for (int i = 0; i < target.getContainerSize(); i++)
            target.setItem(i, source.getItem(i).copy());
        target.setChanged();
    }
}
