package dev.reme.worldseam;

/**
 * Pure coordinate arithmetic for the actual Sable-hull terrain anchor.
 * No Minecraft class dependency: isolated JUnit can exercise it without
 * pretending to launch a client or load Sable native libraries.
 */
final class SeamRapierAnchorMath {
    record Center(int chunkX,int chunkZ) {}
    private static final long MAX_SAMPLE_AGE_TICKS=20;
    private static final double WORLD_LIMIT=30_000_000.0;

    private SeamRapierAnchorMath() {}

    static boolean fresh(long sampled,long now) {
        return now>=sampled &&
            Long.compareUnsigned(now-sampled,MAX_SAMPLE_AGE_TICKS)<=0;
    }

    static Center fromBounds(double minX,double maxX,double minZ,double maxZ) {
        if(!Double.isFinite(minX)||!Double.isFinite(maxX)||
           !Double.isFinite(minZ)||!Double.isFinite(maxZ)||
           minX>=maxX||minZ>=maxZ)return null;
        double x=minX+(maxX-minX)*0.5;
        double z=minZ+(maxZ-minZ)*0.5;
        if(!Double.isFinite(x)||!Double.isFinite(z)||
           Math.abs(x)>=WORLD_LIMIT||Math.abs(z)>=WORLD_LIMIT)return null;
        return new Center(Math.floorDiv((int)Math.floor(x),16),
                          Math.floorDiv((int)Math.floor(z),16));
    }
}
