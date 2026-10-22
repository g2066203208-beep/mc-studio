package dev.reme.worldseam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deduplicated native 16x16x16 rebuilds, independent from Minecraft or GL.
 * Chunk packets invalidate the whole affected 160-block seam band, but the
 * client tick only rebuilds a small number of sections, never full columns.
 */
public final class SeamSectionWorkQueue {
    public record Work(int chunkX,int chunkZ,int sectionY) {}
    private final Set<Work> pending=new LinkedHashSet<>();

    public void invalidateWithNeighbors(int chunkX,int chunkZ,int firstY,int endYExclusive) {
        if(endYExclusive<=firstY || (long)endYExclusive-firstY>512)
            throw new IllegalArgumentException("Invalid bounded seam band");
        int first=Math.floorDiv(firstY,16);
        int last=Math.floorDiv(endYExclusive-1,16);
        for(int sy=first;sy<=last;sy++) {
            pending.add(new Work(chunkX,chunkZ,sy));
            pending.add(new Work(chunkX-1,chunkZ,sy));
            pending.add(new Work(chunkX+1,chunkZ,sy));
            pending.add(new Work(chunkX,chunkZ-1,sy));
            pending.add(new Work(chunkX,chunkZ+1,sy));
        }
    }

    public List<Work> drain(int budget,int centerX,int centerZ,int radius,int closestY) {
        if(budget<0 || budget>32 || radius<0 || radius>8)
            throw new IllegalArgumentException("Invalid section rebuild budget");
        pending.removeIf(w->Math.abs((long)w.chunkX()-centerX)>radius ||
            Math.abs((long)w.chunkZ()-centerZ)>radius);
        if(pending.isEmpty()||budget==0)return List.of();
        var order=new ArrayList<>(pending);
        order.sort(Comparator
            .comparingLong((Work w)->{
                long dx=(long)w.chunkX()-centerX,dz=(long)w.chunkZ()-centerZ;
                return dx*dx+dz*dz;
            })
            .thenComparingLong(w->Math.abs((long)w.sectionY()-closestY))
            .thenComparingInt(Work::chunkX)
            .thenComparingInt(Work::chunkZ)
            .thenComparingInt(Work::sectionY));
        var result=List.copyOf(order.subList(0,Math.min(budget,order.size())));
        pending.removeAll(result);
        return result;
    }

    public int size() {return pending.size();}
    public void clear() {pending.clear();}
}
