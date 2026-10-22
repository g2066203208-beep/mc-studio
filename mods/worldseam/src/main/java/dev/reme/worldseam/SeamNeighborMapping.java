package dev.reme.worldseam;

/**
 * A dimension-stack seam is an adjacency, never an 8:1 Nether portal.
 *
 * Native target blocks touching the external boundary can query the source
 * world beyond the plane without changing either world's own coordinates.
 */
public final class SeamNeighborMapping {
    private SeamNeighborMapping() {}

    /** The first block of the other world lies immediately outside the
     * native target's selected build-limit plane. Custom per-world seam
     * heights (not necessarily multiples of 16) are fully supported. */
    public static boolean outsideTarget(int nativeY,int targetBoundary,
                                         SeamGraph.EdgeSide entry) {
        return entry==SeamGraph.EdgeSide.TOP
            ? nativeY>=targetBoundary : nativeY<targetBoundary;
    }

    /** Map the neighboring native block to its real original-world block. */
    public static int sourceY(int targetNativeY,int sourceBoundary,
                              int targetBoundary) {
        return Math.addExact(targetNativeY,
            Math.subtractExact(sourceBoundary,targetBoundary));
    }

    /** Return null for an outside-world query that cannot belong to the
     * source's configured world height; NEVER force-load any chunks. */
    public static Integer loadedSourceY(int targetNativeY,int sourceBoundary,
                                        int targetBoundary,int sourceMin,int sourceMax,
                                        SeamGraph.EdgeSide entry) {
        if(!outsideTarget(targetNativeY,targetBoundary,entry))return null;
        int y;
        try { y=sourceY(targetNativeY,sourceBoundary,targetBoundary); }
        catch(ArithmeticException overflow) { return null; }
        return y>=sourceMin && y<sourceMax?y:null;
    }
}
