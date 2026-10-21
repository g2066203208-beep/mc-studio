package dev.reme.worldseam;

/** Exact source/destination Y transform for a whole chunk section. */
public final class SeamSectionLayout {
    private SeamSectionLayout() {}

    public static int mappedBlockY(int nativeY,int sourceBoundary,int targetBoundary) {
        return Math.addExact(nativeY,Math.subtractExact(sourceBoundary,targetBoundary));
    }

    /** Floor division is mandatory for negative heights and custom dimensions. */
    public static int mappedSectionMinY(int nativeY,int sourceBoundary,int targetBoundary) {
        return mappedBlockY(Math.floorDiv(nativeY,16)*16,sourceBoundary,targetBoundary);
    }

    public static boolean nearPlane(double cameraY,int sourceBoundary,double maxDistance) {
        if(!Double.isFinite(cameraY) || !Double.isFinite(maxDistance) || maxDistance<0)
            return false;
        return Math.abs(cameraY-sourceBoundary)<=maxDistance;
    }
}
