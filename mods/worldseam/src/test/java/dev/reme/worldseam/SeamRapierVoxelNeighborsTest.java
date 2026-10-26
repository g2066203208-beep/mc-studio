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

    @Test void projectedNativeSectionCannotBeOccludedByLoadedUnprojectedTerrain() {
        // X and Z neighbors across 16-block section boundaries may exist in
        // the foreign ServerLevel but NOT in the native Rapier terrain.
        assertFalse(SeamRapierVoxelNeighbors.sameNativeSection(
            15,111,15,16,111,15));
        assertFalse(SeamRapierVoxelNeighbors.sameNativeSection(
            0,111,0,-1,111,0));
        assertFalse(SeamRapierVoxelNeighbors.sameNativeSection(
            0,111,0,0,111,-1));
        // Nether Y=127 and Y=128 belong to different native sections.
        assertFalse(SeamRapierVoxelNeighbors.sameNativeSection(
            0,127,0,0,128,0));
        assertFalse(SeamRapierVoxelNeighbors.sameNativeSection(
            0,-64,0,0,-65,0));
        // Interior neighbors are still eligible for normal FACE/EDGE
        // classification, including negative world coordinates.
        assertTrue(SeamRapierVoxelNeighbors.sameNativeSection(
            -16,111,-16,-15,111,-15));
        assertTrue(SeamRapierVoxelNeighbors.sameNativeSection(
            5,111,5,6,111,5));
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
