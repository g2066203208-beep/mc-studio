package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SeamChunkPriorityTest {
    private static final int[][] OFFSETS={{0,0},{1,0},{-1,0},{0,1},{-1,1}};

    private static long k(int x,int z) {
        return ((long)x<<32)|(z&0xffffffffL);
    }
    private static int choose(Map<Long,Long> sent,Set<Long> dirty,long tick) {
        return SeamChunkPriority.chooseSlot(0,0,OFFSETS,sent,dirty,tick,1200,20);
    }
    @Test void unseenOuterChunkAlwaysWinsOverDirtyCenter() {
        var sent=new HashMap<Long,Long>();
        for(int i=0;i<OFFSETS.length-1;i++)
            sent.put(k(OFFSETS[i][0],OFFSETS[i][1]),10L);
        var dirty=Set.of(k(0,0));
        assertEquals(4,choose(sent,dirty,200));
    }
    @Test void actualChangedChunkWinsOverPeriodicRefresh() {
        var sent=new HashMap<Long,Long>();
        for(var p:OFFSETS)sent.put(k(p[0],p[1]),0L);
        // center expired, adjacent changed: dirty first.
        assertEquals(1,choose(sent,Set.of(k(1,0)),1300));
    }
    @Test void steadyWindowDoesNotFetchOrTransmitAnyChunk() {
        var sent=new HashMap<Long,Long>();
        for(var p:OFFSETS)sent.put(k(p[0],p[1]),400L);
        assertEquals(-1,choose(sent,Set.of(),500));
    }
    @Test void newChunkRingAfterMovementHasPriorityOverStaleOldCenter() {
        var sent=new HashMap<Long,Long>();
        sent.put(k(0,0),1L);
        sent.put(k(1,0),1L);
        int slot=SeamChunkPriority.chooseSlot(1,0,OFFSETS,
            sent,Set.of(k(1,0)),250,1200,20);
        assertEquals(1,slot);
    }
    @Test void earliestRefreshOnlyAfterDeadline() {
        var sent=new HashMap<Long,Long>();
        for(var p:OFFSETS)sent.put(k(p[0],p[1]),100L);
        assertEquals(-1,choose(sent,Set.of(),1299));
        assertEquals(0,choose(sent,Set.of(),1300));
    }
}
