package dev.reme.worldseam;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamRapierAnchorTest {
    @Test void tracksRealHullWithoutAPlayerAndFloorsNegativeChunks() {
        assertEquals(new SeamRapierAnchor.Center(-1,1),
            SeamRapierAnchor.fromHull(new AABB(-2,-85,20,0,-62,22)));
        assertEquals(new SeamRapierAnchor.Center(2,-2),
            SeamRapierAnchor.fromHull(new AABB(31,-70,-33,35,-61,-29)));
    }

    @Test void refusesStaleSamplesAndCrossServerClockReset() {
        assertTrue(SeamRapierAnchor.fresh(100,120));
        assertFalse(SeamRapierAnchor.fresh(100,121));
        assertFalse(SeamRapierAnchor.fresh(120,119));
        assertFalse(SeamRapierAnchor.fresh(Long.MIN_VALUE,1));
    }

    @Test void refusesNonFiniteAndOutOfWorldAnchors() {
        assertNull(SeamRapierAnchor.fromHull(null));
        assertNull(SeamRapierAnchor.fromHull(
            new AABB(30_000_000,0,0,30_000_002,2,2)));
        assertNull(SeamRapierAnchor.fromHull(
            new AABB(Double.NEGATIVE_INFINITY,0,0,1,2,2)));
    }
}
