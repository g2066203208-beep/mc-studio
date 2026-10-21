package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamBlastGeometryTest {
    @Test void netherRoofBlastAffectsOnlyTheOverworldFloorCap() {
        var plan=SeamBlastGeometry.map(127.5,128,-64,4);
        assertNotNull(plan);
        assertEquals(-64.5,plan.mappedY());
        assertEquals(0.5,plan.distanceToPlane());
        assertEquals(4f,plan.radius());
    }
    @Test void overworldFloorBlastsMapBackToNetherRoof() {
        var plan=SeamBlastGeometry.map(-63.0,-64,128,4);
        assertNotNull(plan);
        assertEquals(129,plan.mappedY());
        assertEquals(1,plan.distanceToPlane());
    }
    @Test void tooFarAwayDoesNotHitOtherDimension() {
        assertNull(SeamBlastGeometry.map(122,128,-64,4));
        assertNull(SeamBlastGeometry.map(-58,-64,128,4));
    }
    @Test void noZeroNegativeOrNonFiniteBlasts() {
        assertNull(SeamBlastGeometry.map(127,128,-64,0));
        assertNull(SeamBlastGeometry.map(127,128,-64,-5));
        assertNull(SeamBlastGeometry.map(Double.NaN,128,-64,4));
        assertNull(SeamBlastGeometry.map(127,128,-64,Float.POSITIVE_INFINITY));
    }
    @Test void giantModExplosionsHaveLimitedCrossDimensionalBudget() {
        var plan=SeamBlastGeometry.map(125,128,-64,100);
        assertNotNull(plan);
        assertEquals(8f,plan.radius());
        assertNull(SeamBlastGeometry.map(119,128,-64,100));
    }
}
