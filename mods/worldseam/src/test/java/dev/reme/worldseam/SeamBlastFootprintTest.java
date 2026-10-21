package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamBlastFootprintTest {
    @Test void tntNearChunkCornerNeedsOnlyActualOverlappingRemoteChunks() {
        var r=SeamBlastFootprint.around(2.5,-5.5,4);
        assertEquals(-1,r.minX()); assertEquals(0,r.maxX());
        assertEquals(-1,r.minZ()); assertEquals(0,r.maxZ());
        assertEquals(4,r.count());
        assertTrue(SeamBlastFootprint.fullyResident(r,(x,z)->x>=-1&&x<=0&&z>=-1&&z<=0));
        assertFalse(SeamBlastFootprint.fullyResident(r,(x,z)->x==0&&z==0));
    }
    @Test void notLoadingUnrelatedDiagonalChunks() {
        var r=SeamBlastFootprint.around(8.5,8.5,3);
        assertEquals(1,r.count());
        assertTrue(SeamBlastFootprint.fullyResident(r,(x,z)->x==0&&z==0));
    }
    @Test void negativeCoordinateChunkMappingPreserved() {
        var r=SeamBlastFootprint.around(-16.5,-16.5,4);
        assertEquals(-2,r.minX()); assertEquals(-1,r.maxX());
        assertEquals(-2,r.minZ()); assertEquals(-1,r.maxZ());
    }
    @Test void boundedRadiusMustNeverBecomeUnboundedRemoteLoads() {
        assertThrows(IllegalArgumentException.class,()->
            SeamBlastFootprint.around(0,0,1000));
        assertThrows(IllegalArgumentException.class,()->
            SeamBlastFootprint.around(Double.NaN,0,4));
        assertThrows(IllegalArgumentException.class,()->
            SeamBlastFootprint.around(0,0,0));
    }
}
