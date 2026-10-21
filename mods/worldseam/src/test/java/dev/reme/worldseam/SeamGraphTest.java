package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeamGraphTest {
    @Test void everySeamIsBidirectionalAndThereAreNoLoops() {
        assertTrue(SeamGraph.mutuallyConnected());
        assertEquals(4, SeamGraph.LINKS.size());
        assertEquals(1, SeamGraph.from("minecraft:the_end").size());
        assertEquals(1, SeamGraph.from("minecraft:overworld").size());
        assertEquals(2, SeamGraph.from("minecraft:the_nether").size());
        assertTrue(SeamGraph.from("minecraft:the_end").stream()
            .anyMatch(l -> l.target().equals("minecraft:the_nether")));
        assertTrue(SeamGraph.from("minecraft:the_nether").stream()
            .anyMatch(l -> l.target().equals("minecraft:the_end")));
    }

    @Test void yMappingPreservesOvershootAndRoundTrips() {
        for (SeamGraph.Link link : SeamGraph.LINKS) {
            SeamGraph.Link reverse = SeamGraph.LINKS.stream()
                .filter(x -> x.source().equals(link.target()) && x.target().equals(link.source()))
                .findFirst().orElseThrow();
            double sourceBoundary = endpoint(link.source(),link.exit());
            double targetBoundary = endpoint(link.target(),link.entry());
            for (double offset : List.of(-13.75, -0.5, 0.5, 12.25)) {
                double y = sourceBoundary + offset;
                assertEquals(y, reverse.targetY(
                    link.targetY(y, sourceBoundary, targetBoundary),
                    targetBoundary, sourceBoundary), 0.000001);
            }
        }
    }

    @Test void endpointBoundariesMapTheNetherToOverworldWithoutUnusedRoofVoid() {
        var netherRoof = SeamGraph.from("minecraft:the_nether").stream()
            .filter(l -> l.exit()==SeamGraph.EdgeSide.TOP)
            .findFirst().orElseThrow();
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
        var up=SeamGraph.from("minecraft:the_nether").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.TOP).findFirst().orElseThrow();
        assertFalse(up.crossed(127.99, 0, 128));
        assertTrue(up.crossed(128.5, 0, 128));
        var down=SeamGraph.from("minecraft:the_nether").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.BOTTOM).findFirst().orElseThrow();
        assertFalse(down.crossed(-0.2,0,128));
        assertTrue(down.crossed(-0.5,0,128));
    }

    private static double endpoint(String dim,SeamGraph.EdgeSide side) {
        return switch(dim) {
            case "minecraft:overworld" ->
                side==SeamGraph.EdgeSide.TOP ? 320:-64;
            case "minecraft:the_nether" ->
                side==SeamGraph.EdgeSide.TOP ? 128:0;
            case "minecraft:the_end" ->
                side==SeamGraph.EdgeSide.TOP ? 256:0;
            default -> throw new IllegalArgumentException(dim);
        };
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
