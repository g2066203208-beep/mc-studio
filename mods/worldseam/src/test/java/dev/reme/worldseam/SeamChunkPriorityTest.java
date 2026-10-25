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
    @Test void nativeOuterRingsNeverPreemptCrossingCriticalFiveByFive() {
        int[][] offsets=SeamChunkPriority.centerFirstOffsets(8);
        assertEquals(289,offsets.length);
        assertArrayEquals(new int[]{0,0},offsets[0]);
        for(int i=0;i<offsets.length;i++) {
            int ring=Math.max(Math.abs(offsets[i][0]),Math.abs(offsets[i][1]));
            if(i<25)assertTrue(ring<=2);
            if(i>=25)assertTrue(ring>=3);
        }
        var sent=new HashMap<Long,Long>();
        for(int i=0;i<25;i++)
            sent.put(k(offsets[i][0],offsets[i][1]),10L);
        // A live edit in the already-loaded crossing 5x5 is more urgent
        // than an optional first-time outer-ring packet.
        assertEquals(0,SeamChunkPriority.chooseSlot(
            0,0,offsets,sent,Set.of(k(0,0)),200,1200,20));
    }
    @Test void missingCriticalChunkStillPreemptsDirtyCriticalEdit() {
        int[][] offsets=SeamChunkPriority.centerFirstOffsets(4);
        var sent=new HashMap<Long,Long>();
        // 24 of the 25 critical chunks arrived. A dirty center must not
        // hide the last missing critical native section.
        for(int i=0;i<24;i++)
            sent.put(k(offsets[i][0],offsets[i][1]),10L);
        assertEquals(24,SeamChunkPriority.chooseSlot(0,0,offsets,
            sent,Set.of(k(0,0)),100,1200,20));
    }

    @Test void liveCriticalEditDoesNotWaitForSixteenChunkRadiusOuterRing() {
        int[][] offsets=SeamChunkPriority.centerFirstOffsets(16);
        var sent=new HashMap<Long,Long>();
        for(int i=0;i<25;i++)
            sent.put(k(offsets[i][0],offsets[i][1]),10L);
        int chosen=SeamChunkPriority.chooseSlot(0,0,offsets,
            sent,Set.of(k(0,0)),100,1200,20,
            slot -> slot==0 || slot>=25);
        assertEquals(0,chosen);
        // A non-resident dirty center must not stall resident outer work.
        assertEquals(25,SeamChunkPriority.chooseSlot(0,0,offsets,
            sent,Set.of(k(0,0)),100,1200,20,slot -> slot>=25));
    }

    @Test void nativeViewRadiusIsBoundedAndComplete() {
        assertEquals(25,SeamChunkPriority.centerFirstOffsets(2).length);
        assertEquals(1089,SeamChunkPriority.centerFirstOffsets(16).length);
        assertThrows(IllegalArgumentException.class,
            () -> SeamChunkPriority.centerFirstOffsets(1));
        assertThrows(IllegalArgumentException.class,
            () -> SeamChunkPriority.centerFirstOffsets(17));
    }
    @Test void earliestRefreshOnlyAfterDeadline() {
        var sent=new HashMap<Long,Long>();
        for(var p:OFFSETS)sent.put(k(p[0],p[1]),100L);
        assertEquals(-1,choose(sent,Set.of(),1299));
        assertEquals(0,choose(sent,Set.of(),1300));
    }
    @Test void pendingCenterNeverStarvesResidentNativeOuterSections() {
        int[][] offsets=SeamChunkPriority.centerFirstOffsets(4);
        var sent=new HashMap<Long,Long>();
        var dirty=new HashSet<Long>();
        assertEquals(0,SeamChunkPriority.chooseSlot(0,0,offsets,
            sent,dirty,100,1200,20));
        // Ticketed center is not yet loaded, but its neighbor and an outer
        // section are already resident: send the nearest ready one.
        int available=SeamChunkPriority.chooseSlot(0,0,offsets,
            sent,dirty,100,1200,20,
            i -> (offsets[i][0]==1 && offsets[i][1]==0) ||
                 (offsets[i][0]==4 && offsets[i][1]==0));
        assertTrue(available>0);
        assertArrayEquals(new int[]{1,0},offsets[available]);
        sent.put(k(1,0),100L);
        int next=SeamChunkPriority.chooseSlot(0,0,offsets,
            sent,dirty,101,1200,20,
            i -> offsets[i][0]==4 && offsets[i][1]==0);
        assertArrayEquals(new int[]{4,0},offsets[next]);
        // All candidates still loading must NOT trigger a blocking fetch.
        assertEquals(-1,SeamChunkPriority.chooseSlot(0,0,offsets,
            sent,dirty,102,1200,20,i -> false));
        // Once the center completes, it regains priority.
        assertEquals(0,SeamChunkPriority.chooseSlot(0,0,offsets,
            sent,dirty,103,1200,20,i -> i==0));
    }

    @Test void readyDirtyChunkMayProgressWhenNewChunksAreAllPending() {
        int[][] offsets=SeamChunkPriority.centerFirstOffsets(2);
        var sent=new HashMap<Long,Long>();
        sent.put(k(0,0),0L);
        var dirty=Set.of(k(0,0));
        assertEquals(0,SeamChunkPriority.chooseSlot(0,0,offsets,
            sent,dirty,100,1200,20,i -> i==0));
    }

}
