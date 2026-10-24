package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamRapierAnchorTest {
    @Test void tracksRealHullCenterAndFloorsNegativeChunks() {
        assertEquals(new SeamRapierAnchorMath.Center(-1,1),
            SeamRapierAnchorMath.fromBounds(-2,0,20,22));
        assertEquals(new SeamRapierAnchorMath.Center(2,-2),
            SeamRapierAnchorMath.fromBounds(31,35,-33,-29));
    }

    @Test void refusesStaleSamplesAndCrossServerClockReset() {
        assertTrue(SeamRapierAnchorMath.fresh(100,120));
        assertFalse(SeamRapierAnchorMath.fresh(100,121));
        assertFalse(SeamRapierAnchorMath.fresh(120,119));
        assertFalse(SeamRapierAnchorMath.fresh(Long.MIN_VALUE,1));
    }

    @Test void refusesNonFiniteAndOutOfWorldAnchors() {
        assertNull(SeamRapierAnchorMath.fromBounds(1,1,0,1));
        assertNull(SeamRapierAnchorMath.fromBounds(30_000_000,30_000_002,0,2));
        assertNull(SeamRapierAnchorMath.fromBounds(Double.NEGATIVE_INFINITY,1,0,2));
    }
}
