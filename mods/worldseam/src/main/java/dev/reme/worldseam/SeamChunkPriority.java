package dev.reme.worldseam;

import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;

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
        if(offsets.length>(2*MAX_RADIUS+1)*(2*MAX_RADIUS+1) || offsets.length<1 ||
            refreshTicks<=0||changeCooldown<=0)
            throw new IllegalArgumentException("Invalid bounded window planner");
        // IMPORTANT: exhaust all new chunks before ANY dirty or timed refresh.
        for(int priority=0;priority<3;priority++) {
            for(int i=0;i<offsets.length;i++) {
                int x=centerX+offsets[i][0],z=centerZ+offsets[i][1];
                long key=((long)x<<32)|(z&0xffffffffL);
                Long previous=sent.get(key);
                boolean unseen=previous==null;
                boolean changed=!unseen && dirty.contains(key) &&
                    tick-previous>=changeCooldown;
                boolean expired=!unseen && !changed &&
                    tick-previous>=refreshTicks;
                if(priority==0 && unseen ||
                   priority==1 && changed ||
                   priority==2 && expired)return i;
            }
        }
        return -1;
    }
}
