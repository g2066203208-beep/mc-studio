package dev.reme.worldseam;

/**
 * Coordinate-only gate for a physics sub-level whose origin crosses a vertical
 * WorldSeam plane. The owner rigid body MUST be transferred by Sable/AeroPortals;
 * applying vanilla Entity.changeDimension() here corrupts plot ownership.
 */
public final class SeamShipCrossing {
    private SeamShipCrossing() {}

    public record Crossing(double destinationY, double velocityY) {}

    public record Bounds(double minX,double minY,double minZ,
                         double maxX,double maxY,double maxZ) {
        public Bounds {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) ||
                !Double.isFinite(minZ) || !Double.isFinite(maxX) ||
                !Double.isFinite(maxY) || !Double.isFinite(maxZ) ||
                minX>=maxX || minY>=maxY || minZ>=maxZ)
                throw new IllegalArgumentException("invalid ship bounds");
        }
        public Bounds shiftedY(double delta) {
            return new Bounds(minX,minY+delta,minZ,maxX,maxY+delta,maxZ);
        }
    }

    /** Cross exactly once in the velocity direction, preserving world X/Z and
     * native velocity. Do not multiply coordinates by the Nether's 8:1 scale. */
    public static Crossing crossing(SeamGraph.EdgeSide exit,double previousY,
                                     double currentY,double velocityY,
                                     double sourceBoundary,double targetBoundary) {
        if (!Double.isFinite(previousY) || !Double.isFinite(currentY) ||
            !Double.isFinite(velocityY) || !Double.isFinite(sourceBoundary) ||
            !Double.isFinite(targetBoundary)) return null;
        // Never interpret teleport corrections or numerical noise as ship
        // physics. A real cross requires directional movement through plane.
        boolean crossed = exit==SeamGraph.EdgeSide.BOTTOM
            ? previousY>=sourceBoundary && currentY<sourceBoundary && velocityY<0
            : previousY<=sourceBoundary && currentY>sourceBoundary && velocityY>0;
        if (!crossed || Math.abs(currentY-previousY)>32) return null;
        return new Crossing(currentY+(targetBoundary-sourceBoundary),velocityY);
    }

    /** Reject arrivals without pre-existing target chunks. No getChunk() call
     * here: generating a ship-sized region synchronously can freeze the TPS. */
    public static boolean loadedFootprint(Bounds destination,int worldMinY,
                                           int worldMaxY, ChunkReady loaded) {
        if (destination.minY<worldMinY || destination.maxY>worldMaxY) return false;
        int left=(int)Math.floor(destination.minX/16.0);
        int right=(int)Math.ceil(destination.maxX/16.0)-1;
        int front=(int)Math.floor(destination.minZ/16.0);
        int back=(int)Math.ceil(destination.maxZ/16.0)-1;
        // Bound the preflight and reject enormous craft until a ticketed
        // asynchronous scheduler can reserve their complete footprint.
        if ((long)(right-left+1)*(back-front+1)>256) return false;
        for(int x=left;x<=right;x++)for(int z=front;z<=back;z++)
            if(!loaded.ready(x,z))return false;
        return true;
    }

    @FunctionalInterface
    public interface ChunkReady { boolean ready(int chunkX,int chunkZ); }
}
