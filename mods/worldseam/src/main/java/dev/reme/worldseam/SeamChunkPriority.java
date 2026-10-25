package dev.reme.worldseam;

import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.IntPredicate;

/**
 * Pure, side-effect-free 5x5 chunk planner; no Minecraft classloader needed.
 * All offset arrays must be bounded and center-first.
 */
public final class SeamChunkPriority {
    private SeamChunkPriority() {}
    public static final int MAX_RADIUS = 16;

    /** Real chunk rings, center first; never put far chunks before near ones. */
    public static int[][] centerFirstOffsets(int radius) {
        if (radius < 2 || radius > MAX_RADIUS)
            throw new IllegalArgumentException("Unsupported native view radius: " + radius);
        var list = new ArrayList<int[]>();
        for (int z = -radius; z <= radius; z++)
            for (int x = -radius; x <= radius; x++)
                list.add(new int[]{x, z});
        list.sort(Comparator.comparingInt((int[] a) ->
            Math.max(Math.abs(a[0]), Math.abs(a[1])))
            .thenComparingInt(a -> a[0] * a[0] + a[1] * a[1]));
        return list.toArray(new int[0][]);
    }

    public static int chooseSlot(int centerX,int centerZ,int[][] offsets,
            Map<Long,Long> sent,Set<Long> dirty,
            long tick,long refreshTicks,long changeCooldown) {
        return chooseSlot(centerX,centerZ,offsets,sent,dirty,tick,
            refreshTicks,changeCooldown,slot -> true);
    }

    /**
     * Prefer the nearest unsent chunk that is ACTUALLY resident. A missing
     * center chunk must not block already-resident neighboring sections:
     * server tickets load asynchronously and their completion order is not
     * guaranteed. The caller must never synchronously generate a chunk here.
     * The predicate receives the offset-array index, not a world chunk key.
     */
    public static int chooseSlot(int centerX,int centerZ,int[][] offsets,
            Map<Long,Long> sent,Set<Long> dirty,
            long tick,long refreshTicks,long changeCooldown,
            IntPredicate resident) {
        if(resident==null)throw new IllegalArgumentException("Missing residency predicate");
        if(offsets.length>(2*MAX_RADIUS+1)*(2*MAX_RADIUS+1) || offsets.length<1 ||
            refreshTicks<=0||changeCooldown<=0)
            throw new IllegalArgumentException("Invalid bounded window planner");
        // The crossing-critical 5x5 must finish its initial prewarm first.
        // Once resident, a REAL block edit in that 5x5 cannot wait behind
        // dozens/hundreds of optional outer-ring first-time chunk packets.
        // Otherwise the second ClientLevel can show stale blocks for seconds
        // at native render distances >2 despite a live server-side edit.
        // Preserve one bounded packet per caller tick; never force a load.
        for(int priority=0;priority<5;priority++) {
            for(int i=0;i<offsets.length;i++) {
                int x=centerX+offsets[i][0],z=centerZ+offsets[i][1];
                long key=((long)x<<32)|(z&0xffffffffL);
                Long previous=sent.get(key);
                boolean critical=Math.max(Math.abs(offsets[i][0]),
                    Math.abs(offsets[i][1]))<=2;
                boolean unseen=previous==null;
                boolean changed=!unseen && dirty.contains(key) &&
                    tick-previous>=changeCooldown;
                boolean expired=!unseen && !changed &&
                    tick-previous>=refreshTicks;
                if((priority==0 && critical && unseen) ||
                   (priority==1 && critical && changed) ||
                   (priority==2 && !critical && unseen) ||
                   (priority==3 && !critical && changed) ||
                   (priority==4 && expired)) {
                    if(resident.test(i)) return i;
                }
            }
        }
        return -1;
    }
}
