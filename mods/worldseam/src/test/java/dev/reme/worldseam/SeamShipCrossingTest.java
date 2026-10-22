package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamShipCrossingTest {
    @Test void fallingPhysicsShipCrossesOverworldFloorToNetherRoofOneToOne() {
        var out=SeamShipCrossing.crossing(SeamGraph.EdgeSide.BOTTOM,
            -63.4,-64.2,-0.8,-64,128);
        assertNotNull(out);
        assertEquals(127.8,out.destinationY(),1.0e-9);
        assertEquals(-0.8,out.velocityY(),1.0e-12);
    }

    @Test void flyingPhysicsShipCrossesNetherRoofToOverworldFloor() {
        var out=SeamShipCrossing.crossing(SeamGraph.EdgeSide.TOP,
            127.5,128.2,0.7,128,-64);
        assertNotNull(out);
        assertEquals(-63.8,out.destinationY(),1.0e-9);
    }

    @Test void shipNeverSpontaneouslyTeleportsWhenStoppedOrMovingAway() {
        assertNull(SeamShipCrossing.crossing(SeamGraph.EdgeSide.BOTTOM,
            -64.4,-64.4,0,-64,128));
        assertNull(SeamShipCrossing.crossing(SeamGraph.EdgeSide.BOTTOM,
            -63.9,-64.1,0.2,-64,128));
        assertNull(SeamShipCrossing.crossing(SeamGraph.EdgeSide.TOP,
            128.1,127.9,-0.2,128,-64));
        assertNull(SeamShipCrossing.crossing(SeamGraph.EdgeSide.TOP,
            127.9,128.1,-0.1,128,-64));
    }

    @Test void physicsPositionCorrectionCannotCountAsSeamCrossing() {
        assertNull(SeamShipCrossing.crossing(SeamGraph.EdgeSide.BOTTOM,
            -63,-150,-87,-64,128));
        assertNull(SeamShipCrossing.crossing(SeamGraph.EdgeSide.TOP,
            Double.NaN,128.1,0.1,128,-64));
        assertNull(SeamShipCrossing.crossing(SeamGraph.EdgeSide.BOTTOM,
            -63.9,-64.1,Double.POSITIVE_INFINITY,-64,128));
    }

    @Test void customModdedDimensionBoundsKeepExactVelocityAndCoordinates() {
        var result=SeamShipCrossing.crossing(SeamGraph.EdgeSide.BOTTOM,
            31.1,30.9,-0.2,31,204);
        assertNotNull(result);
        assertEquals(203.9,result.destinationY(),1e-9);
        assertEquals(-0.2,result.velocityY(),1e-9);
    }

    @Test void targetFootprintMustAlreadyBeResidentAndFitHeight() {
        var bounds=new SeamShipCrossing.Bounds(-16,80,-16,16,84,16);
        assertTrue(SeamShipCrossing.loadedFootprint(bounds,-64,320,(cx,cz)->true));
        assertEquals(false,SeamShipCrossing.loadedFootprint(bounds,-64,320,(cx,cz)->cx!=0));
        assertFalse(SeamShipCrossing.loadedFootprint(bounds,81,320,(cx,cz)->true));
        assertFalse(SeamShipCrossing.loadedFootprint(bounds,-64,83,(cx,cz)->true));
    }

    @Test void chunkBoundsUseNegativeFloorAndExclusiveMax() {
        var bounds=new SeamShipCrossing.Bounds(-16,80,-0.1,0,84,16);
        var visited=new java.util.HashSet<String>();
        assertTrue(SeamShipCrossing.loadedFootprint(bounds,-64,320,(cx,cz)->{
            visited.add(cx+","+cz);
            return true;
        }));
        assertEquals(java.util.Set.of("-1,-1","-1,0"),visited);
    }

    @Test void oversizeShipRequiresFutureAsynchronousWholeFootprintTicketing() {
        var bounds=new SeamShipCrossing.Bounds(0,80,0,300,84,300);
        assertFalse(SeamShipCrossing.loadedFootprint(bounds,-64,320,(x,z)->true));
    }

    @Test void destinationShiftDoesNotChangeShipWidthOrOrientation() {
        var src=new SeamShipCrossing.Bounds(-12,-70,-4,29,-60,18);
        var mapped=src.shiftedY(192);
        assertEquals(-12,mapped.minX());
        assertEquals(29,mapped.maxX());
        assertEquals(122,mapped.minY());
        assertEquals(132,mapped.maxY());
        assertEquals(18,mapped.maxZ());
    }
}
