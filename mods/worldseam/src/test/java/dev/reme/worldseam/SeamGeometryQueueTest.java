package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SeamGeometryQueueTest {
    @Test void oneChunkInvalidatesItselfAndFourNeighbors() {
        SeamGeometryQueue q = new SeamGeometryQueue();
        q.invalidate(10, -9);
        List<Long> work = q.drain(10,10,-9,2);
        assertEquals(5,work.size());
        assertEquals(5,new HashSet<>(work).size());
        assertTrue(work.contains(SeamGeometryQueue.pack(10,-9)));
        assertTrue(work.contains(SeamGeometryQueue.pack(11,-9)));
        assertTrue(work.contains(SeamGeometryQueue.pack(10,-10)));
        assertEquals(0,q.pending());
    }

    @Test void burstCoalescesAndBuildsAtMostTwoColumnsEachTick() {
        SeamGeometryQueue q = new SeamGeometryQueue();
        for (int i=0;i<50;i++) q.invalidate(0,0);
        assertEquals(5,q.pending());
        assertEquals(2,q.drain(2,0,0,2).size());
        assertEquals(3,q.pending());
        assertEquals(2,q.drain(2,0,0,2).size());
        assertEquals(1,q.drain(2,0,0,2).size());
        assertTrue(q.drain(2,0,0,2).isEmpty());
    }

    @Test void nearCameraWinsOverPacketArrivalOrder() {
        SeamGeometryQueue q = new SeamGeometryQueue();
        q.invalidate(2,2);
        q.invalidate(0,0);
        assertEquals(Long.valueOf(SeamGeometryQueue.pack(0,0)),q.drain(1,0,0,2).get(0));
    }

    @Test void movingAwayDropsStaleWindowAndSupportsNegativeChunks() {
        SeamGeometryQueue q = new SeamGeometryQueue();
        q.invalidate(-800,-400);
        q.invalidate(120,110);
        assertEquals(List.of(SeamGeometryQueue.pack(-800,-400)),
            q.drain(2,-800,-400,0));
        assertEquals(0,q.pending());
        long packed=SeamGeometryQueue.pack(-800,-400);
        assertEquals(-800,SeamGeometryQueue.x(packed));
        assertEquals(-400,SeamGeometryQueue.z(packed));
    }

    @Test void invalidBudgetRejectedAndClearReleasesPendingWork() {
        SeamGeometryQueue q = new SeamGeometryQueue();
        q.invalidate(1,2);
        assertThrows(IllegalArgumentException.class,()->q.drain(-1,1,2,2));
        assertThrows(IllegalArgumentException.class,()->q.drain(2,1,2,9));
        q.clear();
        assertEquals(0,q.pending());
    }

    @Test void zeroBudgetDoesNotConsumeWork() {
        SeamGeometryQueue q = new SeamGeometryQueue();
        q.invalidate(0,0);
        assertTrue(q.drain(0,0,0,2).isEmpty());
        assertEquals(5,q.pending());
    }
}
