package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamNativeViewDistanceTest {
    @Test void neverTreatFiveByFivePrefetchAsTheActiveWorldRenderDistance() {
        assertEquals(12,SeamNativeViewDistance.normalize(12));
        assertEquals(24,SeamNativeViewDistance.normalize(24));
        assertEquals(32,SeamNativeViewDistance.normalize(1000));
        assertEquals(3,SeamNativeViewDistance.normalize(-1));
    }

    @Test void nativeFarChunkProbeIsOutsidePrewarmingFootprint() {
        assertEquals(4,SeamNativeViewDistance.farChunkX(0));
        assertEquals(-20,SeamNativeViewDistance.farChunkX(-24));
        assertTrue(SeamNativeViewDistance.shouldRequireVanillaFarChunk(12));
        assertFalse(SeamNativeViewDistance.shouldRequireVanillaFarChunk(3));
    }
}
