package dev.reme.mcstudio.seam;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class SeamSectionCacheTest {
    private static SeamSectionCodec.Encoded uniform(int state) {
        int[] states=new int[4096];
        Arrays.fill(states, state);
        return SeamSectionCodec.encode(states, new byte[4096]);
    }

    private static SeamSectionCache.Frame packet(String scope, long revision,
                                                   int cx, int state) {
        return new SeamSectionCache.Frame("minecraft:overworld",
            "minecraft:the_nether", scope, revision, false,
            new SeamSectionCache.Key(cx, 15, 0), -64, 256, uniform(state));
    }

    @Test void stalePacketsNeverUndoNewerSections() {
        var cache=new SeamSectionCache();
        assertTrue(cache.apply(packet("underworld", 3, 0, 5)));
        assertFalse(cache.apply(packet("underworld", 2, 0, 8)));
        assertEquals(5, cache.snapshot().values().iterator().next().encoded().stateAt(0));
        assertTrue(cache.apply(packet("underworld", 4, 0, 8)));
        assertEquals(8, cache.snapshot().values().iterator().next().encoded().stateAt(0));
    }

    @Test void scopeSwitchAndClearRemoveWrongDimensionGhosts() {
        var cache=new SeamSectionCache();
        cache.apply(packet("old", 1, 0, 1));
        cache.apply(packet("new", 2, 1, 2));
        assertEquals(1, cache.size());
        assertTrue(cache.isFor("minecraft:overworld"));
        assertFalse(cache.isFor("minecraft:the_nether"));
        cache.apply(new SeamSectionCache.Frame("minecraft:overworld",
            "", "", 3, true, null, 0, 0, null));
        assertEquals(0, cache.size());
    }

    @Test void heartbeatsRefreshOnlyMatchingScope() {
        var cache = new SeamSectionCache();
        cache.apply(packet("nether", 1, 0, 5));
        assertTrue(cache.apply(new SeamSectionCache.Frame(
            "minecraft:overworld", "minecraft:the_nether", "nether",
            2, false, null, 0, 0, null
        )));
        assertEquals(1, cache.size());
        assertFalse(cache.apply(new SeamSectionCache.Frame(
            "minecraft:overworld", "minecraft:the_end", "wrong",
            3, false, null, 0, 0, null
        )));
        assertEquals(2, cache.lastRevision());
    }

    @Test void memoryNeverExceedsFixedSectionBudget() {
        var cache=new SeamSectionCache();
        for (int i=0; i<100; i++) assertTrue(cache.apply(packet("same", i+1, i, 1)));
        assertEquals(SeamSectionCache.MAX_SECTIONS, cache.size());
        assertFalse(cache.snapshot().containsKey(new SeamSectionCache.Key(0,15,0)));
        assertTrue(cache.snapshot().containsKey(new SeamSectionCache.Key(99,15,0)));
    }
}
