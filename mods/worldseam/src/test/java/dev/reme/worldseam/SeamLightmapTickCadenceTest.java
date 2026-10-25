package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamLightmapTickCadenceTest {
    @Test void multipleRenderPassesWithinOneWorldTickDoNotAdvanceFlicker() {
        var cadence = new SeamLightmapTickCadence();
        Object world = new Object();
        assertTrue(cadence.shouldTick(world, 500));
        for (int i = 0; i < 240; i++)
            assertFalse(cadence.shouldTick(world, 500));
        assertTrue(cadence.shouldTick(world, 501));
        assertFalse(cadence.shouldTick(world, 501));
    }

    @Test void realClientLevelHandoffResetsCadenceEvenAtSameGameTime() {
        var cadence = new SeamLightmapTickCadence();
        Object overworld = new Object();
        Object nether = new Object();
        assertTrue(cadence.shouldTick(overworld, 900));
        assertTrue(cadence.shouldTick(nether, 900));
        assertFalse(cadence.shouldTick(nether, 900));
        assertTrue(cadence.shouldTick(overworld, 900));
    }

    @Test void worldTimeResyncAndReleaseAreNotTreatedAsFrozenLight() {
        var cadence = new SeamLightmapTickCadence();
        Object world = new Object();
        assertTrue(cadence.shouldTick(world, 200));
        assertTrue(cadence.shouldTick(world, 180));
        assertFalse(cadence.shouldTick(world, 180));
        cadence.reset();
        assertTrue(cadence.shouldTick(world, 180));
        assertThrows(IllegalArgumentException.class,
            () -> cadence.shouldTick(null, 181));
    }
}
