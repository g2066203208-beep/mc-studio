package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SeamReturnGuardTest {
    @Test void cannotFallBackAfterNetherRoofToOverworldFloor() {
        SeamReturnGuard g=new SeamReturnGuard();
        UUID p=UUID.randomUUID();
        g.landed(p,"minecraft:overworld",SeamGraph.EdgeSide.BOTTOM);
        assertTrue(g.blocked(p,"minecraft:overworld",SeamGraph.EdgeSide.BOTTOM,-63.875,-64,320));
        // Simulates gravity trying to drop the player below the same seam.
        assertTrue(g.blocked(p,"minecraft:overworld",SeamGraph.EdgeSide.BOTTOM,-63.98,-64,320));
        assertEquals(-63.875,SeamReturnGuard.safeY(SeamGraph.EdgeSide.BOTTOM,-64,320));
        // Horizontal or unrelated TOP seam remains unaffected.
        assertFalse(g.blocked(p,"minecraft:overworld",SeamGraph.EdgeSide.TOP,319.9,-64,320));
        // Player traverses safely into the new world, so returns become possible.
        assertFalse(g.blocked(p,"minecraft:overworld",SeamGraph.EdgeSide.BOTTOM,-62.7,-64,320));
        assertFalse(g.blocked(p,"minecraft:overworld",SeamGraph.EdgeSide.BOTTOM,-63.9,-64,320));
    }

    @Test void cannotInstantlyBounceFromOverworldFloorIntoNetherRoof() {
        SeamReturnGuard g=new SeamReturnGuard();
        UUID p=UUID.randomUUID();
        g.landed(p,"minecraft:the_nether",SeamGraph.EdgeSide.TOP);
        assertTrue(g.blocked(p,"minecraft:the_nether",SeamGraph.EdgeSide.TOP,127.875,0,128));
        assertEquals(127.875,SeamReturnGuard.safeY(SeamGraph.EdgeSide.TOP,0,128));
        assertTrue(g.blocked(p,"minecraft:the_nether",SeamGraph.EdgeSide.TOP,127.99,0,128));
        assertFalse(g.blocked(p,"minecraft:the_nether",SeamGraph.EdgeSide.TOP,126.7,0,128));
        assertFalse(g.blocked(p,"minecraft:the_nether",SeamGraph.EdgeSide.TOP,127.875,0,128));
    }

    @Test void noPlayerSessionLeaksAcrossClear() {
        SeamReturnGuard g=new SeamReturnGuard();
        UUID p=UUID.randomUUID();
        g.landed(p,"minecraft:overworld",SeamGraph.EdgeSide.BOTTOM);
        assertEquals(1,g.size());
        g.forget(p);
        assertEquals(0,g.size());
        g.landed(p,"minecraft:overworld",SeamGraph.EdgeSide.BOTTOM);
        g.clear();
        assertEquals(0,g.size());
    }
}
