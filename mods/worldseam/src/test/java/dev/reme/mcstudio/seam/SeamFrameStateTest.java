package dev.reme.mcstudio.seam;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SeamFrameStateTest {
    private static final SeamFrameState.Position P = new SeamFrameState.Position(1, 400, 2);
    private static final SeamFrameState.Position Q = new SeamFrameState.Position(2, 400, 2);
    private static final SeamFrameState.Visual STONE = new SeamFrameState.Visual(2, 0xAF);
    private static final SeamFrameState.Visual DIRT = new SeamFrameState.Visual(3, 0xE0);

    @Test void deltaAddsUpdatesAndDeletesWithoutResendingTheRest() {
        var before = Map.of(P, STONE, Q, DIRT);
        var after = Map.of(P, DIRT);
        var changes = SeamFrameState.difference(before, after);
        assertEquals(2, changes.size());
        assertTrue(changes.contains(new SeamFrameState.Change(P, DIRT)));
        assertTrue(changes.contains(new SeamFrameState.Change(Q, null)));
        assertTrue(SeamFrameState.difference(after, after).isEmpty());
    }

    @Test void fullResetAndRevisionGuardPreventCrossWorldGhosts() {
        var cache = new SeamFrameState();
        var full = new SeamFrameState.Frame("minecraft:overworld", "nether:bottom",
            1, true, List.of(new SeamFrameState.Change(P, STONE)));
        assertTrue(cache.accept(full));
        assertFalse(cache.accept(full));
        assertFalse(cache.accept(new SeamFrameState.Frame("minecraft:overworld",
            "end:top", 2, false, List.of(new SeamFrameState.Change(Q, DIRT)))));
        assertEquals(Map.of(P, STONE), cache.blocks());
        assertTrue(cache.accept(new SeamFrameState.Frame("minecraft:overworld",
            "end:top", 3, true, List.of(new SeamFrameState.Change(Q, DIRT)))));
        assertEquals(Map.of(Q, DIRT), cache.blocks());
        assertTrue(cache.accept(new SeamFrameState.Frame("minecraft:overworld",
            "", 4, true, List.of())));
        assertTrue(cache.blocks().isEmpty());
    }

    @Test void lightOnlyChangeIsARealDelta() {
        var dim = new SeamFrameState.Visual(2, 0x10);
        var lit = new SeamFrameState.Visual(2, 0xF0);
        assertEquals(List.of(new SeamFrameState.Change(P, lit)),
            SeamFrameState.difference(Map.of(P, dim), Map.of(P, lit)));
    }

    @Test void invalidLightAndNegativeStateAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SeamFrameState.Visual(3, 256));
        assertThrows(IllegalArgumentException.class, () -> new SeamFrameState.Visual(-1, 0));
    }
}
