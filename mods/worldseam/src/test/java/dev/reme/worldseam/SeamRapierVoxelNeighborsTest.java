package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamRapierVoxelNeighborsTest {
    @Test void allThreeSupportedAxesAreInterior() {
        assertEquals(SeamRapierVoxelNeighbors.Kind.INTERIOR,
            SeamRapierVoxelNeighbors.classify(true,true,false,
                true,true,true,true,true,true));
    }

    @Test void onlyOnePairedAxisIsEdge() {
        assertEquals(SeamRapierVoxelNeighbors.Kind.EDGE,
            SeamRapierVoxelNeighbors.classify(true,true,false,
                true,true,true,false,false,true));
    }

    @Test void twoPairedAxesAreFace() {
        assertEquals(SeamRapierVoxelNeighbors.Kind.FACE,
            SeamRapierVoxelNeighbors.classify(true,true,false,
                true,true,true,true,false,false));
    }

    @Test void incompleteOrSpecialNeighborsStayConservative() {
        assertEquals(SeamRapierVoxelNeighbors.Kind.CORNER,
            SeamRapierVoxelNeighbors.classify(true,true,false,
                true,false,true,false,false,true));
        assertEquals(SeamRapierVoxelNeighbors.Kind.CORNER,
            SeamRapierVoxelNeighbors.classify(true,false,false,
                true,true,true,true,true,true));
        assertEquals(SeamRapierVoxelNeighbors.Kind.CORNER,
            SeamRapierVoxelNeighbors.classify(true,true,true,
                true,true,true,true,true,true));
        assertEquals(SeamRapierVoxelNeighbors.Kind.EMPTY,
            SeamRapierVoxelNeighbors.classify(false,true,false,
                true,true,true,true,true,true));
    }

    @Test void nativeWitnessMustMatchExactVoxelNotSharedBlockState() {
        assertTrue(SeamRapierVoxelNeighbors.matchesWitness(-312,111,-15,
            -312,111,-15));
        assertFalse(SeamRapierVoxelNeighbors.matchesWitness(-311,111,-15,
            -312,111,-15));
        assertFalse(SeamRapierVoxelNeighbors.matchesWitness(-312,112,-15,
            -312,111,-15));
        assertFalse(SeamRapierVoxelNeighbors.matchesWitness(-312,111,-14,
            -312,111,-15));
    }
}
