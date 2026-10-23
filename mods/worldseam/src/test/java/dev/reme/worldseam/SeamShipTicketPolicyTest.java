package dev.reme.worldseam;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SeamShipTicketPolicyTest {
    private static final String OW = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";

    @Test
    void oneShipRetainsTwoDistinctNeighborChartsInsteadOfThrashingTickets() {
        UUID ship = UUID.randomUUID();
        var overworld = new SeamShipTicketPolicy.Key(ship, OW);
        var nether = new SeamShipTicketPolicy.Key(ship, NETHER);
        var active = new HashSet<SeamShipTicketPolicy.Key>();
        active.add(overworld);

        assertTrue(SeamShipTicketPolicy.mayOpen(active, nether));
        active.add(nether);
        assertEquals(2, active.size());
        assertTrue(active.contains(overworld));
        assertTrue(active.contains(nether));
        assertTrue(SeamShipTicketPolicy.mayOpen(active, overworld));
    }

    @Test
    void thirdDestinationForOneShipIsRejectedWithoutEvictingTwoExisting() {
        UUID ship = UUID.randomUUID();
        var active = Set.of(
            new SeamShipTicketPolicy.Key(ship, OW),
            new SeamShipTicketPolicy.Key(ship, NETHER));
        assertFalse(SeamShipTicketPolicy.mayOpen(active,
            new SeamShipTicketPolicy.Key(ship, END)));
    }

    @Test
    void shipLimitCountsUniqueBodiesNotNumberOfDestinationTickets() {
        var active = new HashSet<SeamShipTicketPolicy.Key>();
        UUID first = null;
        for (int i = 0; i < SeamShipTicketPolicy.MAX_SHIPS; i++) {
            UUID ship = new UUID(0L, i + 1L);
            if (first == null) first = ship;
            active.add(new SeamShipTicketPolicy.Key(ship, OW));
        }
        assertFalse(SeamShipTicketPolicy.mayOpen(active,
            new SeamShipTicketPolicy.Key(new UUID(1L, 1L), OW)));
        // Existing ships may still reserve their second destination.
        assertTrue(SeamShipTicketPolicy.mayOpen(active,
            new SeamShipTicketPolicy.Key(first, NETHER)));
    }

    @Test
    void globalTicketCapIsBoundedAndExistingKeysRemainAdmissible() {
        var active = new HashSet<SeamShipTicketPolicy.Key>();
        for (int i = 0; i < SeamShipTicketPolicy.MAX_SHIPS; i++) {
            UUID ship = new UUID(0L, i + 1L);
            active.add(new SeamShipTicketPolicy.Key(ship, OW));
            active.add(new SeamShipTicketPolicy.Key(ship, NETHER));
        }
        assertEquals(SeamShipTicketPolicy.MAX_TICKETS, active.size());
        assertFalse(SeamShipTicketPolicy.mayOpen(active,
            new SeamShipTicketPolicy.Key(new UUID(1L, 1L), END)));
        assertTrue(SeamShipTicketPolicy.mayOpen(active, active.iterator().next()));
    }

    @Test
    void invalidTicketIdentitiesFailClosed() {
        UUID ship = UUID.randomUUID();
        assertThrows(NullPointerException.class,
            () -> new SeamShipTicketPolicy.Key(null, OW));
        assertThrows(NullPointerException.class,
            () -> new SeamShipTicketPolicy.Key(ship, null));
        assertThrows(IllegalArgumentException.class,
            () -> new SeamShipTicketPolicy.Key(ship, " "));
    }
}
