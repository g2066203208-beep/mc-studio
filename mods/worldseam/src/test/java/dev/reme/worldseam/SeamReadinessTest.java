package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SeamReadinessTest {
    @Test void readySignalIsScopedAndExpires() {
        var u=UUID.randomUUID();
        SeamReadiness.clear();
        SeamReadiness.accept(u,"minecraft:overworld","minecraft:the_nether",9,100);
        assertTrue(SeamReadiness.isReady(u,"minecraft:overworld","minecraft:the_nether",200));
        assertFalse(SeamReadiness.isReady(u,"minecraft:overworld","minecraft:the_nether",201));
        assertFalse(SeamReadiness.isReady(u,"minecraft:the_nether","minecraft:overworld",100));
        SeamReadiness.invalidate(u);
        assertFalse(SeamReadiness.isReady(u,"minecraft:overworld","minecraft:the_nether",100));
    }
    @Test void insufficientOrUnknownDestinationRejected() {
        var u=UUID.randomUUID();
        SeamReadiness.clear();
        SeamReadiness.accept(u,"minecraft:overworld","minecraft:the_end",8,42);
        assertFalse(SeamReadiness.isReady(u,"minecraft:overworld","minecraft:the_end",42));
        SeamReadiness.accept(u,"minecraft:overworld","minecraft:random",99,42);
        assertFalse(SeamReadiness.isReady(u,"minecraft:overworld","minecraft:random",42));
        SeamReadiness.accept(u,"minecraft:the_end","minecraft:overworld",9,42);
        assertTrue(SeamReadiness.isReady(u,"minecraft:the_end","minecraft:overworld",43));
        SeamReadiness.clear();
    }
}
