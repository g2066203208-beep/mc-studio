package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SeamSectionWorkQueueTest {
    @Test void packetInvalidatesSectionsIncludingTouchingChunkBoundaries() {
        var q=new SeamSectionWorkQueue();
        q.invalidateWithNeighbors(-1,2,-65,95);
        // [-65,95) spans 11 native chunk sections, times five neighbors.
        assertEquals(55,q.size());
        q.invalidateWithNeighbors(-1,2,-65,95);
        assertEquals(55,q.size());
        var all=q.drain(32,-1,2,2,-5);
        all=java.util.stream.Stream.concat(all.stream(),
            q.drain(32,-1,2,2,-5).stream()).toList();
        assertEquals(55,new HashSet<>(all).size());
        assertEquals(0,q.size());
    }

    @Test void eachTickProcessesAtMostFourSections() {
        var q=new SeamSectionWorkQueue();
        q.invalidateWithNeighbors(0,0,0,160);
        int total=0;
        while(q.size()!=0) {
            var done=q.drain(4,0,0,2,0);
            assertTrue(done.size()>0 && done.size()<=4);
            total+=done.size();
        }
        assertEquals(50,total);
    }

    @Test void cameraPriorityHandlesNegativeChunkAndNativeSection() {
        var q=new SeamSectionWorkQueue();
        q.invalidateWithNeighbors(-2,-5,-64,96);
        var tasks=q.drain(1,-2,-5,2,-4);
        assertEquals(new SeamSectionWorkQueue.Work(-2,-5,-4),tasks.get(0));
    }

    @Test void oldViewportWorkDiscardedBeforeComputing() {
        var q=new SeamSectionWorkQueue();
        q.invalidateWithNeighbors(1,1,0,32);
        assertTrue(q.drain(4,100,100,2,0).isEmpty());
        assertEquals(0,q.size());
    }

    @Test void invalidBandsAndOversizedBudgetFailClosed() {
        var q=new SeamSectionWorkQueue();
        assertThrows(IllegalArgumentException.class,()->q.invalidateWithNeighbors(0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->q.invalidateWithNeighbors(0,0,-100,1000));
        assertThrows(IllegalArgumentException.class,()->q.drain(-1,0,0,2,0));
        assertThrows(IllegalArgumentException.class,()->q.drain(33,0,0,2,0));
    }

    @Test void liveRemoteEditsRebuildBothSidesOfNativeVerticalSectionEdges() {
        var q=new SeamSectionWorkQueue();
        // Edit at native Y=15: the fluid/occlusion neighbors at Y=16
        // must be rebuilt, not just the containing Y=0..15 section.
        q.invalidateWithNeighbors(-2,3,14,17);
        assertEquals(10,q.size()); // 2 vertical sections x 5 columns
        var changed=new HashSet<>(q.drain(32,-2,3,2,0));
        assertTrue(changed.contains(new SeamSectionWorkQueue.Work(-2,3,0)));
        assertTrue(changed.contains(new SeamSectionWorkQueue.Work(-2,3,1)));
        assertTrue(changed.contains(new SeamSectionWorkQueue.Work(-3,3,1)));
        assertTrue(changed.contains(new SeamSectionWorkQueue.Work(-2,4,0)));
        assertEquals(10,changed.size());

        // Negative Y is floor-divided, not truncated towards zero.
        q.invalidateWithNeighbors(-2,3,-17,-14);
        var negative=new HashSet<>(q.drain(32,-2,3,2,-1));
        assertTrue(negative.contains(new SeamSectionWorkQueue.Work(-2,3,-2)));
        assertTrue(negative.contains(new SeamSectionWorkQueue.Work(-2,3,-1)));
        assertEquals(10,negative.size());
    }

    @Test void clearReleasesPendingWorkAfterDimensionChange() {
        var q=new SeamSectionWorkQueue();
        q.invalidateWithNeighbors(0,0,0,160);
        q.clear();
        assertEquals(0,q.size());
    }
}
