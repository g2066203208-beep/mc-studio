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
    void loadedNativeEditCannotStarveBehindMissingChunks() {
        var q = new SeamBoundedRetryQueue<String>(2);
        q.enqueue("missing-a");
        q.enqueue("missing-b");
        q.enqueue("loaded-dirty"); // previously blocked forever in overflow

        assertEquals("missing-a", q.poll());
        q.defer("missing-a");
        assertEquals("missing-b", q.poll());
        q.defer("missing-b");
        // Fair rotation has admitted the loaded edit, even though both
        // original entries are still waiting for authoritative chunks.
        assertEquals("loaded-dirty", q.poll());
        q.complete("loaded-dirty");
        assertEquals(0, q.deferredCount());
        assertEquals("missing-a", q.poll());
        q.defer("missing-a");
        assertEquals("missing-b", q.poll());
        q.defer("missing-b");
    }

    @Test
    void repeatedDeferralsWithOverflowPreserveEveryUniqueSection() {
        var q = new SeamBoundedRetryQueue<Integer>(1);
        q.enqueue(1);
        q.enqueue(2);
        q.enqueue(3);
        for (int i = 0; i < 240; i++) {
            int item = q.poll();
            q.defer(item);
            assertEquals(1, q.size());
            assertEquals(2, q.deferredCount());
        }
        var seen = new java.util.HashSet<Integer>();
        for (int i = 0; i < 3; i++) {
            int item = q.poll();
            assertTrue(seen.add(item));
            q.complete(item);
        }
        assertEquals(java.util.Set.of(1, 2, 3), seen);
        assertNull(q.poll());
        assertEquals(0, q.deferredCount());
    }

    @Test
    void actualTargetBlockEditPreemptsDistantInitialNativeUploads() {
        var q = new SeamBoundedRetryQueue<String>(2);
        q.enqueue("center");
        q.enqueue("far-a");
        q.enqueue("far-b");
        q.enqueue("edited-native-section");
        q.promote("edited-native-section");
        assertEquals(2, q.size());
        assertEquals(2, q.deferredCount());
        assertEquals("edited-native-section", q.poll());
        q.complete("edited-native-section");
        assertEquals("center", q.poll());
        q.complete("center");
        var remaining = new java.util.HashSet<String>();
        remaining.add(q.poll());
        remaining.add(q.poll());
        assertEquals(java.util.Set.of("far-a", "far-b"), remaining);
        for (var item : remaining) q.complete(item);
        assertNull(q.poll());
    }

    @Test
    void secondRealEditDuringNativeUploadIsReplayedAfterJniReturns() {
        var q = new SeamBoundedRetryQueue<String>(1);
        q.enqueue("nether:0,7,0");
        assertEquals("nether:0,7,0", q.poll());
        // A synchronous Sable callback may notify a block edit while the
        // first JNI upload is still in flight on this server thread.
        q.promote("nether:0,7,0");
        q.complete("nether:0,7,0");
        assertEquals("nether:0,7,0", q.poll());
        q.complete("nether:0,7,0");
        assertNull(q.poll());
    }

    @Test
    void promotedMissingSectionStillDefersWithoutDroppingWork() {
        var q = new SeamBoundedRetryQueue<String>(1);
        q.enqueue("unloaded");
        q.enqueue("loaded");
        q.promote("loaded");
        assertEquals("loaded", q.poll());
        q.complete("loaded");
        assertEquals("unloaded", q.poll());
        q.promote("unloaded"); // callback before getChunkNow failure
        q.defer("unloaded");
        assertEquals("unloaded", q.poll());
        q.complete("unloaded");
        assertNull(q.poll());
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
