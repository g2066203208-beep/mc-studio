package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamOffsetVertexConsumerTest {
    @Test void liquidVerticesUseExactTranslatedCameraAndDimensionOffset() {
        // Native Nether ceiling lava Y=128 rendered below Overworld floor:
        // -64-128 = -192 plus camera-relative -0.5 => -192.5.
        var p=SeamFluidCoordinates.shifted(13,128,9,
            -12.5f,-192.5f,8.5f);
        assertEquals(0.5f,p.x(),1e-6f);
        assertEquals(-64.5f,p.y(),1e-6f);
        assertEquals(17.5f,p.z(),1e-6f);
    }
    @Test void noCumulativeDriftAcrossVertices() {
        var p=SeamFluidCoordinates.shifted(0,0,0,2,3,4);
        assertEquals(2,p.x());
        assertEquals(3,p.y());
        var next=SeamFluidCoordinates.shifted(1,1,1,2,3,4);
        assertEquals(3,next.x());
        assertEquals(4,next.y());
        assertEquals(5,next.z());
    }
    @Test void endTopToNetherFloorMappingSupportsNegativeCoordinates() {
        var p=SeamFluidCoordinates.shifted(-16.5f,255.875f,-32.5f,
            4.5f,-255.875f,1.5f);
        assertEquals(-12,p.x(),1e-6f);
        assertEquals(0,p.y(),1e-6f);
        assertEquals(-31,p.z(),1e-6f);
    }
}
