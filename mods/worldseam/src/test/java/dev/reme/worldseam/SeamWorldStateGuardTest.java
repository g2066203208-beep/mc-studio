package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamWorldStateGuardTest {
    @Test void acceptsOnlyTheCurrentSourceAndForeignClientLevel() {
        assertTrue(SeamWorldStateGuard.accepts(
            "minecraft:overworld", "minecraft:the_nether",
            "minecraft:overworld", "minecraft:the_nether", 0f, 1f));
        assertFalse(SeamWorldStateGuard.accepts(
            "minecraft:overworld", "minecraft:the_nether",
            "minecraft:the_nether", "minecraft:overworld", 0.5f, 0.5f));
        assertFalse(SeamWorldStateGuard.accepts(
            "minecraft:overworld", "minecraft:the_end",
            "minecraft:overworld", "minecraft:the_nether", 0.5f, 0.5f));
    }

    @Test void rejectsNonFiniteAndOutOfRangeAtmosphere() {
        assertFalse(SeamWorldStateGuard.accepts("a","b","a","b",
            Float.NaN, 0f));
        assertFalse(SeamWorldStateGuard.accepts("a","b","a","b",
            0f, Float.POSITIVE_INFINITY));
        assertFalse(SeamWorldStateGuard.accepts("a","b","a","b",
            -0.01f, 0f));
        assertFalse(SeamWorldStateGuard.accepts("a","b","a","b",
            0f, 1.01f));
        assertTrue(SeamWorldStateGuard.accepts("a","b","a","b",1f,0f));
    }
}
