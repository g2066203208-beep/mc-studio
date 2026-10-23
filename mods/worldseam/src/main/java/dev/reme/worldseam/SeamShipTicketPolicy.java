package dev.reme.worldseam;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure, deterministic admission policy for read-only Sable-adjacent terrain
 * tickets. Tickets are keyed by BOTH ship identity and destination chart.
 * In particular, a ship close to two seams must retain both destinations.
 *
 * This is chunk residency only. It is not native Rapier collision readiness.
 */
final class SeamShipTicketPolicy {
    static final int MAX_SHIPS = 32;
    static final int MAX_DESTINATIONS_PER_SHIP = 2;
    static final int MAX_TICKETS = MAX_SHIPS * MAX_DESTINATIONS_PER_SHIP;

    private SeamShipTicketPolicy() {}

    record Key(UUID ship, String targetDimension) {
        Key {
            Objects.requireNonNull(ship, "ship");
            Objects.requireNonNull(targetDimension, "targetDimension");
            if (targetDimension.isBlank())
                throw new IllegalArgumentException("empty destination chart");
        }
    }

    static boolean mayOpen(Collection<Key> active, Key next) {
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(next, "next");
        if (active.contains(next)) return true;
        if (active.size() >= MAX_TICKETS) return false;

        var ships = new HashSet<UUID>();
        int chartsForShip = 0;
        for (Key key : active) {
            ships.add(key.ship());
            if (key.ship().equals(next.ship())) chartsForShip++;
        }
        // The same body may hold two adjacent chart tickets, but never
        // monopolize all remote dimensions or evict another active ship.
        if (chartsForShip >= MAX_DESTINATIONS_PER_SHIP) return false;
        return chartsForShip > 0 || ships.size() < MAX_SHIPS;
    }
}
