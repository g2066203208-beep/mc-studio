package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SeamBoundedRetryQueueTest {
    @Test
    void unloadedNativeSectionIsRetriedAfterChunkBecomesResident() {
        var q = new SeamBoundedRetryQueue<String>(2);
        q.enqueue("nether:0,7,0");
        assertEquals("nether:0,7,0", q.poll());
        q.defer("nether:0,7,0"); // getChunkNow returned null
        assertEquals(1, q.size());
        assertEquals("nether:0,7,0", q.poll()); // now resident
        q.complete("nether:0,7,0");
        assertEquals(0, q.size());
    }

    @Test
    void saturatedQueueRetainsDirtySectionUntilNativeUploadCompletes() {
        var q = new SeamBoundedRetryQueue<String>(2);
        q.enqueue("old0");
        q.enqueue("old1");
        q.enqueue("reloaded"); // previously uploaded section was evicted
        q.enqueue("edited");   // block changed while both slots occupied
        q.enqueue("edited");   // duplicate block edits do not multiply work
        assertEquals(2, q.size());
        assertEquals(2, q.deferredCount());
        assertEquals("old0", q.poll());
        q.complete("old0");
        assertEquals(1, q.deferredCount()); // second deferred edit remains accounted for
        assertEquals(2, q.size());
        assertEquals("old1", q.poll());
        q.complete("old1");
        assertEquals("reloaded", q.poll());
        q.complete("reloaded");
        assertEquals("edited", q.poll());
        q.complete("edited");
        assertEquals(0, q.size());
    }

    @Test
    void worldOrShipWindowChangeDiscardsAllOldRetries() {
        var q = new SeamBoundedRetryQueue<Integer>(1);
        q.enqueue(1);
        q.enqueue(2);
        q.clear();
        assertNull(q.poll());
        assertEquals(0, q.deferredCount());
        q.enqueue(3);
        assertEquals(3, q.poll());
        q.complete(3);
    }

    @Test
    void repeatedUnloadedPollDoesNotGrowQueue() {
        var q = new SeamBoundedRetryQueue<Integer>(1);
        q.enqueue(9);
        for(int i=0;i<240;i++) {
            assertEquals(9,q.poll());
            q.defer(9);
            assertEquals(1,q.size());
        }
        assertEquals(9,q.poll());
        q.complete(9);
    }
}
