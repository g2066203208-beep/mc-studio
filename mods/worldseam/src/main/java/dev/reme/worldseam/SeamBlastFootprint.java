package dev.reme.worldseam;

/**
 * Actual chunk footprint potentially touched by a native explosion.
 * Vanilla's entity reach extends to 2x blast power; requiring an arbitrary
 * 3x3 chunk square drops legit explosions at the seam while pointlessly
 * depending on unrelated unloaded diagonal chunks.
 *
 * This gate is deliberately conservative: no cross-world explosion if ANY
 * potentially touched remote chunk is not already resident.
 */
public final class SeamBlastFootprint {
    private SeamBlastFootprint() {}
    public record Range(int minX,int maxX,int minZ,int maxZ) {
        public Range {
            if(minX>maxX || minZ>maxZ)
                throw new IllegalArgumentException("Invalid chunk bounds");
        }
        public int count() {return (maxX-minX+1)*(maxZ-minZ+1);}
    }
    @FunctionalInterface public interface ChunkPresent {
        boolean isPresent(int chunkX,int chunkZ);
    }
    public static Range around(double x,double z,float radius) {
        if(!Double.isFinite(x)||!Double.isFinite(z)||
            !Float.isFinite(radius)||radius<=0||
            radius>SeamBlastGeometry.MAX_RADIUS)
            throw new IllegalArgumentException("Invalid bounded blast");
        double reach=2.0*radius;
        return new Range(
            (int)Math.floor((x-reach)/16.0),
            (int)Math.floor((x+reach)/16.0),
            (int)Math.floor((z-reach)/16.0),
            (int)Math.floor((z+reach)/16.0)
        );
    }
    public static boolean fullyResident(Range r,ChunkPresent chunks) {
        for(int x=r.minX();x<=r.maxX();x++)
            for(int z=r.minZ();z<=r.maxZ();z++)
                if(!chunks.isPresent(x,z))return false;
        return true;
    }
}
