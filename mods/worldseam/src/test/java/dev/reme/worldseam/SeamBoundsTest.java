package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamBoundsTest {
    @Test void netherSeamMatchesActualRoofRatherThanEmptyBuildCeiling() {
        assertEquals(128, SeamBounds.top("minecraft:the_nether",256));
        assertEquals(320, SeamBounds.top("minecraft:overworld",320));
        assertEquals(256, SeamBounds.top("minecraft:the_end",256));
    }

    @Test void newChunkCarveOnlyTouchesSpecifiedWorldsAndBedrockBands() {
        assertEquals(new SeamBounds.CarveRange(-64,-60),
            SeamBounds.newChunkCarveRange("minecraft:overworld",-64));
        assertEquals(new SeamBounds.CarveRange(120,127),
            SeamBounds.newChunkCarveRange("minecraft:the_nether",0));
        assertNull(SeamBounds.newChunkCarveRange("minecraft:the_end",0));
        assertNull(SeamBounds.newChunkCarveRange("mod:other",0));
    }
}
