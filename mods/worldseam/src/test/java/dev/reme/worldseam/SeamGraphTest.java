package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeamGraphTest {
    @Test void everySeamIsBidirectionalAndThereAreNoLoops() {
        assertTrue(SeamGraph.mutuallyConnected());
        assertEquals(4, SeamGraph.LINKS.size());
        assertEquals(1, SeamGraph.from("minecraft:the_end").size());
        assertEquals(2, SeamGraph.from("minecraft:overworld").size());
        assertEquals(1, SeamGraph.from("minecraft:the_nether").size());
    }

    @Test void yMappingPreservesOvershootAndRoundTrips() {
        for (SeamGraph.Link link : SeamGraph.LINKS) {
            SeamGraph.Link reverse = SeamGraph.LINKS.stream()
                .filter(x -> x.source().equals(link.target()) && x.target().equals(link.source()))
                .findFirst().orElseThrow();
            double sourceBoundary = link.exit() == SeamGraph.EdgeSide.TOP ? 320 : -64;
            double targetBoundary = link.entry() == SeamGraph.EdgeSide.TOP ? 256 : 0;
            for (double offset : List.of(-13.75, -0.5, 0.5, 12.25)) {
                double y = sourceBoundary + offset;
                assertEquals(y, reverse.targetY(
                    link.targetY(y, sourceBoundary, targetBoundary),
                    targetBoundary, sourceBoundary), 0.000001);
            }
        }
    }

    @Test void endpointBoundariesMapTheNetherToOverworldWithoutUnusedRoofVoid() {
        var netherRoof = SeamGraph.from("minecraft:the_nether").get(0);
        var overworldFloor = SeamGraph.from("minecraft:overworld").stream()
            .filter(l -> l.exit() == SeamGraph.EdgeSide.BOTTOM)
            .findFirst().orElseThrow();
        int netherY = SeamBounds.top("minecraft:the_nether", 256);
        assertEquals(128, netherY);
        assertTrue(netherRoof.crossed(128.5, 0, netherY));
        assertEquals(-63.5,
            netherRoof.targetY(128.5, netherY, -64), 0.0001);
        assertEquals(128.5,
            overworldFloor.targetY(-63.5, -64, netherY), 0.0001);
        assertTrue(overworldFloor.near(-50,-64,320,32));
        // The real Nether build max 256 is NOT this connection plane.
        assertFalse(netherRoof.near(256,0,128,32));
    }

    @Test void crossingRequiresActualBoundaryOvershoot() {
        var up = SeamGraph.LINKS.get(1);
        assertFalse(up.crossed(319.99, -64, 320));
        assertTrue(up.crossed(320.5, -64, 320));
        var down = SeamGraph.LINKS.get(2);
        assertFalse(down.crossed(-64.2, -64, 320));
        assertTrue(down.crossed(-64.5, -64, 320));
    }

    @Test void rejectsWrongEdgePairs() {
        assertThrows(IllegalArgumentException.class, () ->
            new SeamGraph.Link("minecraft:a", SeamGraph.EdgeSide.TOP,
                "minecraft:b", SeamGraph.EdgeSide.TOP));
        assertThrows(IllegalArgumentException.class, () ->
            new SeamGraph.Link("minecraft:a", SeamGraph.EdgeSide.BOTTOM,
                "minecraft:a", SeamGraph.EdgeSide.TOP));
    }
}
