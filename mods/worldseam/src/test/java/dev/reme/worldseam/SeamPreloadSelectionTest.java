package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SeamPreloadSelectionTest {
    @Test void doesNotPreloadEitherAtNetherMiddle() {
        assertNull(SeamPreloadSelection.choose(List.of(
            new SeamPreloadSelection.Candidate("nether->overworld",70),
            new SeamPreloadSelection.Candidate("nether->end",70)),null));
    }
    @Test void hoveringAtEqualDistanceNeverOscillatesBetweenWorlds() {
        var up="nether->overworld";
        var down="nether->end";
        var first=SeamPreloadSelection.choose(List.of(
            new SeamPreloadSelection.Candidate(up,62),
            new SeamPreloadSelection.Candidate(down,63)),null);
        assertEquals(up,first.scope());
        for(int i=0;i<30;i++) {
            first=SeamPreloadSelection.choose(List.of(
                new SeamPreloadSelection.Candidate(up,i%2==0?65:63),
                new SeamPreloadSelection.Candidate(down,i%2==0?63:65)),first.scope());
            assertEquals(up,first.scope());
        }
    }
    @Test void switchOnlyWhenNewBoundaryDecisivelyCloser() {
        var first=SeamPreloadSelection.choose(List.of(
            new SeamPreloadSelection.Candidate("old",74),
            new SeamPreloadSelection.Candidate("new",58)),"old");
        assertEquals("new",first.scope());
    }
    @Test void oldTicketIsReleasedOutsideExitRadius() {
        assertNull(SeamPreloadSelection.choose(List.of(
            new SeamPreloadSelection.Candidate("old",81)), "old"));
    }
    @Test void withinExitBandKeepsExistingTicket() {
        var c=SeamPreloadSelection.choose(List.of(
            new SeamPreloadSelection.Candidate("old",74)), "old");
        assertEquals("old",c.scope());
    }
    @Test void rejectsInvalidCandidates() {
        assertThrows(IllegalArgumentException.class,()->
            new SeamPreloadSelection.Candidate("test",Double.NaN));
    }
}
