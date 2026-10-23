package dev.reme.worldseam;

import net.minecraft.world.phys.AABB;

/**
 * Bounded physics-terrain window anchor for a REAL authoritative Sable hull.
 * Never creates or transforms a body; the source world remains its owner.
 */
final class SeamRapierAnchor {
    record Center(int chunkX,int chunkZ) {}
    private static final long MAX_SAMPLE_AGE_TICKS=20;
    private static final double WORLD_LIMIT=30_000_000.0;

    private SeamRapierAnchor() {}

    static boolean fresh(long sampled,long now) {
        return now>=sampled && now-sampled<=MAX_SAMPLE_AGE_TICKS;
    }

    static Center fromHull(AABB hull) {
        if(hull==null || !Double.isFinite(hull.minX) ||
           !Double.isFinite(hull.maxX) || !Double.isFinite(hull.minZ) ||
           !Double.isFinite(hull.maxZ) ||
           hull.minX>=hull.maxX || hull.minZ>=hull.maxZ)return null;
        // Subtract before halving to avoid midpoint overflow.
        double x=hull.minX+(hull.maxX-hull.minX)*0.5;
        double z=hull.minZ+(hull.maxZ-hull.minZ)*0.5;
        if(!Double.isFinite(x)||!Double.isFinite(z)||
           Math.abs(x)>=WORLD_LIMIT||Math.abs(z)>=WORLD_LIMIT)return null;
        return new Center(Math.floorDiv((int)Math.floor(x),16),
                          Math.floorDiv((int)Math.floor(z),16));
    }
}
