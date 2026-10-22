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

    @Test void clearReleasesPendingWorkAfterDimensionChange() {
        var q=new SeamSectionWorkQueue();
        q.invalidateWithNeighbors(0,0,0,160);
        q.clear();
        assertEquals(0,q.size());
    }
}
