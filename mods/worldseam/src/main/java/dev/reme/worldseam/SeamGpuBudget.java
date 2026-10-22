package dev.reme.worldseam;

/** Conservative memory estimate for persistent remote block VBO allocation. */
public final class SeamGpuBudget {
    private SeamGpuBudget() {}
    /** VertexBuffer allocation varies with model geometry and driver. */
    public static long estimateBytes(int cells,int layers) {
        if(cells<0 || layers<0)
            throw new IllegalArgumentException("Negative geometry");
        return Math.addExact(
            Math.multiplyExact((long)cells, 768L),
            Math.multiplyExact((long)layers, 4096L));
    }
    public static double coverage(int available,int drawn) {
        if(available<=0)return 0.0;
        return Math.max(0,Math.min(1,(double)drawn/available));
    }
    public static boolean shouldCompile(int cells,int remaining,int compiled) {
        return cells>0 && (cells<=remaining || compiled==0);
    }
}
