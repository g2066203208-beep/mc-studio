package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamCrossingTest {
    private static SeamGraph.Link floorToRoof() {
        return SeamGraph.from("minecraft:overworld").stream()
            .filter(l -> l.exit() == SeamGraph.EdgeSide.BOTTOM)
            .findFirst().orElseThrow();
    }
    private static SeamGraph.Link roofToFloor() {
        return SeamGraph.from("minecraft:the_nether").get(0);
    }

    @Test void crossFromValidOverworldBottomIntoPhysicalNetherRoof() {
        var d = SeamCrossing.evaluate(floorToRoof(),
            -63.80, -63.40, -0.18, -64, 320, 128);
        assertTrue(d.crossing());
        assertTrue(d.early());
        assertEquals(127.875,d.arrivalY(),1e-5);
    }

    @Test void reverseFromPhysicalNetherRoofToOverworldBottom() {
        var d = SeamCrossing.evaluate(roofToFloor(),
            127.80,127.30,0.25,0,128,-64);
        assertTrue(d.crossing());
        assertTrue(d.early());
        assertEquals(-63.875,d.arrivalY(),1e-5);
    }

    @Test void standingNearBoundaryDoesNotCauseForcedTeleport() {
        var d = SeamCrossing.evaluate(floorToRoof(),
            -63.80,-63.80,0,-64,320,128);
        assertFalse(d.crossing());
        var away = SeamCrossing.evaluate(roofToFloor(),
            127.95,127.95,-0.2,0,128,-64);
        assertFalse(away.crossing());
    }

    @Test void hugeOutOfBoundsCoordinatesHaveOldSafeFallback() {
        var d = SeamCrossing.evaluate(roofToFloor(),
            128.6,128.6,0,0,128,-64);
        assertTrue(d.crossing());
        assertFalse(d.early());
        assertEquals(-63.4,d.arrivalY(),1e-5);
    }

    @Test void neverConfuseNetherPhysical128WithVanillaBuild256() {
        var d = SeamCrossing.evaluate(roofToFloor(),
            127.75,127.2,0.1,0,128,-64);
        assertTrue(d.early());
        var no = SeamCrossing.evaluate(roofToFloor(),
            120,119,0.2,0,128,-64);
        assertFalse(no.crossing());
    }
}
