package dev.reme.worldseam;

import java.util.Arrays;

/** Low-allocation bounded CPU submission-time recorder, never a GPU FPS claim. */
public final class SeamRenderSamples {
    private final long[] nanos;
    private int count, next;
    public SeamRenderSamples(int capacity) {
        if(capacity<1 || capacity>2048)throw new IllegalArgumentException("Invalid sample capacity");
        nanos=new long[capacity];
    }
    public void add(long durationNanos) {
        if(durationNanos<0)throw new IllegalArgumentException("Negative duration");
        nanos[next]=durationNanos;
        next=(next+1)%nanos.length;
        if(count<nanos.length)count++;
    }
    public int count() {return count;}
    /** p95 CPU time in milliseconds; invoked only in diagnostics/evidence. */
    public double p95Ms() {
        if(count==0)return 0;
        long[] copy=Arrays.copyOf(nanos,count);
        Arrays.sort(copy);
        int index=Math.max(0,(int)Math.ceil(count*0.95)-1);
        return copy[index]/1_000_000.0;
    }
    public void clear() {
        count=next=0;
        Arrays.fill(nanos,0);
    }
}
