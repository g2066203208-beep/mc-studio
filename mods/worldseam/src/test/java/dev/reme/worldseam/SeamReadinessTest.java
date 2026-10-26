package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SeamReadinessTest {
    @Test void readySignalIsScopedAndExpires() {
        var u=UUID.randomUUID();
        SeamReadiness.clear();
        SeamReadiness.accept(u,"minecraft:overworld","minecraft:the_nether",25,100);
        assertTrue(SeamReadiness.isReady(u,"minecraft:overworld","minecraft:the_nether",200));
        assertFalse(SeamReadiness.isReady(u,"minecraft:overworld","minecraft:the_nether",201));
        assertFalse(SeamReadiness.isReady(u,"minecraft:the_nether","minecraft:overworld",100));
        SeamReadiness.invalidate(u);
        assertFalse(SeamReadiness.isReady(u,"minecraft:overworld","minecraft:the_nether",100));
    }
    @Test void acknowledgementIsInvalidAfterMovingToAnotherChunk() {
        var u=UUID.randomUUID();
        SeamReadiness.clear();
        SeamReadiness.accept(u,"minecraft:overworld","minecraft:the_nether",25,10);
        assertTrue(SeamReadiness.isReadyAt(u,"minecraft:overworld",
            "minecraft:the_nether",10,0,0));
        assertFalse(SeamReadiness.isReadyAt(u,"minecraft:overworld",
            "minecraft:the_nether",10,1,0));
        assertFalse(SeamReadiness.isReadyAt(u,"minecraft:overworld",
            "minecraft:the_nether",10,0,-1));
        SeamReadiness.clear();
    }

    @Test void insufficientOrUnknownDestinationRejected() {
        var u=UUID.randomUUID();
        SeamReadiness.clear();
        SeamReadiness.accept(u,"minecraft:overworld","minecraft:the_end",24,42);
        assertFalse(SeamReadiness.isReady(u,"minecraft:overworld","minecraft:the_end",42));
        SeamReadiness.accept(u,"minecraft:overworld","minecraft:random",99,42);
        assertFalse(SeamReadiness.isReady(u,"minecraft:overworld","minecraft:random",42));
        SeamReadiness.accept(u,"minecraft:the_end","minecraft:overworld",25,42);
        assertFalse(SeamReadiness.isReady(u,"minecraft:the_end","minecraft:overworld",43));
        SeamReadiness.accept(u,"minecraft:the_end","minecraft:the_nether",25,42);
        assertTrue(SeamReadiness.isReady(u,"minecraft:the_end","minecraft:the_nether",43));
        SeamReadiness.clear();
    }
    @Test void delayedAckCannotBeReboundToDifferentServerChunk() {
        var u=UUID.randomUUID();
        SeamReadiness.clear();
        assertTrue(SeamReadiness.acceptAuthorized(u,"minecraft:overworld",
            "minecraft:the_nether",25,100,-2,3,-2,3));
        assertTrue(SeamReadiness.isReadyAt(u,"minecraft:overworld",
            "minecraft:the_nether",100,-2,3));
        // Packet was assembled at (-2,3), but the server player has moved.
        assertFalse(SeamReadiness.acceptAuthorized(u,"minecraft:overworld",
            "minecraft:the_nether",25,101,-2,3,-1,3));
        assertFalse(SeamReadiness.isReadyAt(u,"minecraft:overworld",
            "minecraft:the_nether",101,-1,3));
        assertFalse(SeamReadiness.isReadyAt(u,"minecraft:overworld",
            "minecraft:the_nether",101,-2,3));
        // A fresh, matching client ACK can grant the new window.
        assertTrue(SeamReadiness.acceptAuthorized(u,"minecraft:overworld",
            "minecraft:the_nether",25,102,-1,3,-1,3));
        assertTrue(SeamReadiness.isReadyAt(u,"minecraft:overworld",
            "minecraft:the_nether",102,-1,3));
        // Insufficient chunk count must fail closed even with matching center.
        assertFalse(SeamReadiness.acceptAuthorized(u,"minecraft:overworld",
            "minecraft:the_nether",24,103,-1,3,-1,3));
        assertFalse(SeamReadiness.isReadyAt(u,"minecraft:overworld",
            "minecraft:the_nether",103,-1,3));
        SeamReadiness.clear();
    }

}
