package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamNeighborMappingTest {
    @Test void targetNetherRoofQueriesTrueSourceOverworldFloor() {
        // Source OW BOTTOM=-64, target Nether TOP=128.
        assertTrue(SeamNeighborMapping.outsideTarget(128,128,SeamGraph.EdgeSide.TOP));
        assertFalse(SeamNeighborMapping.outsideTarget(127,128,SeamGraph.EdgeSide.TOP));
        assertEquals(-64,SeamNeighborMapping.sourceY(128,-64,128));
        assertEquals(-63,SeamNeighborMapping.sourceY(129,-64,128));
    }

    @Test void targetOverworldFloorQueriesTrueNetherRoof() {
        // Source Nether TOP=128, target OW BOTTOM=-64.
        assertTrue(SeamNeighborMapping.outsideTarget(-65,-64,SeamGraph.EdgeSide.BOTTOM));
        assertFalse(SeamNeighborMapping.outsideTarget(-64,-64,SeamGraph.EdgeSide.BOTTOM));
        assertEquals(127,SeamNeighborMapping.sourceY(-65,128,-64));
        assertEquals(126,SeamNeighborMapping.sourceY(-66,128,-64));
    }

    @Test void arbitraryDimensionPlaneHeightsDoNotRequireChunkAlignment() {
        // 37 and 209 are legal custom horizontal seam locations.
        assertEquals(Integer.valueOf(37),SeamNeighborMapping.loadedSourceY(
            209,37,209,-10,40,SeamGraph.EdgeSide.TOP));
        assertNull(SeamNeighborMapping.loadedSourceY(
            208,37,209,-10,40,SeamGraph.EdgeSide.TOP));
        assertEquals(Integer.valueOf(38),SeamNeighborMapping.loadedSourceY(
            210,37,209,-10,40,SeamGraph.EdgeSide.TOP));
    }

    @Test void sourceHeightAndArithmeticOverflowRejectUnloadedNeighbor() {
        assertNull(SeamNeighborMapping.loadedSourceY(128,-64,128,-63,320,SeamGraph.EdgeSide.TOP));
        assertNull(SeamNeighborMapping.loadedSourceY(
            Integer.MAX_VALUE,Integer.MIN_VALUE,0,-64,320,SeamGraph.EdgeSide.TOP));
    }

    @Test void continuityAndInverseSymmetryHoldAcrossBothWorlds() {
        for(int source:new int[]{-64,-20,0,37,256})
            for(int target:new int[]{-64,0,37,128,300}) {
                int y=target+5;
                int mapped=SeamNeighborMapping.sourceY(y,source,target);
                assertEquals(source+5,mapped);
                assertEquals(y,SeamNeighborMapping.sourceY(mapped,target,source));
            }
    }
}
